package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.client.MarketQuoteClient;
import com.example.app.dto.InvestmentFundRedeemFeeOptionResponse;
import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentTransactionEntity;
import com.example.app.mapper.InvestmentTransactionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.example.app.service.InvestmentCalcUtils.*;

/** 基金赎回费率与 FIFO 持仓批次计算；行情规则在写事务外预取，计算只访问本地流水。 */
@Service
public class InvestmentFundFeeService {
    private static final String NORMAL_STATUS = "normal";
    private static final String SETTLEMENT_STATUS_CONFIRMED = "confirmed";
    private static final String SETTLEMENT_STATUS_PENDING = "pending";
    private final MarketQuoteClient marketQuoteClient;
    private final InvestmentTransactionMapper transactionMapper;

    public InvestmentFundFeeService(MarketQuoteClient marketQuoteClient, InvestmentTransactionMapper transactionMapper) {
        this.marketQuoteClient = marketQuoteClient;
        this.transactionMapper = transactionMapper;
    }

    public record FundRedeemFeeRule(
        String label,
        int minHoldingDaysInclusive,
        Integer maxHoldingDaysExclusive,
        BigDecimal feeRate
    ) {
    }

    private record FundHoldingLot(
        LocalDate acquiredDate,
        BigDecimal quantity
    ) {
    }

    public BigDecimal calculateFundSellFeeAmount(
        InvestmentPositionEntity position,
        List<FundRedeemFeeRule> rules,
        BigDecimal quantity,
        LocalDate appliedDate,
        BigDecimal estimatedPrice
    ) {
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("暂未获取到基金赎回费率，请稍后再试");
        }

        List<FundHoldingLot> availableLots = buildAvailableFundHoldingLots(position);
        if (availableLots.isEmpty()) {
            throw new IllegalArgumentException("当前基金持仓不足，暂无法计算赎回手续费");
        }

        BigDecimal remainingQuantity = scaleFundQuantity(quantity);
        BigDecimal totalFeeAmount = BigDecimal.ZERO;
        BigDecimal normalizedPrice = defaultZero(estimatedPrice).setScale(6, RoundingMode.HALF_UP);
        for (FundHoldingLot lot : availableLots) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal lotQuantity = lot.quantity().min(remainingQuantity).setScale(6, RoundingMode.HALF_UP);
            if (lotQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal feeRate = resolveFundRedeemFeeRate(rules, resolveFundHoldingDays(lot.acquiredDate(), appliedDate));
            BigDecimal lotFeeAmount = lotQuantity.multiply(normalizedPrice).multiply(feeRate);
            totalFeeAmount = totalFeeAmount.add(lotFeeAmount);
            remainingQuantity = remainingQuantity.subtract(lotQuantity).setScale(6, RoundingMode.HALF_UP);
        }

        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("基金持仓份额数据异常，暂无法计算赎回手续费");
        }
        return totalFeeAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public List<InvestmentFundRedeemFeeOptionResponse> buildFundRedeemFeeOptions(String code) {
        List<FundRedeemFeeRule> rules = fetchFundRedeemFeeRules(code);
        if (rules.isEmpty()) throw new IllegalArgumentException("暂未获取到基金赎回费率，请稍后再试");
        return rules.stream()
            .map(rule -> {
                InvestmentFundRedeemFeeOptionResponse response = new InvestmentFundRedeemFeeOptionResponse();
                response.setLabel(rule.label());
                response.setFeeRate(defaultZero(rule.feeRate()).setScale(4, RoundingMode.HALF_UP));
                return response;
            })
            .toList();
    }

    public List<FundRedeemFeeRule> fetchFundRedeemFeeRules(String code) {
        try {
            JsonNode payload = marketQuoteClient.fundFees(code);
            if (payload == null || !payload.path("matched").asBoolean(false)) {
                return Collections.emptyList();
            }
            JsonNode rules = payload.path("redeemFeeRules");
            if (!rules.isArray()) {
                return Collections.emptyList();
            }
            List<FundRedeemFeeRule> parsed = new ArrayList<>();
            for (JsonNode rule : rules) {
                FundRedeemFeeRule item = parseFundRedeemFeeRule(
                    rule.path("label").asText(""),
                    rule.path("feeRateText").asText("")
                );
                if (item != null) {
                    parsed.add(item);
                }
            }
            return parsed.stream()
                .sorted(Comparator.comparingInt(FundRedeemFeeRule::minHoldingDaysInclusive))
                .toList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }


    FundRedeemFeeRule parseFundRedeemFeeRule(String holdingPeriodText, String feeRateText) {
        if (!StringUtils.hasText(holdingPeriodText) || !StringUtils.hasText(feeRateText)) {
            return null;
        }

        BigDecimal feeRateValue;
        if (feeRateText.contains("%")) {
            BigDecimal percent = safeDecimal(feeRateText.replace("%", ""));
            if (percent == null) {
                return null;
            }
            feeRateValue = percent.movePointLeft(2);
        } else {
            feeRateValue = safeDecimal(feeRateText);
            if (feeRateValue == null) {
                return null;
            }
        }

        Matcher rangeMatcher = Pattern.compile("大于等于(\\d+)天[,，]小于(\\d+)天").matcher(holdingPeriodText);
        if (rangeMatcher.find()) {
            return new FundRedeemFeeRule(
                holdingPeriodText,
                Integer.parseInt(rangeMatcher.group(1)),
                Integer.parseInt(rangeMatcher.group(2)),
                feeRateValue
            );
        }

        Matcher lessThanMatcher = Pattern.compile("小于(\\d+)天").matcher(holdingPeriodText);
        if (lessThanMatcher.find()) {
            return new FundRedeemFeeRule(holdingPeriodText, 0, Integer.parseInt(lessThanMatcher.group(1)), feeRateValue);
        }

        Matcher greaterEqualMatcher = Pattern.compile("大于等于(\\d+)天").matcher(holdingPeriodText);
        if (greaterEqualMatcher.find()) {
            return new FundRedeemFeeRule(holdingPeriodText, Integer.parseInt(greaterEqualMatcher.group(1)), null, feeRateValue);
        }
        return null;
    }


    private List<FundHoldingLot> buildAvailableFundHoldingLots(InvestmentPositionEntity position) {
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getPositionId, position.getId())
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByAsc(InvestmentTransactionEntity::getTradeAt)
            .orderByAsc(InvestmentTransactionEntity::getId));

        BigDecimal currentHoldingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal confirmedBuyQuantity = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal confirmedSellQuantity = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        List<FundHoldingLot> lots = new ArrayList<>();
        List<BigDecimal> consumedSellQuantities = new ArrayList<>();

        for (InvestmentTransactionEntity transaction : transactions) {
            if (("buy".equals(transaction.getTradeType()) || "dividend_reinvest".equals(transaction.getTradeType()))
                && SETTLEMENT_STATUS_CONFIRMED.equals(transaction.getSettlementStatus())) {
                BigDecimal lotQuantity = scaleFundQuantity(defaultZero(transaction.getQuantity()));
                if (lotQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    lots.add(new FundHoldingLot(resolveFundLotAcquireDate(position, transaction), lotQuantity));
                    confirmedBuyQuantity = confirmedBuyQuantity.add(lotQuantity).setScale(6, RoundingMode.HALF_UP);
                }
                continue;
            }
            if ("sell".equals(transaction.getTradeType())
                && (SETTLEMENT_STATUS_CONFIRMED.equals(transaction.getSettlementStatus())
                || SETTLEMENT_STATUS_PENDING.equals(transaction.getSettlementStatus()))) {
                BigDecimal soldQuantity = scaleFundQuantity(defaultZero(transaction.getQuantity()));
                if (soldQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    consumedSellQuantities.add(soldQuantity);
                    if (SETTLEMENT_STATUS_CONFIRMED.equals(transaction.getSettlementStatus())) {
                        confirmedSellQuantity = confirmedSellQuantity.add(soldQuantity).setScale(6, RoundingMode.HALF_UP);
                    }
                }
            }
        }

        BigDecimal inferredInitialQuantity = currentHoldingQuantity.add(confirmedSellQuantity)
            .subtract(confirmedBuyQuantity)
            .setScale(6, RoundingMode.HALF_UP);
        if (inferredInitialQuantity.compareTo(BigDecimal.ZERO) > 0) {
            lots.add(0, new FundHoldingLot(resolveInitialFundLotDate(position), inferredInitialQuantity));
        }

        for (BigDecimal soldQuantity : consumedSellQuantities) {
            consumeFundHoldingLots(lots, soldQuantity);
        }
        return lots.stream()
            .filter(item -> item.quantity().compareTo(BigDecimal.ZERO) > 0)
            .toList();
    }

    private LocalDate resolveFundLotAcquireDate(InvestmentPositionEntity position, InvestmentTransactionEntity transaction) {
        if (transaction.getSettlementConfirmedAt() != null) {
            return transaction.getSettlementConfirmedAt().toLocalDate();
        }
        if (transaction.getSettlementAppliedDate() != null) {
            return transaction.getSettlementAppliedDate();
        }
        if (transaction.getTradeAt() != null) {
            return transaction.getTradeAt().toLocalDate();
        }
        return resolveInitialFundLotDate(position);
    }

    private LocalDate resolveInitialFundLotDate(InvestmentPositionEntity position) {
        if (position.getSubscriptionConfirmedAt() != null) {
            return position.getSubscriptionConfirmedAt().toLocalDate();
        }
        if (position.getSubscriptionAppliedDate() != null) {
            return position.getSubscriptionAppliedDate();
        }
        if (position.getCreatedAt() != null) {
            return position.getCreatedAt().toLocalDate();
        }
        return LocalDate.now();
    }

    private void consumeFundHoldingLots(List<FundHoldingLot> lots, BigDecimal quantity) {
        BigDecimal remainingQuantity = scaleFundQuantity(quantity);
        for (int index = 0; index < lots.size() && remainingQuantity.compareTo(BigDecimal.ZERO) > 0; index++) {
            FundHoldingLot currentLot = lots.get(index);
            BigDecimal currentQuantity = currentLot.quantity().setScale(6, RoundingMode.HALF_UP);
            if (currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal consumedQuantity = currentQuantity.min(remainingQuantity).setScale(6, RoundingMode.HALF_UP);
            BigDecimal nextQuantity = currentQuantity.subtract(consumedQuantity).setScale(6, RoundingMode.HALF_UP);
            lots.set(index, new FundHoldingLot(currentLot.acquiredDate(), nextQuantity));
            remainingQuantity = remainingQuantity.subtract(consumedQuantity).setScale(6, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal resolveFundRedeemFeeRate(List<FundRedeemFeeRule> rules, int holdingDays) {
        for (FundRedeemFeeRule rule : rules) {
            if (holdingDays < rule.minHoldingDaysInclusive()) {
                continue;
            }
            if (rule.maxHoldingDaysExclusive() == null || holdingDays < rule.maxHoldingDaysExclusive()) {
                return defaultZero(rule.feeRate());
            }
        }
        return BigDecimal.ZERO;
    }

    private int resolveFundHoldingDays(LocalDate acquiredDate, LocalDate appliedDate) {
        if (acquiredDate == null || appliedDate == null) {
            return 0;
        }
        return Math.max((int) ChronoUnit.DAYS.between(acquiredDate, appliedDate), 0);
    }
}
