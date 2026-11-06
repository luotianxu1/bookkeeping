package com.example.app.service;

import com.example.app.client.MarketQuoteClient;
import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentTransactionEntity;
import com.example.app.mapper.InvestmentTransactionMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestmentFundFeeServiceTest {
    @Test void parsesBothBoundsOfFeeRange() {
        var service = new InvestmentFundFeeService(mock(MarketQuoteClient.class), mock(InvestmentTransactionMapper.class));
        var rule = service.parseFundRedeemFeeRule("大于等于7天，小于30天", "0.5%");
        assertEquals(7, rule.minHoldingDaysInclusive());
        assertEquals(30, rule.maxHoldingDaysExclusive());
        assertEquals(new BigDecimal("0.005"), rule.feeRate());
    }
    @Test void fifoFeesRespectPriorRedemptionsAndReinvestedDividends() {
        var mapper = mock(InvestmentTransactionMapper.class);
        var service = new InvestmentFundFeeService(mock(MarketQuoteClient.class), mapper);
        var today = LocalDate.of(2026, 11, 6);
        var position = new InvestmentPositionEntity(); position.setId(1L); position.setHoldingQuantity(new BigDecimal("110"));
        position.setCreatedAt(today.minusDays(40).atStartOfDay());
        var buy = trade("buy", "100", today.minusDays(40));
        var reinvest = trade("dividend_reinvest", "30", today.minusDays(2));
        var sold = trade("sell", "20", today.minusDays(1));
        when(mapper.selectList(any())).thenReturn(List.of(buy, reinvest, sold));
        var rules = List.of(service.parseFundRedeemFeeRule("小于7天", "1.5%"),
            service.parseFundRedeemFeeRule("大于等于7天，小于30天", "0.5%"),
            service.parseFundRedeemFeeRule("大于等于30天", "0%"));
        // 老份额剩 80，卖 100 时只有新分红份额中的 20 收 1.5%，净值为 2。
        assertEquals(new BigDecimal("0.60"), service.calculateFundSellFeeAmount(position, rules, new BigDecimal("100"), today, new BigDecimal("2")));
    }
    private InvestmentTransactionEntity trade(String type, String quantity, LocalDate date) {
        var result = new InvestmentTransactionEntity(); result.setTradeType(type); result.setQuantity(new BigDecimal(quantity));
        result.setSettlementStatus("confirmed"); result.setSettlementConfirmedAt(date.atStartOfDay()); return result;
    }
}
