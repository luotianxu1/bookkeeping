package com.example.app.service;

import com.example.app.dto.*;
import com.example.app.dto.TermDepositDtos.*;
import com.example.app.entity.*;
import com.example.app.mapper.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TermDepositServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026,11,8);
    private TermDepositService service(AccountingDatabase db) {
        return new TermDepositService(db.mapper(TermDepositMapper.class),db.mapper(TermDepositOperationMapper.class),db.mapper(AccountMapper.class),
            db.mapper(AccountTypeMapper.class),db.mapper(TransactionMapper.class),db.mapper(CategoryMapper.class),db.savingsAllocations(),db.idempotency(),
            Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneOffset.UTC));
    }
    private void seed(AccountingDatabase db) {
        db.seedCashAccounts();
        db.jdbc.update("INSERT INTO account_types(id,code) VALUES(4,'term_deposit')");
        db.jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,currency_code,status) VALUES(4,1,4,'定期',0,'CNY','active'),(5,2,4,'他人定期',0,'CNY','active'),(6,1,1,'美元',1000,'USD','active')");
        db.jdbc.update("INSERT INTO categories(id,user_id,name,type,status) VALUES(1,1,'利息','income','active'),(2,1,'消费','expense','active'),(3,2,'他人','income','active')");
    }
    private String requestId() { return UUID.randomUUID().toString(); }
    private Create create(String mode, LocalDate start, LocalDate end, SavingsAllocation... parts) {
        return new Create(4L,1L,2L,"一年定期",new BigDecimal("800"),new BigDecimal("2"),start,end,12,"withdraw",mode,null,List.of(parts),requestId());
    }
    private Create due(String mode, SavingsAllocation... parts) { return create(mode,TODAY.minusYears(1),TODAY,parts); }
    private Settle settle(String action) { return new Settle(action,2L,1L,new BigDecimal("16"),TODAY,new BigDecimal("1.8"),TODAY.plusYears(1),12,null,requestId()); }
    private long operation(TermDepositService s, long id) { return s.detail(1,id).operations().stream().filter(o->Set.of("withdraw","early_withdraw","renew_all","renew_principal").contains(o.operation()) && o.reversedAt()==null).findFirst().orElseThrow().id(); }
    private void money(String value, BigDecimal actual) { assertEquals(0,new BigDecimal(value).compareTo(actual), actual.toString()); }
    private BigDecimal term(AccountingDatabase db) { return db.mapper(AccountMapper.class).termDepositPrincipal(4L); }
    private void reserve(AccountingDatabase db, String value) {
        db.jdbc.update("INSERT INTO savings_goals(id,user_id,account_id,name,currency_code,target_amount) VALUES(1,1,1,'目标','CNY',1000)");
        db.jdbc.update("INSERT INTO savings_goal_entries(goal_id,type,amount,occurred_on) VALUES(1,'deposit',?,?)",new BigDecimal(value),TODAY);
    }
    @Test void principalMovesOnceIncomeOnlyOnSettlementAndEverythingCanReverse() throws Exception {
        try(var db=new AccountingDatabase()) {
            seed(db); var s=service(db); var request=due("new");
            var d=db.tx.execute(t->s.create(1,request));
            assertEquals(d.id(),db.tx.execute(t->s.create(1,request)).id());
            assertEquals("due",d.status());money("16",d.expectedInterest()); money("200",db.balance(1)); money("800",term(db));
            assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type='income'",Integer.class));
            var settle=settle("withdraw");
            db.tx.execute(t->s.settle(1,d.id(),settle));db.tx.execute(t->s.settle(1,d.id(),settle));
            money("1316",db.balance(2));money("0",term(db));
            money("16",db.jdbc.queryForObject("SELECT SUM(amount) FROM transactions WHERE type='income' AND status='normal'",BigDecimal.class));
            long ledger=db.jdbc.queryForObject("SELECT id FROM transactions WHERE type='income'",Long.class);
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->db.transactions().delete(ledger,1L)));
            var edit=new TransactionRequest();edit.setUserId(1L);
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->db.transactions().update(ledger,edit)));
            var reverse=new Reverse(operation(s,d.id()),requestId());
            db.tx.execute(t->s.reverse(1,d.id(),reverse));db.tx.execute(t->s.reverse(1,d.id(),reverse));
            money("500",db.balance(2));money("800",term(db));
            var cancel=new Cancel(requestId());db.tx.execute(t->s.cancel(1,d.id(),cancel));db.tx.execute(t->s.cancel(1,d.id(),cancel));
            money("1000",db.balance(1));money("0",term(db));assertTrue(s.list(1,null).isEmpty());
            assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE status='normal'",Integer.class));
        }
    }
    @Test void backfillAndMonthEndForecastDoNotTouchCash() throws Exception {
        try(var db=new AccountingDatabase()) {
            seed(db);var s=service(db);var d=db.tx.execute(t->s.create(1,due("existing")));
            money("1000",db.balance(1));money("800",term(db));assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions",Integer.class));
            var e=new TermDepositEntity();e.setPrincipal(new BigDecimal("10000"));e.setAnnualRate(new BigDecimal("3"));e.setStartDate(LocalDate.of(2024,1,31));e.setMaturityDate(LocalDate.of(2024,2,29));e.setTermMonths(1);
            money("25",TermDepositService.expectedInterest(e));e.setTermMonths(null);money("23.84",TermDepositService.expectedInterest(e));
            var edit=new Edit("更正",2L,new BigDecimal("2.5"),d.startDate(),d.maturityDate().plusDays(1),null,"renew_all","备注",requestId());
            db.tx.execute(t->s.edit(1,d.id(),edit));assertNull(s.detail(1,d.id()).deposit().termMonths());
            db.tx.execute(t->s.cancel(1,d.id(),new Cancel(requestId())));money("1000",db.balance(1));money("0",term(db));
        }
    }
    @Test void reservationsAreOptionalProtectedAndRestoredOnCancellation() throws Exception {
        try(var db=new AccountingDatabase()) {
            seed(db);reserve(db,"500");var s=service(db);
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.create(1,due("new"))));
            assertTrue(s.list(1,null).isEmpty());money("1000",db.balance(1));
            var d=db.tx.execute(t->s.create(1,due("new",new SavingsAllocation(1L,new BigDecimal("300")))));
            money("200",db.balance(1));money("200",db.mapper(SavingsGoalEntryMapper.class).savedAmount(1));
            db.tx.execute(t->s.cancel(1,d.id(),new Cancel(requestId())));
            money("1000",db.balance(1));money("500",db.mapper(SavingsGoalEntryMapper.class).savedAmount(1));
        }
    }
    @Test void renewalsChainAndReverseInOrderWithoutDuplicatingPrincipal() throws Exception {
        for(String action:List.of("renew_principal","renew_all"))try(var db=new AccountingDatabase()) {
            seed(db);var s=service(db);var d=db.tx.execute(t->s.create(1,due("new")));
            db.tx.execute(t->s.settle(1,d.id(),settle(action)));
            money(action.equals("renew_all")?"500":"516",db.balance(2));money(action.equals("renew_all")?"816":"800",term(db));
            long op=operation(s,d.id());long child=s.detail(1,d.id()).operations().stream().filter(o->o.id()==op).findFirst().orElseThrow().childDepositId();
            assertFalse(s.detail(1,child).deposit().canCancel());
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.cancel(1,child,new Cancel(requestId()))));
            db.tx.execute(t->s.settle(1,child,settle("withdraw")));
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.reverse(1,d.id(),new Reverse(op,requestId()))));
            db.tx.execute(t->s.reverse(1,child,new Reverse(operation(s,child),requestId())));
            db.tx.execute(t->s.reverse(1,d.id(),new Reverse(op,requestId())));
            money("500",db.balance(2));money("800",term(db));assertEquals("voided",s.detail(1,child).deposit().status());
        }
    }
    @Test void spentOrReservedPayoutPreventsUndoWithoutPartialChanges() throws Exception {
        try(var db=new AccountingDatabase()) {
            seed(db);var s=service(db);var d=db.tx.execute(t->s.create(1,due("new")));db.tx.execute(t->s.settle(1,d.id(),settle("withdraw")));
            db.jdbc.update("UPDATE accounts SET current_balance=800 WHERE id=2");
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.reverse(1,d.id(),new Reverse(operation(s,d.id()),requestId()))));
            assertEquals("closed",s.detail(1,d.id()).deposit().status());money("0",term(db));money("800",db.balance(2));
            db.jdbc.update("UPDATE accounts SET current_balance=1316 WHERE id=2");reserve(db,"600");db.jdbc.update("UPDATE savings_goals SET account_id=2");
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.reverse(1,d.id(),new Reverse(operation(s,d.id()),requestId()))));
            assertEquals("closed",s.detail(1,d.id()).deposit().status());money("1316",db.balance(2));
        }
    }
    @Test void earlyWithdrawalUsesActualInterestAndRejectsInvalidOwnersCurrencyAndRenewal() throws Exception {
        try(var db=new AccountingDatabase()) {
            seed(db);var s=service(db);var request=create("new",TODAY.minusMonths(1),TODAY.plusMonths(11));
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.create(2,request)));
            var d=db.tx.execute(t->s.create(1,request));
            assertThrows(IllegalArgumentException.class,()->s.detail(2,d.id()));assertTrue(s.list(2,null).isEmpty());
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.settle(1,d.id(),settle("renew_all"))));
            for(Long account:List.of(6L,5L))assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.settle(1,d.id(),new Settle("withdraw",account,1L,BigDecimal.ONE,TODAY,null,null,null,null,requestId()))));
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.settle(1,d.id(),new Settle("withdraw",2L,3L,BigDecimal.ONE,TODAY,null,null,null,null,requestId()))));
            assertThrows(IllegalArgumentException.class,()->db.tx.execute(t->s.settle(1,d.id(),new Settle("withdraw",2L,null,new BigDecimal("-1"),TODAY,null,null,null,null,requestId()))));
            db.tx.execute(t->s.settle(1,d.id(),new Settle("withdraw",2L,null,BigDecimal.ZERO,TODAY,null,null,null,null,requestId())));
            money("1300",db.balance(2));money("0",term(db));assertEquals("early_withdraw",s.detail(1,d.id()).operations().get(0).operation());
        }
    }
    @Test void concurrentSettlementsOnlyCreditOnce() throws Exception {
        try(var db=new AccountingDatabase()) {
            seed(db);var s=service(db);var d=db.tx.execute(t->s.create(1,due("new")));
            var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
            try {
                Callable<Boolean> settle=()->{start.await();try{db.tx.execute(t->s.settle(1,d.id(),settle("withdraw")));return true;}catch(IllegalArgumentException e){return false;}};
                var a=pool.submit(settle);var b=pool.submit(settle);start.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
                money("1316",db.balance(2));money("0",term(db));
            } finally { pool.shutdownNow(); }
        }
    }
}
