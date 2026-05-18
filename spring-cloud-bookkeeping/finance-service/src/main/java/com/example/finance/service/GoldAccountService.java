package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.GoldAccountHoldingResponse;
import com.example.finance.dto.GoldAccountSummaryResponse;
import com.example.finance.dto.GoldLiquidationRecordResponse;
import com.example.finance.dto.GoldLiquidationResponse;
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
import java.util.Collections;
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

    public GoldAccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        InvestmentPositionMapper investmentPositionMapper,
        InvestmentProductMapper investmentProductMapper,
        InvestmentTransactionMapper investmentTransactionMapper
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.investmentPositionMapper = investmentPositionMapper;
        this.investmentProductMapper = investmentProductMapper;
        this.investmentTransactionMapper = investmentTransactionMapper;
    }

    public GoldAccountSummaryResponse summary(Long userId) {
        List<InvestmentPositionEntity> positions = loadGoldPositions(userId);
        GoldAccountSummaryResponse response = new GoldAccountSummaryResponse();
        BigDecimal totalWeight = sum(positions, InvestmentPositionEntity::getHoldingQuantity, 6);
        BigDecimal purchaseTotal = sum(positions, InvestmentPositionEntity::getCostAmount, 2);
        BigDecimal estimatedValue = sum(positions, InvestmentPositionEntity::getMarketValue, 2);
        BigDecimal estimatedProfit = estimatedValue.subtract(purchaseTotal).setScale(2, RoundingMode.HALF_UP);

        response.setTotalWeight(totalWeight);
        response.setPurchaseTotal(purchaseTotal);
        response.setEstimatedValue(estimatedValue);
        response.setEstimatedProfit(estimatedProfit);
        response.setCumulativeProfit(sum(positions, InvestmentPositionEntity::getCumulativeProfit, 2));
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

        return investmentPositionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(InvestmentPositionEntity::getUserId, userId)
                .in(InvestmentPositionEntity::getAccountId, accountMap.keySet())
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS)
                .orderByDesc(InvestmentPositionEntity::getMarketValue)
                .orderByDesc(InvestmentPositionEntity::getId))
            .stream()
            .filter(position -> isGoldProduct(productMap.get(position.getProductId())))
            .map(position -> toHoldingResponse(position, accountMap.get(position.getAccountId()), productMap.get(position.getProductId())))
            .toList();
    }

    public GoldLiquidationResponse liquidations(Long userId) {
        List<AccountEntity> goldAccounts = loadGoldAccounts(userId);
        GoldLiquidationResponse response = new GoldLiquidationResponse();
        if (goldAccounts.isEmpty()) {
            response.setCumulativeWeight(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
            response.setCumulativeProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            response.setRecords(Collections.emptyList());
            return response;
        }

        Map<Long, AccountEntity> accountMap = goldAccounts.stream()
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        List<InvestmentTransactionEntity> sellTransactions = investmentTransactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getUserId, userId)
            .in(InvestmentTransactionEntity::getAccountId, accountMap.keySet())
            .eq(InvestmentTransactionEntity::getTradeType, "sell")
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByDesc(InvestmentTransactionEntity::getTradeAt)
            .orderByDesc(InvestmentTransactionEntity::getId));

        if (sellTransactions.isEmpty()) {
            response.setCumulativeWeight(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
            response.setCumulativeProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            response.setRecords(Collections.emptyList());
            return response;
        }

        Set<Long> productIds = sellTransactions.stream().map(InvestmentTransactionEntity::getProductId).collect(Collectors.toSet());
        Set<Long> positionIds = sellTransactions.stream().map(InvestmentTransactionEntity::getPositionId).collect(Collectors.toSet());
        Map<Long, InvestmentProductEntity> productMap = investmentProductMapper.selectBatchIds(productIds).stream()
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        Map<Long, InvestmentPositionEntity> positionMap = investmentPositionMapper.selectBatchIds(positionIds).stream()
            .collect(Collectors.toMap(InvestmentPositionEntity::getId, Function.identity()));

        List<GoldLiquidationRecordResponse> records = sellTransactions.stream()
            .filter(transaction -> isGoldProduct(productMap.get(transaction.getProductId())))
            .map(transaction -> toLiquidationRecord(transaction, accountMap.get(transaction.getAccountId()), positionMap.get(transaction.getPositionId()), productMap.get(transaction.getProductId())))
            .toList();

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
        response.setRecords(records);
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

    private GoldAccountHoldingResponse toHoldingResponse(InvestmentPositionEntity position, AccountEntity account, InvestmentProductEntity product) {
        GoldAccountHoldingResponse response = new GoldAccountHoldingResponse();
        response.setId(position.getId());
        response.setPositionId(position.getId());
        response.setAccountId(position.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setProductId(position.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setCurrentPrice(scale(position.getCurrentPrice(), 2));
        response.setPurchaseAmount(scale(position.getCostAmount(), 2));
        response.setWeight(scale(position.getHoldingQuantity(), 6));
        response.setHoldingProfit(scale(position.getHoldingProfit(), 2));
        response.setMarketValue(scale(position.getMarketValue(), 2));
        response.setAvgCostPrice(scale(position.getAvgCostPrice(), 2));
        response.setCreatedAt(position.getCreatedAt());
        return response;
    }

    private GoldLiquidationRecordResponse toLiquidationRecord(
        InvestmentTransactionEntity transaction,
        AccountEntity account,
        InvestmentPositionEntity position,
        InvestmentProductEntity product
    ) {
        GoldLiquidationRecordResponse response = new GoldLiquidationRecordResponse();
        BigDecimal weight = scale(transaction.getQuantity(), 6);
        BigDecimal sellPrice = scale(transaction.getPrice(), 2);
        BigDecimal buyPrice = scale(position == null ? null : position.getAvgCostPrice(), 2);
        BigDecimal fee = scale(defaultZero(transaction.getFeeAmount()).add(defaultZero(transaction.getTaxAmount())), 2);
        BigDecimal soldCost = buyPrice.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = scale(defaultZero(transaction.getAmount()).subtract(fee).subtract(soldCost), 2);

        response.setId(transaction.getId());
        response.setAccountId(transaction.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setPositionId(transaction.getPositionId());
        response.setProductId(transaction.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setTradeAt(transaction.getTradeAt());
        response.setProfit(profit);
        response.setWeight(weight);
        response.setBuyPrice(buyPrice);
        response.setSellPrice(sellPrice);
        response.setFee(fee);
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
}
