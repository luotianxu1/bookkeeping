package com.example.app.service;

import com.example.app.dto.*;
import com.example.app.mapper.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SharedCategoryTest {
    @Test void publicRootsDoNotExposeAnotherUsersChildrenOrBlockAccounting() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts();
            db.jdbc.update("INSERT INTO categories(id,user_id,name,type,status,parent_id,is_system) VALUES "
                + "(1,NULL,'餐饮','expense','active',NULL,TRUE),"
                + "(2,2,'午餐','expense','active',1,FALSE),"
                + "(3,1,'已停用','expense','disabled',1,FALSE)");
            var mapper = db.mapper(CategoryMapper.class);
            var categories = new CategoryService(mapper, db.mapper(TransactionMapper.class),
                db.mapper(RenewalSubscriptionMapper.class), db.mapper(CategoryBudgetMapper.class), db.mapper(AccountMapper.class));
            assertEquals(List.of(1L), categories.list(1L, "expense", "active").stream().map(CategoryResponse::getId).toList());
            assertEquals(List.of(1L, 2L), categories.list(2L, "expense", "active").stream().map(CategoryResponse::getId).toList());
            assertEquals(0, mapper.countActiveChildrenForUser(1L, 1L));
            assertEquals(1, mapper.countActiveChildrenForUser(1L, 2L));

            var transaction = new TransactionRequest();
            transaction.setUserId(1L); transaction.setAccountId(1L); transaction.setCategoryId(1L);
            transaction.setType("expense"); transaction.setAmount(new BigDecimal("12"));
            transaction.setOccurredAt(LocalDateTime.of(2026, 11, 9, 12, 0));
            assertNotNull(db.tx.execute(s -> db.transactions().create(transaction)));
            transaction.setCategoryId(2L);
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().create(transaction)));

            var budgets = new CategoryBudgetService(db.mapper(CategoryBudgetMapper.class), mapper, db.mapper(AccountMapper.class), db.transactions());
            var budget = new CategoryBudgetRequest(LocalDate.of(2026,11,1),1L,new BigDecimal("100"));
            db.tx.execute(s -> { budgets.save(1L, budget); return null; });
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> { budgets.save(2L, budget); return null; }));

            var renewals = new RenewalSubscriptionService(db.mapper(RenewalSubscriptionMapper.class), db.mapper(AccountMapper.class),
                db.mapper(AccountTypeMapper.class), mapper, db.mapper(TransactionMapper.class), db.manager);
            var renewal = new RenewalSubscriptionRequest();
            renewal.setUserId(1L); renewal.setName("每月餐费"); renewal.setAmount(new BigDecimal("20"));
            renewal.setFundingAccountId(1L); renewal.setCategoryId(1L); renewal.setBillingDay(10);
            renewal.setNextBillingDate(LocalDate.of(2027,1,10)); renewal.setBillingCycle("monthly");
            assertNotNull(db.tx.execute(s -> renewals.create(renewal)));

            db.jdbc.update("INSERT INTO categories(id,user_id,name,type,status,parent_id,is_system) VALUES(4,1,'晚餐','expense','active',1,FALSE)");
            transaction.setCategoryId(1L);
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> db.transactions().create(transaction)));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> renewals.create(renewal)));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> { budgets.save(1L, budget); return null; }));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> categories.delete(1L, 1L)));

            db.jdbc.update("INSERT INTO categories(id,user_id,name,type,status,parent_id,is_system) VALUES(5,NULL,'公共子分类','expense','active',1,TRUE)");
            assertEquals(1, mapper.countActiveChildrenForUser(1L, 3L));
        }
    }
}
