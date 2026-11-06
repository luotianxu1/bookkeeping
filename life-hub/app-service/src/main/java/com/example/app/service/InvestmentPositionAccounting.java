package com.example.app.service;

import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentTransactionEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import static com.example.app.service.InvestmentCalcUtils.*;

/** 持仓数量、成本、冻结份额及已实现收益的计算；调用方负责事务和并发锁。 */
final class InvestmentPositionAccounting {
    private static final String ACTIVE_STATUS = "active";
    private static final String SUBSCRIPTION_STATUS_CONFIRMED = "confirmed";
    private InvestmentPositionAccounting() {}

    static void applyBuyTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount
    ) {
        BigDecimal nextHoldingQuantity = defaultZero(position.getHoldingQuantity()).add(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextAvailableQuantity = defaultZero(position.getAvailableQuantity()).add(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).add(amount).add(feeAmount).add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        position.setHoldingQuantity(nextHoldingQuantity);
        position.setAvailableQuantity(nextAvailableQuantity);
        position.setCostAmount(nextCostAmount);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            position.setCurrentPrice(price);
        }
        recalculatePositionMetrics(position, ACTIVE_STATUS);
    }

    static void applySellTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount
    ) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal availableQuantity = defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (quantity.compareTo(holdingQuantity) > 0 || quantity.compareTo(availableQuantity) > 0) {
            throw new IllegalArgumentException("卖出数量不能超过当前持仓");
        }

        BigDecimal nextHoldingQuantity = holdingQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextAvailableQuantity = availableQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal soldCostAmount = avgCostPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousCumulativeProfit = defaultZero(position.getCumulativeProfit()).setScale(2, RoundingMode.HALF_UP);
        if (nextCostAmount.compareTo(BigDecimal.ZERO) < 0) {
            nextCostAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        position.setHoldingQuantity(nextHoldingQuantity);
        position.setAvailableQuantity(nextAvailableQuantity);
        position.setCostAmount(nextCostAmount);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            position.setCurrentPrice(price);
        }
        recalculatePositionMetrics(position, nextHoldingQuantity.compareTo(BigDecimal.ZERO) == 0 ? "closed" : ACTIVE_STATUS);

        BigDecimal realizedProfit = amount.subtract(feeAmount).subtract(taxAmount).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        position.setCumulativeProfit(previousCumulativeProfit.add(realizedProfit).setScale(2, RoundingMode.HALF_UP));
        position.setCumulativeProfitRate(rate(position.getCumulativeProfit(), position.getCostAmount()));
    }

    static void freezePendingFundSellQuantity(InvestmentPositionEntity position, BigDecimal quantity) {
        BigDecimal availableQuantity = defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal frozenQuantity = defaultZero(position.getFrozenQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (quantity.compareTo(availableQuantity) > 0) {
            throw new IllegalArgumentException("赎回份额不能超过可用持仓");
        }
        position.setAvailableQuantity(availableQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP));
        position.setFrozenQuantity(frozenQuantity.add(quantity).setScale(6, RoundingMode.HALF_UP));
        position.setLastSyncedAt(LocalDateTime.now());
    }

    static void settlePendingFundSellTransaction(
        InvestmentPositionEntity position,
        InvestmentTransactionEntity transaction,
        BigDecimal confirmedPrice
    ) {
        BigDecimal quantity = defaultZero(transaction.getQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal frozenQuantity = defaultZero(position.getFrozenQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("赎回份额必须大于0");
        }
        if (quantity.compareTo(holdingQuantity) > 0 || quantity.compareTo(frozenQuantity) > 0) {
            throw new IllegalArgumentException("待确认赎回份额超过当前冻结持仓");
        }

        BigDecimal actualAmount = quantity.multiply(confirmedPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeAmount = defaultZero(transaction.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedAmount = defaultZero(transaction.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (estimatedAmount.compareTo(BigDecimal.ZERO) > 0 && feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal effectiveFeeRate = feeAmount.divide(estimatedAmount, 10, RoundingMode.HALF_UP);
            feeAmount = actualAmount.multiply(effectiveFeeRate).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal taxAmount = defaultZero(transaction.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextHoldingQuantity = holdingQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextFrozenQuantity = frozenQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal soldCostAmount = avgCostPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousCumulativeProfit = defaultZero(position.getCumulativeProfit()).setScale(2, RoundingMode.HALF_UP);

        if (nextCostAmount.compareTo(BigDecimal.ZERO) < 0) {
            nextCostAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        position.setHoldingQuantity(nextHoldingQuantity);
        position.setFrozenQuantity(nextFrozenQuantity);
        position.setCostAmount(nextCostAmount);
        position.setCurrentPrice(confirmedPrice.setScale(6, RoundingMode.HALF_UP));
        recalculatePositionMetrics(position, nextHoldingQuantity.compareTo(BigDecimal.ZERO) == 0 ? "closed" : ACTIVE_STATUS);

        BigDecimal realizedProfit = actualAmount.subtract(feeAmount).subtract(taxAmount).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        position.setCumulativeProfit(previousCumulativeProfit.add(realizedProfit).setScale(2, RoundingMode.HALF_UP));
        position.setCumulativeProfitRate(rate(position.getCumulativeProfit(), position.getCostAmount()));
        transaction.setAmount(actualAmount);
        transaction.setFeeAmount(feeAmount);
        transaction.setPrice(confirmedPrice.setScale(6, RoundingMode.HALF_UP));
    }

    static void recalculatePositionMetrics(InvestmentPositionEntity position, String status) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal marketValue = holdingQuantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = holdingQuantity.compareTo(BigDecimal.ZERO) > 0
            ? costAmount.divide(holdingQuantity, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cumulativeProfit = defaultZero(position.getCumulativeProfit()).setScale(2, RoundingMode.HALF_UP);

        position.setHoldingQuantity(holdingQuantity);
        position.setAvailableQuantity(defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP));
        position.setCostAmount(costAmount);
        position.setAvgCostPrice(avgCostPrice);
        position.setMarketValue(marketValue);
        position.setHoldingProfit(holdingProfit);
        position.setHoldingProfitRate(rate(holdingProfit, costAmount));
        position.setCumulativeProfit(cumulativeProfit);
        position.setCumulativeProfitRate(rate(cumulativeProfit, costAmount));
        position.setStatus(status);
        if (!"pending".equals(position.getSubscriptionStatus())) {
            position.setSubscriptionStatus(SUBSCRIPTION_STATUS_CONFIRMED);
            position.setSubscriptionConfirmedAt(LocalDateTime.now());
        }
        position.setLastSyncedAt(LocalDateTime.now());
    }

}
