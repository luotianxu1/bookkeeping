package com.example.app.service;

import com.example.app.client.MarketQuoteClient;
import com.example.app.dto.InvestmentTransactionRequest;
import com.example.app.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestmentServiceTest {
    private InvestmentService service(AccountingDatabase db, InvestmentQuoteSyncService quotes, MarketQuoteClient market) {
        return new InvestmentService(db.mapper(InvestmentProductMapper.class), db.mapper(InvestmentPositionMapper.class),
            db.mapper(InvestmentTransactionMapper.class), db.mapper(InvestmentAutoInvestPlanMapper.class),
            db.mapper(InvestmentDividendPlanMapper.class), db.mapper(InvestmentDividendRecordMapper.class),
            db.mapper(InvestmentFixedExpenseMapper.class), db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class),
            db.mapper(InvestmentPriceQuoteMapper.class), mock(GoldPriceService.class), new ObjectMapper(), db.manager,
            mock(MarketCalendarService.class), 2, market, mock(InvestmentDividendService.class), quotes,
            mock(InvestmentPriceHistoryService.class), mock(InvestmentFundProfitService.class),
            new InvestmentFundFeeService(market, db.mapper(InvestmentTransactionMapper.class)));
    }
    private void seed(AccountingDatabase db, String type) {
        db.seedCashAccounts();
        db.jdbc.update("INSERT INTO investment_products(id,product_type,symbol,name,market,currency_code,unit_name,price_precision) VALUES(1,?,'000001','测试资产','CN','CNY','份',4)", type);
        db.jdbc.update("INSERT INTO investment_positions(id,user_id,account_id,product_id,holding_quantity,available_quantity,frozen_quantity,cost_amount,avg_cost_price,current_price,market_value,cumulative_profit,status,subscription_status,dividend_type,dividend_funding_account_id,created_at) VALUES(1,1,3,1,100,100,0,100,1,1,100,0,'active','confirmed','cash',1,CURRENT_TIMESTAMP)");
    }
    private InvestmentTransactionRequest buy() {
        var r = new InvestmentTransactionRequest(); r.setUserId(1L); r.setAccountId(3L); r.setProductId(1L); r.setPositionId(1L);
        r.setFundingAccountId(1L); r.setTradeType("buy"); r.setQuantity(new BigDecimal("10")); r.setAmount(new BigDecimal("10"));
        r.setPrice(BigDecimal.ONE); r.setTradeAt(LocalDateTime.now()); return r;
    }
    @Test void concurrentBuysDoNotLoseSharesOrCash() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "stock");
            var service = service(db, mock(InvestmentQuoteSyncService.class), mock(MarketQuoteClient.class));
            var executor = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                Callable<Void> task = () -> { start.await(5, TimeUnit.SECONDS); service.createTransaction(buy()); return null; };
                var a = executor.submit(task); var b = executor.submit(task); start.countDown();
                a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
                var position = db.mapper(InvestmentPositionMapper.class).selectById(1L);
                assertEquals(0, new BigDecimal("120").compareTo(position.getHoldingQuantity()));
                assertEquals(0, new BigDecimal("120").compareTo(position.getCostAmount()));
                assertEquals(0, new BigDecimal("980").compareTo(db.balance(1)));
            } finally { executor.shutdownNow(); service.shutdownExecutor(); }
        }
    }
    @Test void detailDoesNotFetchExternalFundFees() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "fund");
            var market = mock(MarketQuoteClient.class);
            var service = service(db, mock(InvestmentQuoteSyncService.class), market);
            try {
                var detail = service.getPositionDetail(1L).orElseThrow();
                assertTrue(detail.getFundRedeemFeeOptions().isEmpty());
                verifyNoInteractions(market);
            } finally { service.shutdownExecutor(); }
        }
    }
    @Test void repeatedDividendSyncCreditsCashOnlyOnce() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "fund");
            var date = LocalDate.of(2026, 11, 6);
            db.jdbc.update("INSERT INTO investment_dividend_plans(id,product_id,pay_date,dividend_per_unit,tax_rate,status) VALUES(1,1,?,0.1,0,'active')", date);
            var quotes = mock(InvestmentQuoteSyncService.class);
            when(quotes.fetchAndSaveLatestFundQuote(any(), any())).thenReturn(
                new InvestmentQuoteSyncService.FundQuoteSnapshot(date, BigDecimal.ONE, BigDecimal.ONE, date.atTime(21,30)));
            var service = service(db, quotes, mock(MarketQuoteClient.class));
            try {
                assertEquals(1, service.syncDailyFundProfits(1L, 3L));
                assertEquals(1, service.syncDailyFundProfits(1L, 3L));
                assertEquals(0, new BigDecimal("1010").compareTo(db.balance(1)));
                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM investment_dividend_records", Integer.class));
            } finally { service.shutdownExecutor(); }
        }
    }
    @Test void editingPendingSellReleasesOldFrozenSharesBeforeApplyingNewQuantity() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "fund");
            db.jdbc.update("UPDATE investment_positions SET available_quantity=80,frozen_quantity=20 WHERE id=1");
            db.jdbc.update("INSERT INTO investment_transactions(id,user_id,account_id,position_id,product_id,trade_type,quantity,amount,funding_account_id,status,settlement_status) VALUES(1,1,3,1,1,'sell',20,20,1,'normal','pending')");
            var quotes = mock(InvestmentQuoteSyncService.class);
            when(quotes.fetchFundBaseInfo(any())).thenReturn(new ObjectMapper().createObjectNode());
            var service = service(db, quotes, mock(MarketQuoteClient.class));
            try {
                var request = buy();
                request.setTradeType("sell"); request.setQuantity(new BigDecimal("30")); request.setAmount(new BigDecimal("30"));
                assertTrue(service.updatePendingTransaction(1L, request).isPresent());
                var position = db.mapper(InvestmentPositionMapper.class).selectById(1L);
                assertEquals(0, new BigDecimal("70").compareTo(position.getAvailableQuantity()));
                assertEquals(0, new BigDecimal("30").compareTo(position.getFrozenQuantity()));
                assertEquals(0, new BigDecimal("1000").compareTo(db.balance(1)));
            } finally { service.shutdownExecutor(); }
        }
    }

    @Test void slowQuoteSyncCannotOverwriteTradeCompletedDuringFetch() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "stock");
            var fetching = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var quotes = mock(InvestmentQuoteSyncService.class);
            when(quotes.fetchAndSaveLatestStockQuote(any())).thenAnswer(invocation -> {
                fetching.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return new InvestmentQuoteSyncService.StockQuoteSnapshot(LocalDate.now(), BigDecimal.ONE, BigDecimal.ONE, LocalDateTime.now());
            });
            var service = service(db, quotes, mock(MarketQuoteClient.class));
            var executor = Executors.newSingleThreadExecutor();
            try {
                var sync = executor.submit(() -> service.syncDailyStockQuotes(1L, 3L));
                assertTrue(fetching.await(5, TimeUnit.SECONDS));
                service.createTransaction(buy());
                release.countDown();
                assertEquals(1, sync.get(10, TimeUnit.SECONDS));
                var position = db.mapper(InvestmentPositionMapper.class).selectById(1L);
                assertEquals(0, new BigDecimal("110").compareTo(position.getHoldingQuantity()));
                assertEquals(0, new BigDecimal("110").compareTo(position.getCostAmount()));
                assertEquals(0, new BigDecimal("990").compareTo(db.balance(1)));
                assertEquals(0, new BigDecimal("110").compareTo(db.balance(3)));
            } finally { release.countDown(); executor.shutdownNow(); service.shutdownExecutor(); }
        }
    }
    @Test void autoInvestSkipsPlanEditedDuringQuoteFetchEvenWithUnchangedTimestamp() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "fund");
            db.jdbc.update("INSERT INTO investment_auto_invest_plans(id,user_id,account_id,position_id,product_id,funding_account_id,amount,frequency,next_execute_date,status,updated_at) VALUES(1,1,3,1,1,1,100,'daily',?,'active','2026-11-06 09:00:00')", LocalDate.now());
            var quotes = mock(InvestmentQuoteSyncService.class);
            var edited = new java.util.concurrent.atomic.AtomicBoolean();
            when(quotes.fetchFundBaseInfo(any())).thenAnswer(invocation -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                if (edited.compareAndSet(false, true)) {
                    db.tx.executeWithoutResult(status -> {
                        db.mapper(AccountMapper.class).lockUserForWrite(1L);
                        db.jdbc.update("UPDATE investment_auto_invest_plans SET amount=200 WHERE id=1");
                    });
                }
                return new ObjectMapper().readTree("{\"Datas\":{\"SGZT\":\"开放申购\"}}");
            });
            var service = service(db, quotes, mock(MarketQuoteClient.class));
            try {
                assertEquals(0, service.executeDueAutoInvestPlans());
                assertEquals(0, new BigDecimal("1000").compareTo(db.balance(1)));
                assertEquals(1, service.executeDueAutoInvestPlans());
                assertEquals(0, new BigDecimal("800").compareTo(db.balance(1)));
                assertEquals(0, service.executeDueAutoInvestPlans());
                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM investment_transactions", Integer.class));
            } finally { service.shutdownExecutor(); }
        }
    }
}
