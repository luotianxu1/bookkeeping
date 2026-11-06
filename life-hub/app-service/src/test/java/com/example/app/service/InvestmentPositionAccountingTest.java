package com.example.app.service;

import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentTransactionEntity;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.app.service.InvestmentPositionAccounting.*;

class InvestmentPositionAccountingTest {
    private BigDecimal n(String value) { return new BigDecimal(value); }
    private InvestmentPositionEntity position() {
        var p = new InvestmentPositionEntity(); p.setHoldingQuantity(n("100")); p.setAvailableQuantity(n("100"));
        p.setFrozenQuantity(n("0")); p.setCostAmount(n("200")); p.setAvgCostPrice(n("2")); p.setCurrentPrice(n("2"));
        p.setCumulativeProfit(n("0")); p.setSubscriptionStatus("confirmed"); return p;
    }
    @Test void buyAndSellPreserveQuantityCostAndRealizedProfit() {
        var p = position();
        applyBuyTransaction(p, n("50"), n("3"), n("150"), n("1"), n("0"));
        assertEquals(0, n("150").compareTo(p.getHoldingQuantity()));
        assertEquals(0, n("351").compareTo(p.getCostAmount()));
        applySellTransaction(p, n("50"), n("3"), n("150"), n("1"), n("0"));
        assertEquals(0, n("100").compareTo(p.getHoldingQuantity()));
        assertEquals(0, n("234").compareTo(p.getCostAmount()));
        assertEquals(0, n("32").compareTo(p.getCumulativeProfit()));
    }
    @Test void fundConfirmationConsumesFrozenSharesAndRepricesFee() {
        var p = position(); freezePendingFundSellQuantity(p, n("40"));
        var trade = new InvestmentTransactionEntity(); trade.setQuantity(n("40"));
        trade.setAmount(n("80")); trade.setFeeAmount(n("1.20")); trade.setTaxAmount(n("0"));
        settlePendingFundSellTransaction(p, trade, n("3"));
        assertEquals(0, n("60").compareTo(p.getHoldingQuantity()));
        assertEquals(0, n("60").compareTo(p.getAvailableQuantity()));
        assertEquals(0, n("0").compareTo(p.getFrozenQuantity()));
        assertEquals(0, n("120").compareTo(trade.getAmount()));
        assertEquals(0, n("1.80").compareTo(trade.getFeeAmount()));
        assertEquals(0, n("38.20").compareTo(p.getCumulativeProfit()));
    }
    @Test void rejectsOversellingWithoutChangingPosition() {
        var p = position();
        assertThrows(IllegalArgumentException.class, () -> applySellTransaction(p, n("101"), n("3"), n("303"), n("0"), n("0")));
        assertEquals(n("100"), p.getHoldingQuantity());
    }
}
