package com.example.app.service;

import com.example.app.dto.DebtRecordRequest;
import com.example.app.dto.HumanRelationRecordRequest;
import com.example.app.mapper.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CashLedgerWritersTest {
    @Test void editingDebtOnSameFundingAccountReversesThenReappliesAmount() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO account_types(id,code) VALUES(4,'debt')");
            db.jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,status) VALUES(4,1,4,'往来',0,'active')");
            var service = new DebtAccountService(db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class), db.mapper(DebtRecordMapper.class));
            var request = new DebtRecordRequest();
            request.setUserId(1L); request.setAccountId(4L); request.setFundingAccountId(1L);
            request.setDirection("receivable"); request.setRecordType("borrow"); request.setAmount(new BigDecimal("100"));
            var record = db.tx.execute(s -> service.createRecord(request));
            request.setAmount(new BigDecimal("150"));
            db.tx.execute(s -> service.updateRecord(record.getId(), request));
            assertEquals(0, new BigDecimal("850").compareTo(db.balance(1)));
            db.tx.execute(s -> service.deleteRecord(record.getId(), 1L));
            assertEquals(0, new BigDecimal("1000").compareTo(db.balance(1)));
        }
    }

    @Test void editingGiftOnSameFundingAccountDoesNotLoseRefund() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO account_types(id,code) VALUES(4,'human_relation')");
            db.jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,status) VALUES(4,1,4,'人情',0,'active')");
            var service = new HumanRelationAccountService(db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class), db.mapper(HumanRelationRecordMapper.class));
            var request = new HumanRelationRecordRequest();
            request.setUserId(1L); request.setAccountId(4L); request.setFundingAccountId(1L);
            request.setDirection("outgoing"); request.setAmount(new BigDecimal("100"));
            var record = db.tx.execute(s -> service.createRecord(request));
            request.setAmount(new BigDecimal("150"));
            db.tx.execute(s -> service.updateRecord(record.getId(), request));
            assertEquals(0, new BigDecimal("850").compareTo(db.balance(1)));
            db.tx.execute(s -> service.deleteRecord(record.getId(), 1L));
            assertEquals(0, new BigDecimal("1000").compareTo(db.balance(1)));
        }
    }

    @Test void concurrentRenewalRunsChargeDueCycleOnlyOnce() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            db.jdbc.update("INSERT INTO categories(id,user_id,name,type,status) VALUES(1,1,'订阅','expense','active')");
            db.jdbc.update("INSERT INTO renewal_subscriptions(id,user_id,name,amount,funding_account_id,category_id,billing_day,billing_cycle,next_billing_date,status) VALUES(1,1,'月费',100,1,1,?,'monthly',?,'active')", today.getDayOfMonth(), today);
            var service = new RenewalSubscriptionService(db.mapper(RenewalSubscriptionMapper.class), db.mapper(AccountMapper.class),
                db.mapper(AccountTypeMapper.class), db.mapper(CategoryMapper.class), db.mapper(TransactionMapper.class), db.manager);
            var executor = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                Callable<Void> run = () -> { assertTrue(start.await(5, TimeUnit.SECONDS)); service.processDueSubscription(1L); return null; };
                var first = executor.submit(run); var second = executor.submit(run); start.countDown();
                first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
                assertEquals(0, new BigDecimal("900").compareTo(db.balance(1)));
                assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
            } finally { executor.shutdownNow(); }
        }
    }
    @Test void photographyAndLedgerRollbackCannotRefundTheSameIncomeTwice() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO transactions(id,user_id,type,account_id,amount,status,occurred_at) VALUES(1,1,'income',1,100,'normal',CURRENT_TIMESTAMP)");
            var photo = new PhotographyAccountingService(db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class),
                db.mapper(CategoryMapper.class), db.mapper(TransactionMapper.class));
            var executor = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                var first = executor.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return db.tx.execute(s -> photo.voidPhotographyIncome(1L, 1L)); });
                var second = executor.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return db.tx.execute(s -> db.transactions().delete(1L, 1L)); });
                start.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
                assertEquals(0, new BigDecimal("900").compareTo(db.balance(1)));
                assertEquals("voided", db.jdbc.queryForObject("SELECT status FROM transactions WHERE id=1", String.class));
            } finally { executor.shutdownNow(); }
        }
    }
}
