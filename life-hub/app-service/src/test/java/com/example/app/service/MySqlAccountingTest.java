package com.example.app.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.example.app.dto.TransactionRequest;
import com.example.app.dto.TransactionResponse;
import com.example.app.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 仅连接显式指定的测试实例。每次新建随机 schema，跳过脚本中指向业务库的 CREATE DATABASE / USE。 */
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql://.+")
class MySqlAccountingTest {
    @Test void transactionSearchSupportsMixedTitleCollations() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        String user = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String schema = "life_hub_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        try {
            String base = url.split("\\?", 2)[0];
            String params = url.contains("?") ? url.substring(url.indexOf('?')) : "";
            String server = base.substring(0, base.indexOf('/', "jdbc:mysql://".length()));
            var source = new DriverManagerDataSource(server + "/" + schema + params, user, password);
            var jdbc = new JdbcTemplate(source);
            for (String file : List.of("001_create_database_and_users", "002_create_account_types", "003_create_accounts",
                "004_create_categories", "005_create_transactions", "014_create_debt_records", "016_create_human_relation_records",
                "045_create_reminder_preferences")) {
                initializeFile(jdbc, file + ".mysql.sql", false);
            }
            // 复现既有库：账户名称用 unicode_ci，流水及往来记录用 general_ci。
            jdbc.execute("ALTER TABLE accounts MODIFY name VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL");
            for (String table : List.of("transactions", "debt_records", "human_relation_records"))
                jdbc.execute("ALTER TABLE " + table + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            jdbc.update("INSERT INTO users(id,username,display_name,password_hash) VALUES(1,'search','Search','test'),(2,'other','Other','test')");
            jdbc.update("INSERT INTO account_types(id,code,name) VALUES(1,'cash','现金'),(2,'debt','债务'),(3,'human','人情')");
            jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name) VALUES(1,1,1,'钱包'),(2,1,2,'朋友借款'),(3,1,3,'朋友礼金')");
            jdbc.update("INSERT INTO categories(id,name,type,icon) VALUES(1,'运动','expense','other')");
            for (int i = 1; i <= 11; i++) {
                jdbc.update("INSERT INTO transactions(id,transaction_no,user_id,type,amount,account_id,category_id,title,occurred_at) VALUES(?,?,1,'expense',20,1,1,?,'2026-11-07')",
                    i, "search-" + i, i == 1 ? "Keep 会员" : "其他消费" + i);
            }
            jdbc.update("INSERT INTO transactions(transaction_no,user_id,type,amount,account_id,category_id,title,status,occurred_at) VALUES"
                + "('other',2,'expense',999,1,1,'keep','normal','2026-11-07'),"
                + "('voided',1,'expense',999,1,1,'keep','voided','2026-11-07')");
            var config = new MybatisConfiguration();
            for (var type : List.of(TransactionMapper.class, AccountMapper.class, AccountTypeMapper.class, CategoryMapper.class)) config.addMapper(type);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source); factory.setConfiguration(config);
            factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/TransactionMapper.xml"));
            var session = new SqlSessionTemplate(factory.getObject());
            var service = new TransactionService(session.getMapper(TransactionMapper.class), session.getMapper(AccountMapper.class),
                session.getMapper(AccountTypeMapper.class), session.getMapper(CategoryMapper.class), null, null, null);
            var start = java.time.LocalDate.of(2026, 11, 1);
            var end = start.withDayOfMonth(30);
            var unfiltered = service.page(List.of(1L), null, null, start, end, "desc", true, 1, 50);
            assertEquals(11, unfiltered.getTotal());
            for (String keyword : List.of("keep", "KEEP", " Keep ")) {
                var result = service.page(List.of(1L), null, null, start, end, "desc", true, 2, 1,
                    new com.example.app.dto.TransactionSearch(keyword, null, null, null));
                assertEquals(1, result.getTotal()); assertEquals(1, result.getPage());
                assertEquals("Keep 会员", result.getItems().get(0).getTitle());
                assertEquals(0, new BigDecimal("20").compareTo(result.getExpenseTotal()));
            }
            jdbc.update("INSERT INTO debt_records(user_id,account_id,funding_account_id,direction,record_type,amount,remark,occurred_at) VALUES"
                + "(1,2,1,'payable','borrow',100,'会议垫付','2026-11-07')");
            jdbc.update("INSERT INTO human_relation_records(user_id,account_id,funding_account_id,direction,amount,remark,occurred_at) VALUES"
                + "(1,3,1,'outgoing',50,'会议礼物100%_','2026-11-07')");
            for (String title : List.of("朋友借款 · 借入", "朋友礼金 · 送出")) {
                var result = service.page(List.of(1L), null, null, start, end, "desc", true, 1, 50,
                    new com.example.app.dto.TransactionSearch(title, null, null, null));
                assertEquals(1, result.getTotal()); assertEquals(title, result.getItems().get(0).getTitle());
            }
            var remarks = service.page(List.of(1L), null, null, start, end, "desc", true, 1, 1,
                new com.example.app.dto.TransactionSearch("会议", null, null, null));
            assertEquals(2, remarks.getTotal()); assertEquals(2, remarks.getTotalPages());
            assertEquals(0, new BigDecimal("100").compareTo(remarks.getIncomeTotal()));
            assertEquals(0, new BigDecimal("50").compareTo(remarks.getExpenseTotal()));
            var literal = service.page(List.of(1L), null, null, start, end, "desc", true, 1, 50,
                new com.example.app.dto.TransactionSearch("%_", null, null, null));
            assertEquals(1, literal.getTotal());
            assertEquals("human_relation_record", literal.getItems().get(0).getSourceType());
        } finally { admin.execute("DROP DATABASE " + schema); }
    }

    @Test void productIdentityIsEnforcedByFreshTableDefinition() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        String user = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String schema = "life_hub_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        try {
            var jdbc = new JdbcTemplate(testDataSource(url, schema, user, password));
            String script = Files.readString(Path.of("../../database/007_create_investments.mysql.sql"));
            var table = Pattern.compile("(?ms)^CREATE TABLE IF NOT EXISTS investment_products \\(.*?\\) ENGINE=.*?;").matcher(script);
            assertTrue(table.find());
            jdbc.execute(table.group());
            jdbc.update("INSERT INTO investment_products(id,product_type,symbol,name,market) VALUES(101,'gold','DUP','Original',NULL)");
            for (String market : new String[]{null, "", " "}) {
                var duplicate = assertThrows(org.springframework.dao.DataAccessException.class, () ->
                    jdbc.update("INSERT INTO investment_products(product_type,symbol,name,market) VALUES('gold',' dup ','Duplicate',?)", market));
                assertEquals(1062, ((java.sql.SQLException) duplicate.getMostSpecificCause()).getErrorCode());
            }
            assertEquals("Original", jdbc.queryForObject("SELECT name FROM investment_products WHERE id=101", String.class));
            assertEquals("DUP", jdbc.queryForObject("SELECT symbol_key FROM investment_products WHERE id=101", String.class));
            assertEquals("", jdbc.queryForObject("SELECT market_key FROM investment_products WHERE id=101", String.class));
            assertNull(jdbc.queryForObject("SELECT market FROM investment_products WHERE id=101", String.class));
            jdbc.update("INSERT INTO investment_products(product_type,symbol,name,market) VALUES('gold','DUP','HK product','HK'),('stock','DUP','Stock',NULL)");
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM investment_products", Integer.class));
        } finally { admin.execute("DROP DATABASE " + schema); }
    }

    /** 从空库按顺序执行全部初始化文件，覆盖结构、基础数据和 MySQL 5.7 / 8 金额约束。 */
    @Test void freshSchemaIncludesAllFeaturesAndPreservesInitialDefaults() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        String user = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String schema = "life_hub_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        try {
            var jdbc = new JdbcTemplate(testDataSource(url, schema, user, password));
            try (var files = Files.list(Path.of("../../database"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".mysql.sql")).sorted().toList())
                    initializeFile(jdbc, file.getFileName().toString(), true);
            }
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM debt_records", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM photography_orders", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM food_dishes", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM food_orders", Integer.class));
            assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM account_types", Integer.class));
            assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='weight_records' AND column_name='body_fat_rate'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_collation<>'utf8mb4_unicode_ci'", Integer.class));
            long owner = jdbc.queryForObject("SELECT id FROM users WHERE username='admin'", Long.class);
            long cashType = jdbc.queryForObject("SELECT id FROM account_types WHERE code='cash'", Long.class);
            long category = jdbc.queryForObject("SELECT id FROM categories WHERE name='餐饮'", Long.class);
            jdbc.update("UPDATE users SET display_name='Custom name', role_name='user' WHERE id=?", owner);
            jdbc.update("UPDATE account_types SET name='Custom cash' WHERE id=?", cashType);
            jdbc.update("UPDATE categories SET icon='custom' WHERE id=?", category);
            for (String file : List.of("001_create_database_and_users", "002_create_account_types", "004_create_categories"))
                initializeFile(jdbc, file + ".mysql.sql", true);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
            assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM account_types", Integer.class));
            assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class));
            assertEquals("Custom name", jdbc.queryForObject("SELECT display_name FROM users WHERE id=?", String.class, owner));
            assertEquals("user", jdbc.queryForObject("SELECT role_name FROM users WHERE id=?", String.class, owner));
            assertEquals("Custom cash", jdbc.queryForObject("SELECT name FROM account_types WHERE id=?", String.class, cashType));
            assertEquals("custom", jdbc.queryForObject("SELECT icon FROM categories WHERE id=?", String.class, category));
            jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance) VALUES(1,?,?,'Cash',900)", owner, cashType);
            jdbc.update("INSERT INTO transactions(id,transaction_no,user_id,type,amount,account_id,category_id,title,occurred_at,reimbursement_amount) VALUES(1,'original',?,'expense',100,1,?,'Expense','2026-01-01',50)", owner, category);
            jdbc.update("INSERT INTO transactions(id,transaction_no,user_id,type,amount,account_id,category_id,title,occurred_at,original_transaction_id,recovery_kind) VALUES(2,'refund',?,'expense',-20,1,?,'Refund','2026-02-01',1,'refund')", owner, category);
            jdbc.update("INSERT INTO expense_recovery_followups(transaction_id,user_id,recipient,expected_date) VALUES(1,?,'Finance','2026-11-07')", owner);
            jdbc.update("INSERT INTO reminder_settings(user_id) VALUES(?)", owner);
            assertEquals("Finance", jdbc.queryForObject("SELECT recipient FROM expense_recovery_followups WHERE transaction_id=1", String.class));
            assertTrue(jdbc.queryForObject("SELECT budget_enabled AND reimbursement_enabled FROM reminder_settings WHERE user_id=?", Boolean.class, owner));
            assertEquals(0, new BigDecimal("80").compareTo(jdbc.queryForObject("SELECT SUM(amount) FROM transactions", BigDecimal.class)));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE transactions SET amount=-1 WHERE id=1"));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE transactions SET recovery_kind=NULL WHERE id=2"));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE transactions SET reimbursement_amount=101 WHERE id=1"));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("INSERT INTO transactions(transaction_no,user_id,type,amount,account_id,category_id,title,occurred_at) VALUES('invalid',?,'expense',-1,1,?,'Invalid','2026-01-01')", owner, category));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("INSERT INTO category_budgets(user_id,budget_month,category_id,amount) VALUES(?,'2026-02-01',?,-1)", owner, category));
            jdbc.update("INSERT INTO category_budgets(user_id,budget_month,category_id,amount) VALUES(?,'2026-01-01',?,200)", owner, category);
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE category_budgets SET amount=0"));
        } finally { admin.execute("DROP DATABASE " + schema); }
    }

    @Test void realMySqlConstraintsLockingIdempotencyAndRollback() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        String user = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        assertTrue(admin.queryForObject("SELECT VERSION()", String.class).startsWith("8."), "MySQL 8 is required");
        String schema = "life_hub_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        var executor = Executors.newFixedThreadPool(4);
        try {
            String base = url.split("\\?", 2)[0];
            String params = url.contains("?") ? url.substring(url.indexOf('?')) : "";
            String server = base.substring(0, base.indexOf('/', "jdbc:mysql://".length()));
            var source = new DriverManagerDataSource(server + "/" + schema + params, user, password);
            var jdbc = new JdbcTemplate(source);
            for (String file : List.of("001_create_database_and_users", "002_create_account_types", "003_create_accounts",
                "004_create_categories", "005_create_transactions", "007_create_investments", "043_create_accounting_requests", "045_create_reminder_preferences")) {
                initializeFile(jdbc, file + ".mysql.sql", false);
            }
            jdbc.update("INSERT INTO users(id,username,display_name,password_hash) VALUES(1,'test','Test','test')");
            jdbc.update("INSERT INTO account_types(id,code,name) VALUES(1,'cash','Cash')");
            jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance) VALUES(1,1,1,'A',1000),(2,1,1,'B',500)");
            var config = new MybatisConfiguration();
            for (var type : List.of(AccountMapper.class, AccountTypeMapper.class, TransactionMapper.class,
                CategoryMapper.class, ExpenseRecoveryMapper.class, AccountingRequestMapper.class, InvestmentPositionMapper.class, InvestmentProductMapper.class,
                InvestmentTransactionMapper.class, InvestmentDividendPlanMapper.class)) config.addMapper(type);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source); factory.setConfiguration(config);
            var session = new SqlSessionTemplate(factory.getObject());
            var service = new TransactionService(session.getMapper(TransactionMapper.class), session.getMapper(AccountMapper.class),
                session.getMapper(AccountTypeMapper.class), session.getMapper(CategoryMapper.class), null, null,
                new AccountingIdempotencyService(session.getMapper(AccountingRequestMapper.class), new ObjectMapper()));
            var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            var request = new TransactionRequest();
            request.setUserId(1L); request.setType("transfer"); request.setFromAccountId(1L); request.setToAccountId(2L);
            request.setAmount(new BigDecimal("125.50")); request.setOccurredAt(LocalDateTime.of(2026,11,6,12,0));
            request.setRequestId(UUID.randomUUID().toString());
            var start = new CountDownLatch(1);
            Callable<TransactionResponse> submit = () -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return tx.execute(s -> service.create(request)); };
            var a = executor.submit(submit); var b = executor.submit(submit); start.countDown();
            var first = a.get(15, TimeUnit.SECONDS);
            assertEquals(first.getId(), b.get(15, TimeUnit.SECONDS).getId());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_requests", Integer.class));
            assertEquals(0, new BigDecimal("874.50").compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=1", BigDecimal.class)));
            assertEquals(0, new BigDecimal("1500").compareTo(jdbc.queryForObject("SELECT SUM(current_balance) FROM accounts", BigDecimal.class)));

            request.setAmount(new BigDecimal("1"));
            assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> service.create(request)));
            request.setRequestId(UUID.randomUUID().toString());
            assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
                service.create(request); throw new IllegalStateException("simulate failure before commit");
            }));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_requests", Integer.class));
            assertNotNull(tx.execute(s -> service.create(request)));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));

            var constraint = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE transactions SET amount=-1 WHERE id=?", first.getId()));
            assertEquals(3819, ((java.sql.SQLException) constraint.getMostSpecificCause()).getErrorCode());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("UPDATE accounting_requests SET user_id=999"));
            // 不同 key 并发仍受同一用户行锁保护，余额不会丢更新。
            var jobs = new java.util.ArrayList<Future<?>>();
            for (int i=0; i<4; i++) jobs.add(executor.submit(() -> {
                var next = new TransactionRequest();
                org.springframework.beans.BeanUtils.copyProperties(request, next);
                next.setRequestId(UUID.randomUUID().toString());
                return tx.execute(s -> service.create(next));
            }));
            for (var job : jobs) job.get(15, TimeUnit.SECONDS);
            assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
            assertEquals(0, new BigDecimal("869.50").compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=1", BigDecimal.class)));
            jdbc.update("INSERT INTO account_types(id,code,name) VALUES(2,'investment','Investment')");
            jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,remark) VALUES(3,1,2,'Portfolio',100,'keep')");
            jdbc.update("INSERT INTO investment_products(id,product_type,symbol,name) VALUES(1,'stock','TEST','Test')");
            jdbc.update("INSERT INTO investment_positions(id,user_id,account_id,product_id,holding_quantity,available_quantity,cost_amount,current_price,market_value) VALUES(1,1,3,1,100,100,100,1,100)");
            var market = org.mockito.Mockito.mock(com.example.app.client.MarketQuoteClient.class);
            var quotes = org.mockito.Mockito.mock(InvestmentQuoteSyncService.class);
            org.mockito.Mockito.when(quotes.fetchFundBaseInfo(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                return new ObjectMapper().readTree("{\"Datas\":{\"SGZT\":\"开放申购\",\"DWJZ\":\"10\"}}");
            });
            var investment = new InvestmentService(session.getMapper(InvestmentProductMapper.class), session.getMapper(InvestmentPositionMapper.class),
                session.getMapper(InvestmentTransactionMapper.class), null, session.getMapper(InvestmentDividendPlanMapper.class), null, null, session.getMapper(AccountMapper.class),
                session.getMapper(AccountTypeMapper.class), null, org.mockito.Mockito.mock(GoldPriceService.class), new ObjectMapper(),
                new DataSourceTransactionManager(source), org.mockito.Mockito.mock(MarketCalendarService.class), 1, market,
                org.mockito.Mockito.mock(InvestmentDividendService.class), quotes,
                org.mockito.Mockito.mock(InvestmentPriceHistoryService.class), org.mockito.Mockito.mock(InvestmentFundProfitService.class),
                new InvestmentFundFeeService(market, session.getMapper(InvestmentTransactionMapper.class)), java.util.Set::of,
                new AccountingIdempotencyService(session.getMapper(AccountingRequestMapper.class), new ObjectMapper()));
            try {
                var buy = new com.example.app.dto.InvestmentTransactionRequest();
                buy.setUserId(1L); buy.setAccountId(3L); buy.setProductId(1L); buy.setPositionId(1L); buy.setFundingAccountId(1L);
                buy.setTradeType("buy"); buy.setQuantity(BigDecimal.TEN); buy.setPrice(BigDecimal.ONE); buy.setAmount(BigDecimal.TEN);
                buy.setTradeAt(LocalDateTime.of(2026,11,6,12,0)); buy.setRequestId(UUID.randomUUID().toString());
                var firstBuy = executor.submit(() -> investment.createTransaction(buy));
                var secondBuy = executor.submit(() -> investment.createTransaction(buy));
                assertEquals(firstBuy.get(15, TimeUnit.SECONDS).getId(), secondBuy.get(15, TimeUnit.SECONDS).getId());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM investment_transactions", Integer.class));
                assertEquals(0, new BigDecimal("110").compareTo(jdbc.queryForObject("SELECT holding_quantity FROM investment_positions WHERE id=1", BigDecimal.class)));
                assertEquals(0, new BigDecimal("110").compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=3", BigDecimal.class)));
                assertEquals("keep", jdbc.queryForObject("SELECT remark FROM accounts WHERE id=3", String.class));
                assertEquals(0, new BigDecimal("859.50").compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=1", BigDecimal.class)));
                for (String type : List.of("stock", "fund", "gold")) {
                    var opening = opening(type, "OPEN-" + type, null);
                    var aOpen = executor.submit(() -> investment.createPosition(opening));
                    var bOpen = executor.submit(() -> investment.createPosition(opening));
                    var opened = aOpen.get(15, TimeUnit.SECONDS);
                    assertEquals(opened.getId(), bOpen.get(15, TimeUnit.SECONDS).getId());
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM investment_positions WHERE product_id=?", Integer.class, opened.getProductId()));
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM investment_transactions WHERE position_id=?", Integer.class, opened.getId()));
                    // 老数据未写 market，与新请求空串/空格指向同一身份；复用不覆盖名称。
                    var productRequest = opening.getProduct();
                    productRequest.setMarket("  "); productRequest.setName("不应覆盖");
                    assertEquals(opened.getProductId(), investment.createProduct(productRequest).getId());
                    assertEquals("新产品", jdbc.queryForObject("SELECT name FROM investment_products WHERE id=?", String.class, opened.getProductId()));
                }
                assertEquals(0, new BigDecimal("559.50").compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=1", BigDecimal.class)));

                // 两个用户先建立相同 RR 快照，再同时首次建仓；用户行锁无法替代产品唯一约束。
                jdbc.update("INSERT INTO users(id,username,display_name,password_hash) VALUES(2,'second','Second','test')");
                jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance) VALUES(4,2,1,'Cash',1000),(5,2,2,'Portfolio',0)");
                var left = opening("stock", " race-stock ", " cn ");
                var right = opening("stock", "RACE-STOCK", "CN");
                right.setUserId(2L); right.setAccountId(5L); right.setFundingAccountId(4L);
                var barrier = new CyclicBarrier(2);
                java.util.function.Function<com.example.app.dto.InvestmentPositionRequest, com.example.app.dto.InvestmentPositionResponse> concurrentOpen = payload -> tx.execute(status -> {
                    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM investment_products WHERE symbol_key='RACE-STOCK'", Integer.class));
                    try { barrier.await(5, TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException(e); }
                    return investment.createPosition(payload);
                });
                var leftResult = executor.submit(() -> concurrentOpen.apply(left));
                var rightResult = executor.submit(() -> concurrentOpen.apply(right));
                assertEquals(leftResult.get(15, TimeUnit.SECONDS).getProductId(), rightResult.get(15, TimeUnit.SECONDS).getProductId());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM investment_products WHERE symbol_key='RACE-STOCK' AND market_key='CN'", Integer.class));
                assertEquals(0, new BigDecimal("459.50").compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=1", BigDecimal.class)));
                assertEquals(0, new BigDecimal("900").compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=4", BigDecimal.class)));
                var hk = opening("stock", "RACE-STOCK", "HK");
                assertNotEquals(leftResult.get().getProductId(), investment.createProduct(hk.getProduct()).getId());

                var failed = opening("stock", "ROLLBACK-PRODUCT", null);
                failed.setCostAmount(new BigDecimal("10000"));
                assertThrows(IllegalArgumentException.class, () -> investment.createPosition(failed));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM investment_products WHERE symbol_key='ROLLBACK-PRODUCT'", Integer.class));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_requests WHERE request_id=?", Integer.class, failed.getRequestId()));

            } finally { investment.shutdownExecutor(); }
            jdbc.update("INSERT INTO categories(id,name,type,icon) VALUES(1,'Expense','expense','other')");
            var expense = new TransactionRequest();
            expense.setUserId(1L); expense.setType("expense"); expense.setAccountId(1L); expense.setCategoryId(1L);
            expense.setAmount(new BigDecimal("100")); expense.setCurrencyCode("CNY");
            expense.setOccurredAt(LocalDateTime.of(2026,1,1,12,0));
            var original = tx.execute(s -> service.create(expense));
            var recoveryService = new ExpenseRecoveryService(session.getMapper(TransactionMapper.class), session.getMapper(ExpenseRecoveryMapper.class),
                session.getMapper(AccountMapper.class), session.getMapper(AccountTypeMapper.class),
                new AccountingIdempotencyService(session.getMapper(AccountingRequestMapper.class), new ObjectMapper()));
            var receiptRequest = new com.example.app.dto.ExpenseRecoveryRequest(original.getId(),2L,new BigDecimal("100"),"refund",
                LocalDateTime.of(2026,2,1,12,0),null,UUID.randomUUID().toString());
            var balanceBefore = jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=2", BigDecimal.class);
            var received = tx.execute(s -> recoveryService.receive(1L,receiptRequest));
            assertEquals(received.id(),tx.execute(s -> recoveryService.receive(1L,receiptRequest)).id());
            assertEquals(0,balanceBefore.add(new BigDecimal("100")).compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=2",BigDecimal.class)));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE transactions SET recovery_kind=NULL WHERE id=?", received.id()));
            assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> service.delete(original.getId(),1L)));
            assertEquals(Boolean.TRUE,tx.execute(s -> service.delete(received.id(),1L)));
            assertEquals(0,balanceBefore.compareTo(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=2",BigDecimal.class)));


        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            admin.execute("DROP DATABASE " + schema);
        }
    }
    private static com.example.app.dto.InvestmentPositionRequest opening(String type, String symbol, String market) {
        var request = new com.example.app.dto.InvestmentPositionRequest();
        request.setUserId(1L); request.setAccountId(3L); request.setFundingAccountId(1L);
        request.setRequestId(UUID.randomUUID().toString()); request.setHoldingQuantity(BigDecimal.TEN);
        request.setAvailableQuantity(BigDecimal.TEN); request.setCostAmount(new BigDecimal("100"));
        request.setCurrentPrice(BigDecimal.TEN); request.setTradeAt(LocalDateTime.of(2026,11,6,10,0));
        var product = new com.example.app.dto.InvestmentProductRequest();
        product.setProductType(type); product.setSymbol(symbol); product.setName("新产品"); product.setMarket(market);
        request.setProduct(product);
        return request;
    }

    private static DriverManagerDataSource testDataSource(String url, String schema, String user, String password) {
        String base = url.split("\\?", 2)[0];
        String params = url.contains("?") ? url.substring(url.indexOf('?')) : "";
        String server = base.substring(0, base.indexOf('/', "jdbc:mysql://".length()));
        return new DriverManagerDataSource(server + "/" + schema + params, user, password);
    }

    /** 执行原始初始化语句（含 DELIMITER）；永不切换到业务库，只按需装入基础数据。 */
    private static void initializeFile(JdbcTemplate jdbc, String file, boolean includeSeeds) throws Exception {
        String delimiter = ";";
        var statement = new StringBuilder();
        for (String line : Files.readAllLines(Path.of("../../database", file))) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
            if (trimmed.startsWith("DELIMITER ")) { delimiter = trimmed.substring(10).trim(); continue; }
            statement.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String sql = statement.toString().trim();
                if (!sql.startsWith("CREATE DATABASE") && !sql.startsWith("USE ")
                    && (includeSeeds || !sql.startsWith("INSERT ")))
                    jdbc.execute(sql.substring(0, sql.length() - delimiter.length()));
                statement.setLength(0);
            }
        }
        assertEquals(0, statement.length(), file);
    }

}
