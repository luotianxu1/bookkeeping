package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.GoldAccountHoldingResponse;
import com.example.finance.dto.GoldAccountSummaryResponse;
import com.example.finance.dto.GoldLiquidationRecordResponse;
import com.example.finance.dto.GoldLiquidationResponse;
import com.example.finance.dto.GoldPriceResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.InvestmentPositionEntity;
import com.example.finance.entity.InvestmentProductEntity;
import com.example.finance.entity.InvestmentTransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.InvestmentPositionMapper;
import com.example.finance.mapper.InvestmentProductMapper;
import com.example.finance.mapper.InvestmentTransactionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GoldAccountService {

    private static final String GOLD_ACCOUNT_TYPE_CODE = "gold";
    private static final String ACTIVE_STATUS = "active";
    private static final String NORMAL_STATUS = "normal";

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final InvestmentPositionMapper investmentPositionMapper;
    private final InvestmentProductMapper investmentProductMapper;
    private final InvestmentTransactionMapper investmentTransactionMapper;
    private final GoldPriceService goldPriceService;

    public GoldAccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        InvestmentPositionMapper investmentPositionMapper,
        InvestmentProductMapper investmentProductMapper,
        InvestmentTransactionMapper investmentTransactionMapper,
        GoldPriceService goldPriceService
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.investmentPositionMapper = investmentPositionMapper;
        this.investmentProductMapper = investmentProductMapper;
        this.investmentTransactionMapper = investmentTransactionMapper;
        this.goldPriceService = goldPriceService;
    }

    public GoldAccountSummaryResponse summary(Long userId) {
        List<InvestmentPositionEntity> positions = loadGoldPositions(userId);
        GoldAccountSummaryResponse response = new GoldAccountSummaryResponse();
        BigDecimal realtimePrice = getRealtimeGoldPrice(positions);
        BigDecimal totalWeight = sum(positions, InvestmentPositionEntity::getHoldingQuantity, 6);
        BigDecimal purchaseTotal = sum(positions, InvestmentPositionEntity::getCostAmount, 2);
        BigDecimal estimatedValue = totalWeight.multiply(realtimePrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedProfit = estimatedValue.subtract(purchaseTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cumulativeProfit = liquidations(userId).getCumulativeProfit();

        response.setTotalWeight(totalWeight);
        response.setPurchaseTotal(purchaseTotal);
        response.setEstimatedValue(estimatedValue);
        response.setEstimatedProfit(estimatedProfit);
        response.setCumulativeProfit(cumulativeProfit);
        response.setAveragePrice(totalWeight.compareTo(BigDecimal.ZERO) > 0
            ? purchaseTotal.divide(totalWeight, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setProfitRate(ratePercent(estimatedProfit, purchaseTotal));
        return response;
    }

    public List<GoldAccountHoldingResponse> holdings(Long userId) {
        List<AccountEntity> goldAccounts = loadGoldAccounts(userId);
        if (goldAccounts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, AccountEntity> accountMap = goldAccounts.stream()
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Map<Long, InvestmentProductEntity> productMap = loadGoldProductMap();
        List<InvestmentPositionEntity> positions = investmentPositionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(InvestmentPositionEntity::getUserId, userId)
                .in(InvestmentPositionEntity::getAccountId, accountMap.keySet())
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS)
                .orderByDesc(InvestmentPositionEntity::getMarketValue)
                .orderByDesc(InvestmentPositionEntity::getId));
        BigDecimal realtimePrice = getRealtimeGoldPrice(positions);

        return positions.stream()
            .filter(position -> isGoldProduct(productMap.get(position.getProductId())))
            .map(position -> toHoldingResponse(position, accountMap.get(position.getAccountId()), productMap.get(position.getProductId()), realtimePrice))
            .sorted((left, right) -> right.getMarketValue().compareTo(left.getMarketValue()))
            .toList();
    }

    public GoldLiquidationResponse liquidations(Long userId) {
        List<AccountEntity> goldAccounts = loadGoldAccounts(userId);
        if (goldAccounts.isEmpty()) {
            return emptyLiquidationResponse();
        }

        Map<Long, AccountEntity> accountMap = goldAccounts.stream()
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        List<InvestmentTransactionEntity> transactions = investmentTransactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getUserId, userId)
            .in(InvestmentTransactionEntity::getAccountId, accountMap.keySet())
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByAsc(InvestmentTransactionEntity::getPositionId)
            .orderByAsc(InvestmentTransactionEntity::getTradeAt)
            .orderByAsc(InvestmentTransactionEntity::getId));
        if (transactions.isEmpty()) {
            return emptyLiquidationResponse();
        }

        Set<Long> productIds = transactions.stream().map(InvestmentTransactionEntity::getProductId).collect(Collectors.toSet());
        Map<Long, InvestmentProductEntity> productMap = investmentProductMapper.selectByIds(productIds).stream()
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));

        List<GoldLiquidationRecordResponse> records = buildLiquidationRecords(transactions, accountMap, productMap);
        GoldLiquidationResponse response = new GoldLiquidationResponse();
        response.setCumulativeWeight(records.stream()
            .map(GoldLiquidationRecordResponse::getWeight)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(6, RoundingMode.HALF_UP));
        response.setCumulativeProfit(records.stream()
            .map(GoldLiquidationRecordResponse::getProfit)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP));
        response.setRecords(records.stream()
            .sorted(Comparator.comparing(GoldLiquidationRecordResponse::getTradeAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GoldLiquidationRecordResponse::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList());
        return response;
    }

    private List<AccountEntity> loadGoldAccounts(Long userId) {
        AccountTypeEntity goldType = accountTypeMapper.selectOne(new LambdaQueryWrapper<AccountTypeEntity>()
            .eq(AccountTypeEntity::getCode, GOLD_ACCOUNT_TYPE_CODE)
            .last("LIMIT 1"));
        if (goldType == null) {
            return Collections.emptyList();
        }
        return accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(userId != null, AccountEntity::getUserId, userId)
            .eq(AccountEntity::getAccountTypeId, goldType.getId())
            .eq(AccountEntity::getStatus, ACTIVE_STATUS)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId));
    }

    private List<InvestmentPositionEntity> loadGoldPositions(Long userId) {
        List<AccountEntity> goldAccounts = loadGoldAccounts(userId);
        if (goldAccounts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, InvestmentProductEntity> productMap = loadGoldProductMap();
        return investmentPositionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(InvestmentPositionEntity::getUserId, userId)
                .in(InvestmentPositionEntity::getAccountId, goldAccounts.stream().map(AccountEntity::getId).toList())
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .filter(position -> isGoldProduct(productMap.get(position.getProductId())))
            .toList();
    }

    private Map<Long, InvestmentProductEntity> loadGoldProductMap() {
        return investmentProductMapper.selectList(new LambdaQueryWrapper<InvestmentProductEntity>()
                .eq(InvestmentProductEntity::getProductType, GOLD_ACCOUNT_TYPE_CODE))
            .stream()
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
    }

    private boolean isGoldProduct(InvestmentProductEntity product) {
        return product != null && GOLD_ACCOUNT_TYPE_CODE.equals(product.getProductType());
    }

    private GoldAccountHoldingResponse toHoldingResponse(
        InvestmentPositionEntity position,
        AccountEntity account,
        InvestmentProductEntity product,
        BigDecimal realtimePrice
    ) {
        BigDecimal weight = scale(position.getHoldingQuantity(), 6);
        BigDecimal purchaseAmount = scale(position.getCostAmount(), 2);
        BigDecimal marketValue = weight.multiply(realtimePrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(purchaseAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = weight.compareTo(BigDecimal.ZERO) > 0
            ? purchaseAmount.divide(weight, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        GoldAccountHoldingResponse response = new GoldAccountHoldingResponse();
        response.setId(position.getId());
        response.setPositionId(position.getId());
        response.setAccountId(position.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setProductId(position.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setCurrentPrice(realtimePrice);
        response.setPurchaseAmount(purchaseAmount);
        response.setWeight(weight);
        response.setHoldingProfit(holdingProfit);
        response.setMarketValue(marketValue);
        response.setAvgCostPrice(avgCostPrice);
        response.setCreatedAt(position.getCreatedAt());
        return response;
    }

    private BigDecimal getRealtimeGoldPrice(List<InvestmentPositionEntity> positions) {
        BigDecimal cachedSpotPrice = goldPriceService.getCachedSpotPrice();
        if (cachedSpotPrice != null && cachedSpotPrice.compareTo(BigDecimal.ZERO) > 0) {
            return cachedSpotPrice.setScale(2, RoundingMode.HALF_UP);
        }

        try {
            GoldPriceResponse goldPrice = goldPriceService.getGoldPrice("1d");
            BigDecimal price = goldPrice == null || goldPrice.getSpotGold() == null
                ? null
                : goldPrice.getSpotGold().getPrice();
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                return price.setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {
            // Return zero when neither the cache nor the remote quote is available.
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private List<GoldLiquidationRecordResponse> buildLiquidationRecords(
        List<InvestmentTransactionEntity> transactions,
        Map<Long, AccountEntity> accountMap,
        Map<Long, InvestmentProductEntity> productMap
    ) {
        Map<Long, PositionCostState> positionStates = new HashMap<>();
        List<GoldLiquidationRecordResponse> records = new ArrayList<>();

        for (InvestmentTransactionEntity transaction : transactions) {
            InvestmentProductEntity product = productMap.get(transaction.getProductId());
            if (!isGoldProduct(product)) {
                continue;
            }

            PositionCostState state = positionStates.computeIfAbsent(
                transaction.getPositionId(),
                ignored -> new PositionCostState()
            );
            BigDecimal quantity = scale(transaction.getQuantity(), 6);
            BigDecimal fee = scale(defaultZero(transaction.getFeeAmount()).add(defaultZero(transaction.getTaxAmount())), 2);

            if ("buy".equals(transaction.getTradeType())) {
                BigDecimal buyCost = defaultZero(transaction.getAmount())
                    .add(defaultZero(transaction.getFeeAmount()))
                    .add(defaultZero(transaction.getTaxAmount()))
                    .setScale(2, RoundingMode.HALF_UP);
                state.quantity = state.quantity.add(quantity).setScale(6, RoundingMode.HALF_UP);
                state.costAmount = state.costAmount.add(buyCost).setScale(2, RoundingMode.HALF_UP);
                continue;
            }

            if (!"sell".equals(transaction.getTradeType())) {
                continue;
            }

            BigDecimal buyPrice = state.quantity.compareTo(BigDecimal.ZERO) > 0
                ? state.costAmount.divide(state.quantity, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
            BigDecimal soldCost = buyPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            if (soldCost.compareTo(state.costAmount) > 0) {
                soldCost = state.costAmount;
            }
            BigDecimal profit = defaultZero(transaction.getAmount())
                .subtract(fee)
                .subtract(soldCost)
                .setScale(2, RoundingMode.HALF_UP);

            records.add(toLiquidationRecord(
                transaction,
                accountMap.get(transaction.getAccountId()),
                product,
                buyPrice,
                profit,
                fee
            ));

            state.quantity = state.quantity.subtract(quantity).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
            state.costAmount = state.costAmount.subtract(soldCost).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        return records;
    }

    private GoldLiquidationRecordResponse toLiquidationRecord(
        InvestmentTransactionEntity transaction,
        AccountEntity account,
        InvestmentProductEntity product,
        BigDecimal buyPrice,
        BigDecimal profit,
        BigDecimal fee
    ) {
        GoldLiquidationRecordResponse response = new GoldLiquidationRecordResponse();
        BigDecimal weight = scale(transaction.getQuantity(), 6);
        BigDecimal sellPrice = scale(transaction.getPrice(), 2);

        response.setId(transaction.getId());
        response.setAccountId(transaction.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setPositionId(transaction.getPositionId());
        response.setProductId(transaction.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setTradeAt(transaction.getTradeAt());
        response.setProfit(scale(profit, 2));
        response.setWeight(weight);
        response.setBuyPrice(scale(buyPrice, 2));
        response.setSellPrice(sellPrice);
        response.setFee(fee);
        return response;
    }

    private GoldLiquidationResponse emptyLiquidationResponse() {
        GoldLiquidationResponse response = new GoldLiquidationResponse();
        response.setCumulativeWeight(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        response.setCumulativeProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setRecords(Collections.emptyList());
        return response;
    }

    private BigDecimal ratePercent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return numerator.multiply(BigDecimal.valueOf(100))
            .divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return defaultZero(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal sum(List<InvestmentPositionEntity> positions, Function<InvestmentPositionEntity, BigDecimal> getter, int scale) {
        return positions.stream()
            .map(getter)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(scale, RoundingMode.HALF_UP);
    }

    private static final class PositionCostState {
        private BigDecimal quantity = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        private BigDecimal costAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
