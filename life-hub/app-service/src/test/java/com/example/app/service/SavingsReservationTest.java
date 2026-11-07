package com.example.app.service;

import com.example.app.dto.*;
import com.example.app.mapper.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SavingsReservationTest {
    private SavingsGoalService goals(AccountingDatabase db) {
        return new SavingsGoalService(db.mapper(SavingsGoalMapper.class), db.mapper(SavingsGoalEntryMapper.class),
            db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class), db.idempotency());
    }
    private void seed(AccountingDatabase db) {
        db.seedCashAccounts();
        db.jdbc.update("INSERT INTO categories(id,user_id,name,type,status) VALUES(1,1,'支出','expense','active'),(2,1,'收入','income','active')");
    }
    private long goal(AccountingDatabase db, long account, String amount) {
        return db.tx.execute(s -> goals(db).create(1, new SavingsGoalRequest("目标", account, new BigDecimal("2000"), null, null, null))).id();
    }
    private void allocate(AccountingDatabase db, long id, String amount) {
        db.tx.execute(s -> goals(db).addEntry(1, id, new SavingsGoalEntryRequest("deposit", new BigDecimal(amount),
            LocalDate.now(ZoneId.of("Asia/Shanghai")), null, UUID.randomUUID().toString())));
    }
    private TransactionRequest transaction(String type, String amount, SavingsAllocation... allocations) {
        var request = new TransactionRequest(); request.setUserId(1L); request.setType(type);
        request.setAmount(new BigDecimal(amount)); request.setAccountId(1L); request.setCategoryId("income".equals(type) ? 2L : 1L);
        request.setFromAccountId(1L); request.setToAccountId(2L); request.setOccurredAt(LocalDateTime.of(2026, 11, 7, 12, 0));
        request.setSavingsAllocations(List.of(allocations)); request.setRequestId(UUID.randomUUID().toString()); return request;
    }
    private SavingsAllocation part(long goal, String amount) { return new SavingsAllocation(goal, new BigDecimal(amount)); }
    private void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private BigDecimal saved(AccountingDatabase db, long id) { return db.mapper(SavingsGoalEntryMapper.class).savedAmount(id); }

    @Test void optionalExpenseDeductionProtectsAllGoalsAndRollbackRestoresBothSides() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db); long a=goal(db,1,"300"), b=goal(db,1,"200"); allocate(db,a,"300"); allocate(db,b,"200");
            db.tx.execute(s -> db.transactions().create(transaction("expense","400")));
            money("600",db.balance(1)); money("300",saved(db,a)); money("200",saved(db,b));
            var failure = assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().create(transaction("expense","200"))));
            assertTrue(failure.getMessage().contains("100.00"));
            var expense=db.tx.execute(s -> db.transactions().create(transaction("expense","200",part(a,"100"))));
            money("400",db.balance(1)); money("200",saved(db,a));
            var invalid = transaction("expense","500",part(a,"10"));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().update(expense.getId(),invalid)));
            money("400",db.balance(1)); money("200",saved(db,a));
            db.tx.execute(s -> db.transactions().delete(expense.getId(),1L));
            money("600",db.balance(1)); money("300",saved(db,a)); money("200",saved(db,b));
        }
    }

    @Test void incomeSupportsSplitReplayNetEditsAndSynchronizedDeletion() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db); long a=goal(db,1,"0"), b=goal(db,1,"0");
            var request=transaction("income","100",part(a,"60"),part(b,"40"));
            var income=db.tx.execute(s -> db.transactions().create(request));
            assertEquals(income.getId(),db.tx.execute(s -> db.transactions().create(request)).getId());
            money("1100",db.balance(1)); money("60",saved(db,a)); money("40",saved(db,b));
            db.tx.execute(s -> db.transactions().create(transaction("expense","1000")));
            money("100",db.balance(1));
            request.setRemark("仅改备注"); request.setSavingsAllocations(null);
            db.tx.execute(s -> db.transactions().update(income.getId(),request));
            money("100",db.balance(1)); money("60",saved(db,a));
            request.setAmount(new BigDecimal("120")); request.setSavingsAllocations(List.of(part(a,"70"),part(b,"50")));
            db.tx.execute(s -> db.transactions().update(income.getId(),request));
            money("120",db.balance(1)); money("70",saved(db,a)); money("50",saved(db,b));
            db.tx.execute(s -> db.transactions().delete(income.getId(),1L));
            money("0",db.balance(1)); money("0",saved(db,a)); money("0",saved(db,b));
        }
    }

    @Test void transferAllocationsAreOptionalAndReversibleOnBothAccounts() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db); long from=goal(db,1,"0"), to=goal(db,2,"0"); allocate(db,from,"500"); allocate(db,to,"100");
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().create(transaction("transfer","800"))));
            var request=transaction("transfer","800",part(from,"300"),part(to,"400"));
            var transfer=db.tx.execute(s -> db.transactions().create(request));
            money("200",db.balance(1)); money("1300",db.balance(2)); money("200",saved(db,from)); money("500",saved(db,to));
            db.tx.execute(s -> db.transactions().delete(transfer.getId(),1L));
            money("1000",db.balance(1)); money("500",db.balance(2)); money("500",saved(db,from)); money("100",saved(db,to));
        }
    }

    @Test void linkedIncomeCannotBeRemovedAfterItsGoalMoneyWasSpent() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db); long goal=goal(db,1,"0");
            var income=db.tx.execute(s -> db.transactions().create(transaction("income","100",part(goal,"100"))));
            var expense=db.tx.execute(s -> db.transactions().create(transaction("expense","50",part(goal,"50"))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().delete(income.getId(),1L)));
            money("1050",db.balance(1)); money("50",saved(db,goal));
            assertEquals(1,db.transactions().savingsAllocations(income.getId(),1).size());
            db.tx.execute(s -> db.transactions().delete(expense.getId(),1L));
            db.tx.execute(s -> db.transactions().delete(income.getId(),1L));
            money("1000",db.balance(1)); money("0",saved(db,goal));
        }
    }

    @Test void manualReservationAndOtherCashWritersCannotBypassTheFloor() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db); long goal=goal(db,1,"0"); allocate(db,goal,"1000");
            assertThrows(IllegalArgumentException.class, () -> allocate(db,goal,"0.01"));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.mapper(AccountMapper.class).subtractBalanceIfSufficient(1L,new BigDecimal("0.01"))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.mapper(AccountMapper.class).addBalance(1L,new BigDecimal("-0.01"))));
            db.jdbc.update("INSERT INTO account_types(id,code) VALUES(4,'debt')");
            db.jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,status) VALUES(4,1,4,'往来',0,'active')");
            var debt = new DebtAccountService(db.mapper(AccountMapper.class),db.mapper(AccountTypeMapper.class),db.mapper(DebtRecordMapper.class));
            var request = new DebtRecordRequest(); request.setUserId(1L);request.setAccountId(4L);request.setFundingAccountId(1L);
            request.setDirection("receivable");request.setRecordType("borrow");request.setAmount(BigDecimal.ONE);
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> debt.createRecord(request)));
            assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM debt_records",Integer.class));
            var transfer=transaction("transfer","1"); transfer.setFromAccountId(2L);transfer.setToAccountId(1L);
            var received=db.tx.execute(s -> db.transactions().create(transfer)); allocate(db,goal,"1");
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().delete(received.getId(),1L)));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> goals(db).update(1,goal,new SavingsGoalRequest("移动",2L,new BigDecimal("2000"),null,null,null))));
            money("1001",db.balance(1)); money("1001",saved(db,goal));
        }
    }

    @Test void invalidAndForeignAllocationsRollBackTheTransaction() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db); long a=goal(db,1,"0"), b=goal(db,2,"0"); allocate(db,a,"100");
            for (var request : List.of(transaction("income","50",part(a,"51")),transaction("income","50",part(b,"20")),
                transaction("expense","200",part(a,"101")),transaction("income","50",part(a,"20"),part(a,"20")))) {
                assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().create(request)));
            }
            db.jdbc.update("UPDATE savings_goals SET user_id=2 WHERE id=?",b);
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().create(transaction("income","50",part(b,"20")))));
            assertThrows(IllegalArgumentException.class, () -> db.transactions().savingsAllocations(999,1));
            money("1000",db.balance(1)); money("100",saved(db,a));
            assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions",Integer.class));
        }
    }

    @Test void concurrentExpenseAndReservationCannotSpendTheSameAvailableFunds() throws Exception {
        try (var db = new AccountingDatabase()) {
            seed(db); long goal=goal(db,1,"0"); allocate(db,goal,"100");
            var executor=Executors.newFixedThreadPool(2);
            try {
                Callable<Boolean> reserve=()->{try{allocate(db,goal,"500");return true;}catch(IllegalArgumentException expected){return false;}};
                Callable<Boolean> spend=()->{try{db.tx.execute(s->db.transactions().create(transaction("expense","600")));return true;}catch(IllegalArgumentException expected){return false;}};
                var first=executor.submit(reserve);var second=executor.submit(spend);
                assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
                assertTrue(db.balance(1).compareTo(saved(db,goal))>=0);
            } finally { executor.shutdownNow(); }
        }
    }
}
