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

/** 仅连接显式指定的测试实例。每次新建随机 schema，绝不执行脚本中的 USE/种子数据。 */
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql://.+")
class MySqlAccountingTest {
    /** 单独运行本用例可检查 MySQL 5.7 的迁移语法，不代表整套账务支持 5.7。 */
    @Test void productIdentityMigrationRejectsDuplicatesAndCanBeRetried() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        String user = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String schema = "life_hub_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        try {
            String base = url.split("\\?", 2)[0];
            String params = url.contains("?") ? url.substring(url.indexOf('?')) : "";
            String server = base.substring(0, base.indexOf('/', "jdbc:mysql://".length()));
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(server + "/" + schema + params, user, password));
            String script = Files.readString(Path.of("../../database/007_create_investments.mysql.sql"));
            var table = Pattern.compile("(?ms)^CREATE TABLE IF NOT EXISTS investment_products \\(.*?\\) ENGINE=.*?;").matcher(script);
            assertTrue(table.find());
            jdbc.execute(table.group());
            jdbc.update("INSERT INTO investment_products(id,product_type,symbol,name,market) VALUES(101,'gold','DUP','A',NULL),(102,'gold',' dup ','B',' ')");
            var migrationFailure = assertThrows(org.springframework.dao.DataAccessException.class, () -> migrateProducts(jdbc));
            assertEquals(1644, ((java.sql.SQLException) migrationFailure.getMostSpecificCause()).getErrorCode());
            assertEquals(List.of(101L, 102L), jdbc.queryForList("SELECT id FROM investment_products ORDER BY id", Long.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='investment_products' AND column_name='market_key'", Integer.class));
            // 只清除本测试的冲突数据，验证失败后重跑以及成功后重跑。
            jdbc.update("DELETE FROM investment_products WHERE id=102");
            migrateProducts(jdbc);
            migrateProducts(jdbc);
            assertEquals("", jdbc.queryForObject("SELECT market_key FROM investment_products WHERE id=101", String.class));
            assertNull(jdbc.queryForObject("SELECT market FROM investment_products WHERE id=101", String.class));
            var duplicate = assertThrows(org.springframework.dao.DataAccessException.class, () ->
                jdbc.update("INSERT INTO investment_products(product_type,symbol,name,market) VALUES('gold',' dup ','C',' ')"));
            assertEquals(1062, ((java.sql.SQLException) duplicate.getMostSpecificCause()).getErrorCode());
            jdbc.update("INSERT INTO investment_products(product_type,symbol,name,market) VALUES('gold','DUP','HK product','HK')");
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM investment_products", Integer.class));
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
                "004_create_categories", "005_create_transactions", "007_create_investments", "043_create_accounting_requests")) {
                String script = Files.readString(Path.of("../../database", file + ".mysql.sql"));
                var tables = Pattern.compile("(?ms)^CREATE TABLE.*?\\) ENGINE=.*?;").matcher(script);
                assertTrue(tables.find(), "DDL missing in " + file);
                do { jdbc.execute(tables.group()); } while (tables.find());
            }
            migrateProducts(jdbc);
            jdbc.update("INSERT INTO users(id,username,display_name,password_hash) VALUES(1,'test','Test','test')");
            jdbc.update("INSERT INTO account_types(id,code,name) VALUES(1,'cash','Cash')");
            jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance) VALUES(1,1,1,'A',1000),(2,1,1,'B',500)");
            var config = new MybatisConfiguration();
            for (var type : List.of(AccountMapper.class, AccountTypeMapper.class, TransactionMapper.class,
                CategoryMapper.class, AccountingRequestMapper.class, InvestmentPositionMapper.class, InvestmentProductMapper.class,
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

    /** 执行原始迁移（含 MySQL DELIMITER），不另造测试用的替代 DDL。 */
    private static void migrateProducts(JdbcTemplate jdbc) throws Exception {
        String delimiter = ";";
        var statement = new StringBuilder();
        for (String line : Files.readAllLines(Path.of("../../database/044_unique_investment_product_identity.mysql.sql"))) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
            if (trimmed.startsWith("DELIMITER ")) { delimiter = trimmed.substring(10).trim(); continue; }
            statement.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String sql = statement.toString().trim();
                jdbc.execute(sql.substring(0, sql.length() - delimiter.length()));
                statement.setLength(0);
            }
        }
        assertEquals(0, statement.length());
    }

}
