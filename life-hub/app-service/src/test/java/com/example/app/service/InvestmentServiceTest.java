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
        return service(db, quotes, market, mock(GoldPriceService.class), db.mapper(InvestmentPositionMapper.class));
    }
    private InvestmentService service(AccountingDatabase db, InvestmentQuoteSyncService quotes, MarketQuoteClient market,
        GoldPriceService gold, InvestmentPositionMapper positions) {
        return new InvestmentService(db.mapper(InvestmentProductMapper.class), positions,
            db.mapper(InvestmentTransactionMapper.class), db.mapper(InvestmentAutoInvestPlanMapper.class),
            db.mapper(InvestmentDividendPlanMapper.class), db.mapper(InvestmentDividendRecordMapper.class),
            db.mapper(InvestmentFixedExpenseMapper.class), db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class),
            db.mapper(InvestmentPriceQuoteMapper.class), gold, new ObjectMapper(), db.manager,
            mock(MarketCalendarService.class), 2, market, mock(InvestmentDividendService.class), quotes,
            mock(InvestmentPriceHistoryService.class), mock(InvestmentFundProfitService.class),
            new InvestmentFundFeeService(market, db.mapper(InvestmentTransactionMapper.class)), self -> self == 1L || self == 2L ? java.util.Set.of(1L, 2L) : java.util.Set.of(self), db.idempotency());
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
    @Test void investmentBuyCannotSpendSavingsReservations() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "stock");
            db.jdbc.update("INSERT INTO savings_goals(id,user_id,account_id,name,target_amount,currency_code) VALUES(1,1,1,'预留',1000,'CNY')");
            db.jdbc.update("INSERT INTO savings_goal_entries(goal_id,type,amount,occurred_on) VALUES(1,'deposit',1000,CURRENT_DATE)");
            var service = service(db, mock(InvestmentQuoteSyncService.class), mock(MarketQuoteClient.class));
            try {
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(buy()));
                assertEquals(0, new BigDecimal("1000").compareTo(db.balance(1)));
                assertEquals(0, new BigDecimal("100").compareTo(db.mapper(InvestmentPositionMapper.class).selectById(1L).getHoldingQuantity()));
                assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM investment_transactions", Integer.class));
            } finally { service.shutdownExecutor(); }
        }
    }
    @Test void detailDoesNotFetchExternalFundFees() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "fund");
            var market = mock(MarketQuoteClient.class);
            var service = service(db, mock(InvestmentQuoteSyncService.class), market);
            try {
                var request = new org.springframework.mock.web.MockHttpServletRequest();
                request.setAttribute(com.example.common.security.SecurityUtils.USER_ID_ATTRIBUTE, 1L);
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
                var detail = service.getPositionDetail(1L).orElseThrow();
                assertTrue(detail.getFundRedeemFeeOptions().isEmpty());
                verifyNoInteractions(market);
            } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); service.shutdownExecutor(); }
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
            } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); service.shutdownExecutor(); }
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
            } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); service.shutdownExecutor(); }
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
            } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); service.shutdownExecutor(); }
        }
    }

    private void asUser(long id) {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(com.example.common.security.SecurityUtils.USER_ID_ATTRIBUTE, id);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
    }

    @Test void familyCanReadButCannotEditAndOutsiderCannotReadPosition() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "stock");
            var service = service(db, mock(InvestmentQuoteSyncService.class), mock(MarketQuoteClient.class));
            try {
                asUser(2L);
                assertTrue(service.getPosition(1L).isPresent());
                assertTrue(service.getPositionDetail(1L).isPresent());
                var edit = new com.example.app.dto.InvestmentPositionRequest();
                edit.setUserId(2L); edit.setAccountId(3L);
                assertTrue(service.updatePosition(1L, edit).isEmpty());
                assertFalse(service.deletePosition(1L, 2L));
                asUser(3L);
                assertTrue(service.getPosition(1L).isEmpty());
                assertTrue(service.getPositionDetail(1L).isEmpty());
                assertEquals(1L, db.mapper(InvestmentPositionMapper.class).selectById(1L).getUserId());
            } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); service.shutdownExecutor(); }
        }
    }

    @Test void goldQuoteRunsOutsideTransactionAndFailureUsesLocalValue() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "gold");
            db.jdbc.update("UPDATE account_types SET code='gold' WHERE id=2");
            var gold = mock(GoldPriceService.class);
            when(gold.getCachedSpotPrice()).thenAnswer(call -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                return new BigDecimal("2");
            });
            var service = service(db, mock(InvestmentQuoteSyncService.class), mock(MarketQuoteClient.class), gold, db.mapper(InvestmentPositionMapper.class));
            try {
                var edit = new com.example.app.dto.InvestmentPositionRequest();
                edit.setUserId(1L); edit.setAccountId(3L); edit.setProductId(1L);
                edit.setHoldingQuantity(new BigDecimal("100")); edit.setAvailableQuantity(new BigDecimal("100"));
                edit.setCostAmount(new BigDecimal("100")); edit.setCurrentPrice(BigDecimal.ONE);
                assertTrue(service.updatePosition(1L, edit).isPresent());
                assertEquals(0, new BigDecimal("200").compareTo(db.balance(3)));
                when(gold.getCachedSpotPrice()).thenThrow(new IllegalStateException("offline"));
                assertTrue(service.updatePosition(1L, edit).isPresent());
                assertEquals(0, new BigDecimal("100").compareTo(db.balance(3)));
                assertTrue(service.deletePosition(1L, 1L));
                assertEquals(0, BigDecimal.ZERO.compareTo(db.balance(3)));
            } finally { service.shutdownExecutor(); }
        }
    }

    @Test void quoteSyncAggregatesOnceForTwoPositionsInSameAccount() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "stock");
            db.jdbc.update("INSERT INTO investment_positions(id,user_id,account_id,product_id,holding_quantity,available_quantity,cost_amount,current_price,market_value,status,subscription_status) VALUES(2,1,3,1,50,50,50,1,50,'active','confirmed')");
            db.jdbc.update("UPDATE accounts SET remark='保留备注' WHERE id=3");
            var positions = mock(InvestmentPositionMapper.class, org.mockito.AdditionalAnswers.delegatesTo(db.mapper(InvestmentPositionMapper.class)));
            var quotes = mock(InvestmentQuoteSyncService.class);
            doCallRealMethod().when(quotes).applyStockQuoteToPosition(any(), any(), any(), any());
            when(quotes.fetchAndSaveLatestStockQuote(any())).thenAnswer(call -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                return new InvestmentQuoteSyncService.StockQuoteSnapshot(LocalDate.now(), new BigDecimal("2"), BigDecimal.ONE, LocalDateTime.now());
            });
            var service = service(db, quotes, mock(MarketQuoteClient.class), mock(GoldPriceService.class), positions);
            try {
                assertEquals(2, service.syncDailyStockQuotes(1L, 3L));
                verify(positions, times(1)).sumAccountValue(1L, 3L, null);
                assertEquals(0, new BigDecimal("300").compareTo(db.balance(3)));
                assertEquals("保留备注", db.jdbc.queryForObject("SELECT remark FROM accounts WHERE id=3", String.class));
            } finally { service.shutdownExecutor(); }
        }
    }

    @Test void duplicateInvestmentRetryReplaysWithoutFetchingQuotes() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "stock");
            var quotes = mock(InvestmentQuoteSyncService.class);
            var service = service(db, quotes, mock(MarketQuoteClient.class));
            try {
                var request = buy(); request.setRequestId(java.util.UUID.randomUUID().toString());
                var first = service.createTransaction(request);
                assertEquals(first.getId(), service.createTransaction(request).getId());
                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM investment_transactions", Integer.class));
                assertEquals(0, new BigDecimal("990").compareTo(db.balance(1)));
                verifyNoInteractions(quotes);
            } finally { service.shutdownExecutor(); }
        }
    }
    @Test void concurrentGoldSaleRetryCreditsCashAndReducesHoldingsOnce() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db, "gold");
            db.jdbc.update("UPDATE account_types SET code='gold' WHERE id=2");
            var gold = mock(GoldPriceService.class);
            when(gold.getCachedSpotPrice()).thenAnswer(call -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                return BigDecimal.ONE;
            });
            var service = service(db, mock(InvestmentQuoteSyncService.class), mock(MarketQuoteClient.class), gold,
                db.mapper(InvestmentPositionMapper.class));
            var executor = Executors.newFixedThreadPool(2);
            try {
                var sale = buy(); sale.setTradeType("sell"); sale.setRequestId(java.util.UUID.randomUUID().toString());
                var start = new CountDownLatch(1);
                Callable<com.example.app.dto.InvestmentTransactionResponse> submit = () -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS)); return service.createTransaction(sale);
                };
                var first = executor.submit(submit); var second = executor.submit(submit); start.countDown();
                var response = first.get(10, TimeUnit.SECONDS);
                assertEquals(response.getId(), second.get(10, TimeUnit.SECONDS).getId());
                clearInvocations(gold);
                assertEquals(response.getId(), service.createTransaction(sale).getId());
                verifyNoInteractions(gold);
                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM investment_transactions", Integer.class));
                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM accounting_requests", Integer.class));
                var position = db.mapper(InvestmentPositionMapper.class).selectById(1L);
                assertEquals(0, new BigDecimal("90").compareTo(position.getHoldingQuantity()));
                assertEquals(0, new BigDecimal("90").compareTo(position.getAvailableQuantity()));
                assertEquals(0, new BigDecimal("1010").compareTo(db.balance(1)));
                assertEquals(0, new BigDecimal("90").compareTo(db.balance(3)));
            } finally { executor.shutdownNow(); service.shutdownExecutor(); }
        }
    }

    private com.example.app.dto.InvestmentPositionRequest newPosition(String type) {
        var request = new com.example.app.dto.InvestmentPositionRequest();
        request.setUserId(1L); request.setAccountId(3L); request.setFundingAccountId(1L);
        request.setRequestId(java.util.UUID.randomUUID().toString());
        request.setHoldingQuantity(BigDecimal.TEN); request.setAvailableQuantity(BigDecimal.TEN);
        request.setCostAmount(new BigDecimal("100")); request.setCurrentPrice(BigDecimal.TEN);
        request.setTradeAt(LocalDateTime.of(2026,11,6,10,0));
        var product = new com.example.app.dto.InvestmentProductRequest();
        product.setProductType(type); product.setSymbol(" new-product "); product.setName("新产品"); product.setMarket(" cn ");
        request.setProduct(product);
        return request;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"stock", "fund", "gold"})
    void positionRetryCreatesOnePositionAndDebitsOnce(String type) throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            if (type.equals("gold")) db.jdbc.update("UPDATE account_types SET code='gold' WHERE id=2");
            var quotes = mock(InvestmentQuoteSyncService.class);
            when(quotes.fetchFundBaseInfo(any())).thenAnswer(call -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                return new ObjectMapper().readTree("{\"Datas\":{\"SGZT\":\"开放申购\",\"DWJZ\":\"10\"}}");
            });
            var service = service(db, quotes, mock(MarketQuoteClient.class));
            var executor = Executors.newFixedThreadPool(2);
            try {
                var request = newPosition(type);
                var start = new CountDownLatch(1);
                Callable<com.example.app.dto.InvestmentPositionResponse> submit = () -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS)); return service.createPosition(request);
                };
                var first = executor.submit(submit); var second = executor.submit(submit); start.countDown();
                var response = first.get(10, TimeUnit.SECONDS);
                assertEquals(response.getId(), second.get(10, TimeUnit.SECONDS).getId());
                when(quotes.fetchFundBaseInfo(any())).thenThrow(new IllegalStateException("upstream unavailable"));
                assertEquals(response.getId(), service.createPosition(request).getId());
                assertEquals(0, new BigDecimal("900").compareTo(db.balance(1)));
                for (String table : java.util.List.of("investment_positions", "investment_products", "investment_transactions", "accounting_requests")) {
                    assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
                }
                assertEquals("CN", db.jdbc.queryForObject("SELECT market FROM investment_products", String.class));
                assertEquals("NEW-PRODUCT", db.jdbc.queryForObject("SELECT symbol FROM investment_products", String.class));
                request.setCostAmount(new BigDecimal("101"));
                assertThrows(IllegalArgumentException.class, () -> service.createPosition(request));
            } finally { executor.shutdownNow(); service.shutdownExecutor(); }
        }
    }

    @Test void failedPositionCreationRollsBackProductAndRequestKey() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            var service = service(db, mock(InvestmentQuoteSyncService.class), mock(MarketQuoteClient.class));
            try {
                var request = newPosition("stock"); request.setCostAmount(new BigDecimal("1001"));
                assertThrows(IllegalArgumentException.class, () -> service.createPosition(request));
                for (String table : java.util.List.of("investment_positions", "investment_products", "investment_transactions", "accounting_requests")) {
                    assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
                }
                db.jdbc.update("UPDATE accounts SET current_balance=2000 WHERE id=1");
                assertNotNull(service.createPosition(request));
                assertEquals(0, new BigDecimal("999").compareTo(db.balance(1)));
            } finally { service.shutdownExecutor(); }
        }
    }

}
