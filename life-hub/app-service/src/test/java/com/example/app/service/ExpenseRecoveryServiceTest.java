package com.example.app.service;

import com.example.app.dto.*;
import com.example.app.mapper.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseRecoveryServiceTest {
    @Test void followUpPersistsWithPlanAndSummaryUsesAllPendingRowsPerCurrency() throws Exception {
        try (var db = new AccountingDatabase()) {
            long id = seed(db); var service = service(db);
            var due = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1);
            var plan = db.tx.execute(s -> service.plan(1L, id, new BigDecimal("200"), new ReimbursementFollowUp(" 公司财务 ", due)));
            assertEquals("公司财务", plan.getReimbursementRecipient()); assertEquals(due, plan.getExpectedReimbursementDate());
            db.tx.execute(s -> service.receive(1L, receipt(id, "reimbursement", "80")));
            db.jdbc.update("INSERT INTO transactions(id,user_id,type,account_id,amount,reimbursement_amount,currency_code,status,occurred_at) VALUES(900,1,'expense',1,60,50,'USD','normal','2026-01-31'),(901,2,'expense',1,900,900,'CNY','normal','2026-01-31')");
            var summaries = service.list(1L, "received", null, 1).pendingSummary();
            assertEquals(2, summaries.size());
            assertEquals("CNY", summaries.get(0).currencyCode()); equal("120", summaries.get(0).amount());
            equal("120", summaries.get(0).overdueAmount()); assertEquals(1, summaries.get(0).count());
            assertEquals("USD", summaries.get(1).currencyCode()); equal("50", summaries.get(1).amount()); equal("0", summaries.get(1).overdueAmount());
            assertEquals(1, service.list(1L, "all", id, 1).pendingSummary().size());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.plan(2L, id, new BigDecimal("200"), new ReimbursementFollowUp("他人", due))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.plan(1L, id, new BigDecimal("250"), new ReimbursementFollowUp("公司", LocalDate.of(2025,1,1)))));
            equal("200", service.detail(1L,id).getReimbursementAmount());
            db.tx.execute(s -> service.plan(1L, id, new BigDecimal("80")));
            assertEquals(due, service.detail(1L,id).getExpectedReimbursementDate()); // 旧客户端和撤销不清空跟进信息。
            assertTrue(service.list(1L, "all", id, 1).pendingSummary().isEmpty());
            db.tx.execute(s -> service.plan(1L, id, new BigDecimal("80"), new ReimbursementFollowUp(null, null)));
            assertNull(service.detail(1L,id).getExpectedReimbursementDate()); assertNull(service.detail(1L,id).getReimbursementRecipient());
            equal("700",db.balance(1)); equal("580",db.balance(2));
        }
    }
    private ExpenseRecoveryService service(AccountingDatabase db) {
        return new ExpenseRecoveryService(db.mapper(TransactionMapper.class), db.mapper(ExpenseRecoveryMapper.class),
            db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class), db.idempotency());
    }
    private long seed(AccountingDatabase db) {
        db.seedCashAccounts();
        db.jdbc.update("INSERT INTO categories(id,name,type,status) VALUES(1,'出差','expense','active')");
        var request = new TransactionRequest();
        request.setUserId(1L); request.setType("expense"); request.setAmount(new BigDecimal("300"));
        request.setCurrencyCode("CNY"); request.setAccountId(1L); request.setCategoryId(1L);
        request.setOccurredAt(LocalDateTime.of(2026,1,31,12,0));
        return db.tx.execute(s -> db.transactions().create(request)).getId();
    }
    private ExpenseRecoveryRequest receipt(long id, String kind, String amount) {
        return new ExpenseRecoveryRequest(id, 2L, new BigDecimal(amount), kind, LocalDateTime.of(2026,2,1,12,0), null, UUID.randomUUID().toString());
    }
    private void equal(String amount, BigDecimal actual) { assertEquals(0, new BigDecimal(amount).compareTo(actual)); }

    @Test void refundsAndPartialReimbursementsUseReceiptMonthAndReverseSafely() throws Exception {
        try (var db = new AccountingDatabase()) {
            long id = seed(db); var service = service(db);
            equal("700", db.balance(1));
            db.tx.execute(s -> service.plan(1L, id, new BigDecimal("200")));
            assertEquals("pending", service.detail(1L, id).getReimbursementStatus());
            var refundRequest = receipt(id, "refund", "100");
            var refund = db.tx.execute(s -> service.receive(1L, refundRequest));
            assertEquals(refund.id(), db.tx.execute(s -> service.receive(1L, refundRequest)).id());
            equal("600", db.balance(2));
            var partial = db.tx.execute(s -> service.receive(1L, receipt(id, "reimbursement", "80")));
            assertEquals("partial", service.detail(1L, id).getReimbursementStatus());
            assertEquals(1, service.list(1L, "partial", null, 1).total());
            equal("120", service.detail(1L, id).getPendingAmount());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L, receipt(id, "refund", "1"))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.plan(1L, id, new BigDecimal("79"))));
            db.tx.execute(s -> service.receive(1L, receipt(id, "reimbursement", "120")));
            assertEquals("received", service.detail(1L, id).getReimbursementStatus());
            equal("800", db.balance(2));
            var january = db.transactions().getAnalysis(1L, "month", "2026-01", null);
            var february = db.transactions().getAnalysis(1L, "month", "2026-02", null);
            equal("300", january.getSummary().getExpense()); equal("-300", february.getSummary().getExpense());
            equal("0", february.getSummary().getIncome());
            equal("-300", february.getExpenseBreakdown().get(0).getAmount());
            equal("0", february.getExpenseBreakdown().get(0).getPercent());
            var page = db.transactions().page(List.of(1L), "expense", 2L, LocalDate.of(2026,2,1), LocalDate.of(2026,2,28), "desc", true, 1, 50);
            equal("-300", page.getExpenseTotal()); equal("300", page.getBalanceTotal());
            assertTrue(page.getItems().stream().allMatch(t -> t.getOriginalTransactionId().equals(id)));
            assertEquals(3, service.detail(1L,id).getReceipts().size());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().delete(id, 1L)));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().update(refund.id(), updateRequest())));
            db.jdbc.update("UPDATE accounts SET current_balance=10 WHERE id=2");
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().delete(partial.id(), 1L)));
            equal("10", db.balance(2)); assertEquals(3, service.detail(1L,id).getReceipts().size());
            db.jdbc.update("UPDATE accounts SET current_balance=800 WHERE id=2");
            assertEquals(Boolean.TRUE, db.tx.execute(s -> db.transactions().delete(partial.id(),1L)));
            assertEquals(Boolean.FALSE, db.tx.execute(s -> db.transactions().delete(partial.id(),1L)));
            equal("720", db.balance(2)); assertEquals("partial", service.detail(1L,id).getReimbursementStatus());
        }
    }
    private TransactionRequest updateRequest() {
        var r = new TransactionRequest(); r.setUserId(1L); r.setType("expense"); r.setAmount(BigDecimal.ONE);
        r.setAccountId(1L); r.setCategoryId(1L); r.setOccurredAt(LocalDateTime.now()); return r;
    }
    @Test void cancellingPendingPlanPreservesExpenseBalancesAndReceivedMoney() throws Exception {
        try (var db = new AccountingDatabase()) {
            long id = seed(db); var service = service(db);
            db.tx.execute(s -> service.plan(1L, id, new BigDecimal("200")));
            db.tx.execute(s -> service.plan(1L, id, BigDecimal.ZERO));
            var cancelled = service.detail(1L, id);
            assertEquals("none", cancelled.getReimbursementStatus());
            equal("0", cancelled.getPendingAmount()); equal("300", cancelled.getAmount());
            equal("300", cancelled.getRecoverableAmount());
            assertEquals(0, service.list(1L, "pending", null, 1).total());
            equal("700", db.balance(1)); equal("500", db.balance(2));

            db.tx.execute(s -> service.plan(1L, id, new BigDecimal("200")));
            var received = db.tx.execute(s -> service.receive(1L, receipt(id, "reimbursement", "80")));
            // 页面金额落后于新到账时，清零必须被拒绝，不能影响已到账记录。
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.plan(1L, id, BigDecimal.ZERO)));
            equal("120", service.detail(1L, id).getPendingAmount());
            db.tx.execute(s -> service.plan(1L, id, new BigDecimal("80")));
            var remainingCancelled = service.detail(1L, id);
            equal("0", remainingCancelled.getPendingAmount()); equal("80", remainingCancelled.getReimbursedAmount());
            assertEquals(List.of(received.id()), remainingCancelled.getReceipts().stream().map(ExpenseRecoveryResponse.Receipt::id).toList());
            equal("700", db.balance(1)); equal("580", db.balance(2));
            equal("-80", db.transactions().getAnalysis(1L, "month", "2026-02", null).getSummary().getExpense());
        }
    }
    @Test void foreignAccountsOriginalsInvalidDatesAndLimitsNeverChangeBalances() throws Exception {
        try (var db = new AccountingDatabase()) {
            long id = seed(db); var service = service(db);
            assertThrows(IllegalArgumentException.class, () -> service.detail(2L,id));
            assertEquals(0, service.list(2L,"all",null,1).total());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.plan(2L,id,BigDecimal.ONE)));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(2L,receipt(id,"refund","1"))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,receipt(id,"refund","301"))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,receipt(id,"reimbursement","1"))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,receipt(id,"refund","0.001"))));
            for (long account : List.of(3L, 99L)) assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,
                new ExpenseRecoveryRequest(id,account,BigDecimal.ONE,"refund",LocalDateTime.of(2026,2,1,0,0),null,null))));
            for (var date : List.of(LocalDateTime.of(2026,1,1,0,0), LocalDateTime.now().plusDays(1)))
                assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,
                    new ExpenseRecoveryRequest(id,2L,BigDecimal.ONE,"refund",date,null,null))));
            db.jdbc.update("UPDATE accounts SET user_id=2 WHERE id=2");
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,receipt(id,"refund","1"))));
            db.jdbc.update("UPDATE accounts SET user_id=1,currency_code='USD' WHERE id=2");
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,receipt(id,"refund","1"))));
            equal("700", db.balance(1)); equal("500", db.balance(2));
            assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions",Integer.class));
        }
    }
    @Test void concurrentReceiptsEnforceSharedLimitAndRetryIsExactlyOnce() throws Exception {
        try (var db = new AccountingDatabase()) {
            long id = seed(db); var service = service(db); var executor = Executors.newFixedThreadPool(2);
            try {
                var start = new CountDownLatch(1);
                Callable<Boolean> job = () -> { start.await(); try { db.tx.execute(s -> service.receive(1L,receipt(id,"refund","200"))); return true; }
                    catch (IllegalArgumentException expected) { return false; } };
                var a = executor.submit(job); var b = executor.submit(job); start.countDown();
                assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)); equal("700",db.balance(2));
                var request = receipt(id,"refund","100"); var retry = new CountDownLatch(1);
                Callable<Long> same = () -> { retry.await(); return db.tx.execute(s -> service.receive(1L,request)).id(); };
                var c = executor.submit(same); var d = executor.submit(same); retry.countDown();
                assertEquals(c.get(10,TimeUnit.SECONDS),d.get(10,TimeUnit.SECONDS)); equal("800",db.balance(2));
                assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.receive(1L,
                    new ExpenseRecoveryRequest(id,2L,BigDecimal.ONE,"refund",request.occurredAt(),null,request.requestId()))));
            } finally { executor.shutdownNow(); }
        }
    }
}
