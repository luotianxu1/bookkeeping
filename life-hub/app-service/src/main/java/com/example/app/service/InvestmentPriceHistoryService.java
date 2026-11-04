package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentPriceQuoteEntity;
import com.example.app.entity.InvestmentTransactionEntity;
import com.example.app.mapper.InvestmentPriceQuoteMapper;
import com.example.app.mapper.InvestmentTransactionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.example.app.service.InvestmentCalcUtils.*;

/**
 * 共享价格历史助手：提供持仓成交明细加载、历史价格加载与任意历史日期的持仓数量/价格回溯。
 * 被资产趋势（InvestmentService）与基金收益页（InvestmentFundProfitService）共用。
 */
@Service
public class InvestmentPriceHistoryService {

    private static final String NORMAL_STATUS = "normal";
    private static final String VOIDED_STATUS = "voided";
    private static final String SUBSCRIPTION_STATUS_PENDING = "pending";
    private static final String SETTLEMENT_STATUS_PENDING = "pending";

    private final InvestmentTransactionMapper transactionMapper;
    private final InvestmentPriceQuoteMapper priceQuoteMapper;

    public InvestmentPriceHistoryService(
        InvestmentTransactionMapper transactionMapper,
        InvestmentPriceQuoteMapper priceQuoteMapper
    ) {
        this.transactionMapper = transactionMapper;
        this.priceQuoteMapper = priceQuoteMapper;
    }

    public Map<Long, List<InvestmentTransactionEntity>> loadConfirmedTransactionsByPosition(
        Long userId,
        Long accountId,
        List<InvestmentPositionEntity> positions
    ) {
        if (positions.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> positionIds = positions.stream()
            .map(InvestmentPositionEntity::getId)
            .collect(Collectors.toSet());
        if (positionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
                .eq(userId != null, InvestmentTransactionEntity::getUserId, userId)
                .eq(accountId != null, InvestmentTransactionEntity::getAccountId, accountId)
                .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
                .in(InvestmentTransactionEntity::getPositionId, positionIds)
                .orderByAsc(InvestmentTransactionEntity::getTradeAt)
                .orderByAsc(InvestmentTransactionEntity::getId))
            .stream()
            .collect(Collectors.groupingBy(InvestmentTransactionEntity::getPositionId));
    }

    public Map<Long, NavigableMap<LocalDate, BigDecimal>> loadPriceHistoryByProduct(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, NavigableMap<LocalDate, BigDecimal>> history = new HashMap<>();
        for (InvestmentPriceQuoteEntity quote : priceQuoteMapper.selectList(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .in(InvestmentPriceQuoteEntity::getProductId, productIds)
            .orderByAsc(InvestmentPriceQuoteEntity::getProductId)
            .orderByAsc(InvestmentPriceQuoteEntity::getQuoteDate)
            .orderByAsc(InvestmentPriceQuoteEntity::getQuoteTime)
            .orderByAsc(InvestmentPriceQuoteEntity::getId))) {
            LocalDate quoteDate = quote.getQuoteDate();
            BigDecimal price = resolveQuotePrice(quote);
            if (quoteDate == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            history.computeIfAbsent(quote.getProductId(), key -> new TreeMap<>())
                .put(quoteDate, price.setScale(6, RoundingMode.HALF_UP));
        }
        return history;
    }

    public BigDecimal resolvePositionQuantityAtDate(
        InvestmentPositionEntity position,
        LocalDate targetDate,
        List<InvestmentTransactionEntity> transactions
    ) {
        if (position.getCreatedAt() != null && position.getCreatedAt().toLocalDate().isAfter(targetDate)) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        if (isPendingFundSubscription(position)
            && (position.getSubscriptionConfirmedAt() == null || position.getSubscriptionConfirmedAt().toLocalDate().isAfter(targetDate))) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }

        BigDecimal quantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (transactions == null || transactions.isEmpty()) {
            return quantity.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
        }

        for (InvestmentTransactionEntity transaction : transactions) {
            LocalDate effectiveDate = resolveTransactionEffectiveDate(transaction);
            if (effectiveDate != null && effectiveDate.isAfter(targetDate)) {
                quantity = quantity.subtract(signedTransactionQuantity(transaction)).setScale(6, RoundingMode.HALF_UP);
            }
        }
        return quantity.compareTo(BigDecimal.ZERO) > 0 ? quantity : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal resolvePositionPriceAtDate(
        InvestmentPositionEntity position,
        LocalDate targetDate,
        NavigableMap<LocalDate, BigDecimal> priceHistory
    ) {
        if (position != null
            && targetDate != null
            && position.getLastSyncedAt() != null
            && targetDate.equals(position.getLastSyncedAt().toLocalDate())) {
            LocalDate latestHistoryDate = priceHistory == null || priceHistory.isEmpty() ? null : priceHistory.lastKey();
            if (latestHistoryDate == null || latestHistoryDate.isBefore(targetDate)) {
                BigDecimal currentPrice = defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
                if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                    return currentPrice;
                }
            }
        }

        if (priceHistory != null && !priceHistory.isEmpty()) {
            Map.Entry<LocalDate, BigDecimal> floorEntry = priceHistory.floorEntry(targetDate);
            if (floorEntry != null && floorEntry.getValue() != null && floorEntry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                return floorEntry.getValue().setScale(6, RoundingMode.HALF_UP);
            }
            Map.Entry<LocalDate, BigDecimal> firstEntry = priceHistory.firstEntry();
            if (firstEntry != null && firstEntry.getValue() != null && firstEntry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                return firstEntry.getValue().setScale(6, RoundingMode.HALF_UP);
            }
        }

        BigDecimal currentPrice = defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
        if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            return currentPrice;
        }
        return defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal resolveQuotePrice(InvestmentPriceQuoteEntity quote) {
        if (quote == null) {
            return null;
        }
        if (quote.getClosePrice() != null && quote.getClosePrice().compareTo(BigDecimal.ZERO) > 0) {
            return quote.getClosePrice();
        }
        if (quote.getLatestPrice() != null && quote.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            return quote.getLatestPrice();
        }
        if (quote.getOpenPrice() != null && quote.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
            return quote.getOpenPrice();
        }
        return null;
    }

    public LocalDate resolveTransactionEffectiveDate(InvestmentTransactionEntity transaction) {
        if (transaction == null || VOIDED_STATUS.equals(transaction.getStatus())) {
            return null;
        }
        if (SETTLEMENT_STATUS_PENDING.equals(transaction.getSettlementStatus()) && transaction.getSettlementConfirmedAt() == null) {
            return null;
        }
        if (transaction.getSettlementConfirmedAt() != null) {
            return transaction.getSettlementConfirmedAt().toLocalDate();
        }
        return transaction.getTradeAt() == null ? null : transaction.getTradeAt().toLocalDate();
    }

    public BigDecimal signedTransactionQuantity(InvestmentTransactionEntity transaction) {
        BigDecimal quantity = defaultZero(transaction.getQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (isPositiveTradeType(transaction.getTradeType())) {
            return quantity;
        }
        if (isNegativeTradeType(transaction.getTradeType())) {
            return quantity.negate().setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    }

    public boolean isPositiveTradeType(String tradeType) {
        return "buy".equals(tradeType)
            || "add".equals(tradeType)
            || "dividend_reinvest".equals(tradeType)
            || "split_adjust".equals(tradeType);
    }

    public boolean isNegativeTradeType(String tradeType) {
        return "sell".equals(tradeType) || "reduce".equals(tradeType);
    }

    private boolean isPendingFundSubscription(InvestmentPositionEntity position) {
        return position != null && SUBSCRIPTION_STATUS_PENDING.equals(position.getSubscriptionStatus());
    }
}
