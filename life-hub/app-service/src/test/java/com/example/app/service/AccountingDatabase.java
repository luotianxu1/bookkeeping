package com.example.app.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.example.app.entity.*;
import com.example.app.mapper.*;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 每个测试独立内存库，使用生产 Mapper/SQL 和真实数据库事务，不接触开发数据库。 */
final class AccountingDatabase implements AutoCloseable {
    final JdbcTemplate jdbc;
    final DataSourceTransactionManager manager;
    final TransactionTemplate tx;
    final SqlSessionTemplate session;
    AccountingDatabase() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        tx = new TransactionTemplate(manager);
        // 与 MySQL 默认隔离级别一致，防止只在 READ_COMMITTED 下通过。
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var configuration = new MybatisConfiguration();
        for (Class<?> mapper : List.of(AccountMapper.class, AccountTypeMapper.class, TransactionMapper.class, AccountingRequestMapper.class,
            CategoryMapper.class, DebtRecordMapper.class, HumanRelationRecordMapper.class, RenewalSubscriptionMapper.class,
            InvestmentProductMapper.class, InvestmentPositionMapper.class, InvestmentTransactionMapper.class,
            InvestmentAutoInvestPlanMapper.class, InvestmentDividendPlanMapper.class, InvestmentDividendRecordMapper.class,
            InvestmentFixedExpenseMapper.class, InvestmentPriceQuoteMapper.class)) configuration.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/TransactionMapper.xml"));
        session = new SqlSessionTemplate(factory.getObject());
        jdbc.execute("CREATE TABLE accounting_requests(user_id BIGINT, operation VARCHAR(40), request_id VARCHAR(64), request_hash VARCHAR(64), response_json CLOB, PRIMARY KEY(user_id,operation,request_id))");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, status VARCHAR(32))");
        jdbc.update("INSERT INTO users VALUES(1, 'active'), (2, 'active')");
        for (Class<?> entity : List.of(AccountEntity.class, AccountTypeEntity.class, TransactionEntity.class,
            CategoryEntity.class, DebtRecordEntity.class, HumanRelationRecordEntity.class, RenewalSubscriptionEntity.class,
            InvestmentProductEntity.class, InvestmentPositionEntity.class, InvestmentTransactionEntity.class,
            InvestmentAutoInvestPlanEntity.class, InvestmentDividendPlanEntity.class, InvestmentDividendRecordEntity.class,
            InvestmentFixedExpenseEntity.class, InvestmentPriceQuoteEntity.class)) {
            var table = TableInfoHelper.getTableInfo(entity);
            String fields = table.getFieldList().stream().map(field -> field.getColumn() + " " + sqlType(field.getPropertyType()))
                .collect(java.util.stream.Collectors.joining(", "));
            jdbc.execute("CREATE TABLE " + table.getTableName() + " (" + table.getKeyColumn()
                + " BIGINT AUTO_INCREMENT PRIMARY KEY, " + fields + ")");
        }
        jdbc.execute("ALTER TABLE investment_products ADD symbol_key VARCHAR(64) GENERATED ALWAYS AS (UPPER(TRIM(symbol)))");
        jdbc.execute("ALTER TABLE investment_products ADD market_key VARCHAR(32) GENERATED ALWAYS AS (UPPER(TRIM(COALESCE(market,''))))");
        jdbc.execute("CREATE UNIQUE INDEX uk_investment_products_identity ON investment_products(product_type,symbol_key,market_key)");
    }
    private static String sqlType(Class<?> type) {
        if (type == BigDecimal.class) return "DECIMAL(24,6)";
        if (type == LocalDateTime.class) return "TIMESTAMP";
        if (type == LocalDate.class) return "DATE";
        if (type == Boolean.class || type == boolean.class) return "BOOLEAN";
        if (Number.class.isAssignableFrom(type)) return "BIGINT";
        return "VARCHAR(2048)";
    }
    <T> T mapper(Class<T> type) { return session.getMapper(type); }
    TransactionService transactions() {
        return new TransactionService(mapper(TransactionMapper.class), mapper(AccountMapper.class),
            mapper(AccountTypeMapper.class), mapper(CategoryMapper.class), mapper(DebtRecordMapper.class),
            mapper(HumanRelationRecordMapper.class), idempotency());
    }
    AccountingIdempotencyService idempotency() {
        return new AccountingIdempotencyService(mapper(AccountingRequestMapper.class), new com.fasterxml.jackson.databind.ObjectMapper());
    }
    void seedCashAccounts() {
        jdbc.update("INSERT INTO account_types(id, code) VALUES (1, 'cash'), (2, 'investment')");
        jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,status,currency_code) VALUES (1,1,1,'现金1',1000,'active','CNY'),(2,1,1,'现金2',500,'active','CNY'),(3,1,2,'投资',0,'active','CNY')");
    }
    BigDecimal balance(long id) { return jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id=?", BigDecimal.class, id); }
    @Override public void close() { jdbc.execute("DROP ALL OBJECTS"); }
}
