package com.example.app.service;
import com.example.app.dto.*;
import com.example.app.mapper.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class CategoryBudgetServiceTest {
    @Test void categoryBudgetIsMonthlyNetCashExpenseAndIsolatedByOwner() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO categories(id,user_id,name,type,status,parent_id) VALUES(1,NULL,'餐饮','expense','active',NULL),(2,NULL,'午餐','expense','active',1),(3,2,'私有','expense','active',NULL),(4,NULL,'收入','income','active',NULL)");
            var budgets = new CategoryBudgetService(db.mapper(CategoryBudgetMapper.class), db.mapper(CategoryMapper.class), db.mapper(AccountMapper.class), db.transactions());
            var month = LocalDate.of(2026,2,1);
            for (long id : new long[]{1,3,4}) assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> { budgets.save(1L,new CategoryBudgetRequest(month,id,new BigDecimal("100"))); return null; }));
            db.tx.execute(s -> { budgets.save(1L,new CategoryBudgetRequest(month,2L,new BigDecimal("100"))); return null; });
            db.jdbc.update("INSERT INTO transactions(user_id,type,account_id,category_id,amount,status,occurred_at,currency_code) VALUES(1,'expense',1,2,100,'normal','2026-02-01','CNY'),(1,'expense',2,2,-20,'normal','2026-02-02','CNY'),(1,'expense',1,2,999,'normal','2026-01-01','CNY'),(2,'expense',1,2,999,'normal','2026-02-01','CNY'),(1,'expense',3,2,999,'normal','2026-02-01','CNY'),(1,'expense',1,2,999,'normal','2026-02-01','USD')");
            var result = budgets.list(1L,month).get(0);
            assertEquals(0,new BigDecimal("80").compareTo(result.usedAmount()));
            assertEquals(0,new BigDecimal("20").compareTo(result.remainingAmount()));
            assertEquals(0,new BigDecimal("80").compareTo(result.usagePercent()));
            assertTrue(budgets.list(2L,month).isEmpty()); assertTrue(budgets.list(1L,month.plusMonths(1)).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> { budgets.delete(2L,result.id()); return null; }));
            db.tx.execute(s -> { budgets.save(1L,new CategoryBudgetRequest(month,2L,new BigDecimal("200"))); return null; });
            assertEquals(1,budgets.list(1L,month).size());
            assertEquals(result.id(),budgets.list(1L,month).get(0).id());
            var monthly = new MonthlyBudgetService(db.mapper(MonthlyBudgetMapper.class),db.transactions(),db.mapper(AccountMapper.class));
            var request = new MonthlyBudgetRequest(); request.setUserId(1L);request.setBudgetMonth(month);request.setAmount(new BigDecimal("500"));request.setCurrencyCode("CNY");
            var total = db.tx.execute(s -> monthly.create(request));
            assertEquals(0,new BigDecimal("80").compareTo(total.getUsedAmount()));
            request.setUserId(2L);
            assertTrue(db.tx.execute(s -> monthly.update(total.getId(),request)).isEmpty());
        }
    }
}
