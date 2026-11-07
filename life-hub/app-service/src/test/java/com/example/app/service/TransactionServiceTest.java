package com.example.app.service;

import com.example.app.dto.TransactionRequest;
import com.example.app.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    @Test void searchFiltersBeforePaginationAndKeepsSignedTotalsAndUserScope() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO categories(id,name,type,parent_id) VALUES(1,'餐饮','expense',NULL),(2,'咖啡','expense',1),(3,'交通','expense',NULL)");
            db.jdbc.update("INSERT INTO transactions(id,user_id,type,account_id,category_id,title,remark,amount,status,occurred_at) VALUES"
                + "(1,1,'expense',1,2,'咖啡','出差',120,'normal','2026-02-01'),"
                + "(2,1,'expense',1,2,'退款 · 咖啡','',-20,'normal','2026-02-02'),"
                + "(3,1,'expense',1,3,'车费','出差',50,'normal','2026-02-03'),"
                + "(4,2,'expense',1,2,'咖啡','出差',100,'normal','2026-02-01'),"
                + "(5,1,'expense',1,2,'优惠100%_','备注',10,'normal','2026-02-01'),"
                + "(6,1,'expense',1,2,'咖啡','',999,'voided','2026-02-01')");
            var search = new com.example.app.dto.TransactionSearch(" 咖啡 ", 1L, new BigDecimal("20"), new BigDecimal("120"));
            var first = db.transactions().page(List.of(1L), "expense", null, null, null, "asc", true, 1, 1, search);
            assertEquals(2, first.getTotal()); assertEquals(2, first.getTotalPages());
            assertEquals(1L, first.getItems().get(0).getId());
            assertEquals(0, new BigDecimal("100").compareTo(first.getExpenseTotal()));
            var second = db.transactions().page(List.of(1L), "expense", null, null, null, "asc", true, 2, 1, search);
            assertEquals(2L, second.getItems().get(0).getId());
            assertEquals(first.getExpenseTotal(), second.getExpenseTotal());
            var remark = db.transactions().page(List.of(1L), null, null, null, null, "desc", true, 1, 20,
                new com.example.app.dto.TransactionSearch("出差", null, null, null));
            assertEquals(2, remark.getTotal());
            var literal = db.transactions().page(List.of(1L), null, null, null, null, "desc", true, 1, 20,
                new com.example.app.dto.TransactionSearch("%_", null, null, null));
            assertEquals(1, literal.getTotal()); assertEquals(5L, literal.getItems().get(0).getId());
            assertThrows(IllegalArgumentException.class, () -> new com.example.app.dto.TransactionSearch(null, null, new BigDecimal("2"), BigDecimal.ONE));
            assertThrows(IllegalArgumentException.class, () -> new com.example.app.dto.TransactionSearch(null, null, new BigDecimal("-1"), null));
            assertThrows(IllegalArgumentException.class, () -> new com.example.app.dto.TransactionSearch(null, null, null, new BigDecimal("0.001")));
        }
    }
    @Test void transferAndRollbackPreserveTotalBalance() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            var service = db.transactions();
            var request = new TransactionRequest();
            request.setUserId(1L); request.setType("transfer");
            request.setFromAccountId(1L); request.setToAccountId(2L);
            request.setAmount(new BigDecimal("125.50")); request.setOccurredAt(LocalDateTime.now());
            var record = db.tx.execute(status -> service.create(request));
            assertEquals(0, new BigDecimal("874.50").compareTo(db.balance(1)));
            assertEquals(0, new BigDecimal("625.50").compareTo(db.balance(2)));
            assertEquals(Boolean.TRUE, db.tx.execute(status -> service.delete(record.getId(), 1L)));
            assertEquals(0, new BigDecimal("1000").compareTo(db.balance(1)));
            assertEquals(0, new BigDecimal("500").compareTo(db.balance(2)));
        }
    }
    @Test void concurrentDeleteRollsBackExpenseExactlyOnce() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO transactions(id,user_id,type,account_id,amount,status,occurred_at) VALUES(1,1,'expense',1,100,'normal',CURRENT_TIMESTAMP)");
            var service = db.transactions();
            var executor = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                Callable<Boolean> delete = () -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return db.tx.execute(s -> service.delete(1L, 1L)); };
                var first = executor.submit(delete); var second = executor.submit(delete);
                start.countDown();
                assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                assertEquals(0, new BigDecimal("1100").compareTo(db.balance(1)));
                assertEquals("voided", db.jdbc.queryForObject("SELECT status FROM transactions WHERE id=1", String.class));
            } finally { executor.shutdownNow(); }
        }
    }
    @Test void failedTransferRollsBackItsRecordAndBothBalances() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            var request = new TransactionRequest();
            request.setUserId(1L); request.setType("transfer"); request.setFromAccountId(1L); request.setToAccountId(2L);
            request.setAmount(new BigDecimal("1001")); request.setOccurredAt(LocalDateTime.now());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().create(request)));
            assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
            assertEquals(0, new BigDecimal("1000").compareTo(db.balance(1)));
            assertEquals(0, new BigDecimal("500").compareTo(db.balance(2)));
        }
    }
    @Test void analysisAndCombinedSummaryUseSameBoundedCashLedger() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO transactions(id,user_id,type,account_id,amount,status,occurred_at) VALUES(1,1,'income',1,100,'normal','2026-02-01'),(2,1,'expense',1,20,'normal','2026-02-28 23:59:59'),(3,1,'expense',1,999,'normal','2026-03-01'),(4,2,'income',1,999,'normal','2026-02-01')");
            var analysis = db.transactions().getAnalysis(1L, "month", "2026-02", null);
            var sums = db.mapper(TransactionMapper.class).summarizeMerged(List.of(1L), null, null,
                LocalDate.of(2026,2,1).atStartOfDay(), LocalDate.of(2026,3,1).atStartOfDay(), java.util.Set.of(1L,2L), null);
            assertEquals(2, sums.getTotal());
            assertEquals(0, new BigDecimal("100").compareTo(sums.getIncomeTotal()));
            var page = db.transactions().page(List.of(1L), null, null, LocalDate.of(2026,2,1), LocalDate.of(2026,2,28), "desc", true, 1, 20);
            assertEquals(2, page.getTotal());
            assertEquals(0, new BigDecimal("80").compareTo(page.getBalanceTotal()));
            assertEquals("2026-02", analysis.getMonth());
            assertEquals(0, new BigDecimal("100").compareTo(analysis.getSummary().getIncome()));
        }
    }
}
