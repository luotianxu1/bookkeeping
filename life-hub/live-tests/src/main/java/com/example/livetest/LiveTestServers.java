package com.example.livetest;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.app.AppServiceApplication;
import com.example.auth.AuthServiceApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * 仅由 live-e2e profile 构建。运行生产 Controller/Service/Mapper 和完整安全链，
 * 使用全新 H2 内存库，固定绑定回环地址；不加载业务配置、调度任务或测试用认证后门。
 */
public final class LiveTestServers {
    private static volatile boolean ready;

    public static void main(String[] args) throws Exception {
        String database = "jdbc:h2:mem:live_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=0;LOCK_TIMEOUT=5000";
        try (var auth = new SpringApplicationBuilder(AuthWeb.class).run(properties(database, 18881));
             var app = new SpringApplicationBuilder(FinanceWeb.class).run(properties(database, 18882))) {
            var jdbc = app.getBean(JdbcTemplate.class);
            initializeDatabase(jdbc, auth.getBean(PasswordEncoder.class));
            ready = true;
            System.out.println("LIVE_E2E_READY: auth=18881 finance=18882 (isolated H2)");
            new CountDownLatch(1).await();
        }
    }

    private static String[] properties(String database, int port) {
        // 命令行属性覆盖本机环境，绝不回落到 application.yml 中的业务数据库。
        return new String[]{
            "--spring.config.location=optional:classpath:/live-test.properties",
            "--server.address=127.0.0.1", "--server.port=" + port,
            "--spring.datasource.url=" + database, "--spring.datasource.username=sa",
            "--spring.datasource.password=", "--spring.datasource.driver-class-name=org.h2.Driver",
            "--app.jwt.secret=live-e2e-test-only-jwt-secret-at-least-32-bytes",
            "--app.jwt.expiration-seconds=3600", "--auth.cookie.secure=false",
            "--internal.api-key=live-e2e-internal-only", "--spring.profiles.active=live-e2e",
            "--market.center.base-url=http://127.0.0.1:18882",
            "--mybatis-plus.mapper-locations=classpath*:/mapper/*.xml",
            "--springdoc.api-docs.enabled=false", "--spring.main.banner-mode=off",
            "--logging.level.root=WARN"
        };
    }

    private static void initializeDatabase(JdbcTemplate jdbc, PasswordEncoder encoder) {
        // 浏览器测试验证 HTTP/鉴权/事务；MySQL 特有约束由独立 MySQL 集成测试覆盖。
        // 从已装配的生产 Mapper 元数据创建字段，避免复制所有领域的测试表结构。
        for (var table : TableInfoHelper.getTableInfos()) {
            String fields = table.getFieldList().stream()
                .map(field -> field.getColumn() + " " + sqlType(field.getPropertyType()))
                .collect(java.util.stream.Collectors.joining(", "));
            String key = table.getKeyColumn() + " " + sqlType(table.getKeyType())
                + (table.getIdType() == IdType.AUTO ? " AUTO_INCREMENT" : "") + " PRIMARY KEY";
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table.getTableName() + " (" + key + ", " + fields + ")");
        }
        jdbc.execute("CREATE TABLE accounting_requests(user_id BIGINT, operation VARCHAR(40), request_id VARCHAR(64), request_hash VARCHAR(64), response_json CLOB, PRIMARY KEY(user_id,operation,request_id))");
        jdbc.execute("CREATE TABLE reminder_settings(user_id BIGINT PRIMARY KEY, advance_days INT DEFAULT 3, todo_enabled BOOLEAN DEFAULT TRUE, anniversary_enabled BOOLEAN DEFAULT TRUE, renewal_enabled BOOLEAN DEFAULT TRUE, liability_enabled BOOLEAN DEFAULT TRUE, investment_enabled BOOLEAN DEFAULT TRUE, budget_enabled BOOLEAN DEFAULT TRUE, reimbursement_enabled BOOLEAN DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE reminder_states(user_id BIGINT, reminder_key VARCHAR(120), read_at TIMESTAMP, snoozed_until TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id,reminder_key))");
        jdbc.execute("CREATE TABLE expense_recovery_followups(transaction_id BIGINT PRIMARY KEY,user_id BIGINT,recipient VARCHAR(100),expected_date DATE)");
        jdbc.execute("CREATE UNIQUE INDEX uk_category_budget_month ON category_budgets(user_id,budget_month,category_id)");
        jdbc.execute("ALTER TABLE investment_products ADD symbol_key VARCHAR(64) GENERATED ALWAYS AS (UPPER(TRIM(symbol)))");
        jdbc.execute("ALTER TABLE investment_products ADD market_key VARCHAR(32) GENERATED ALWAYS AS (UPPER(TRIM(COALESCE(market,''))))");
        jdbc.execute("CREATE UNIQUE INDEX uk_investment_products_identity ON investment_products(product_type,symbol_key,market_key)");
        String password = encoder.encode("live-test-password");
        for (long id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO users(id,username,password_hash,display_name,status,role_name,family_id) VALUES(?,?,?,?, 'active','user',?)",
                id, "live-user-" + id, password, "联通测试用户" + id, id <= 2 ? 1L : null);
        }
        jdbc.update("INSERT INTO families(id,owner_user_id,invite_code,family_name,status) VALUES(1,1,'FAMILY-LIVETEST','测试家庭','active')");
        jdbc.update("INSERT INTO account_types(id,code,name,category,balance_direction,include_in_net_worth_default,allow_overdraft,is_system,sort_order,status) VALUES(1,'cash','现金','asset','positive',TRUE,FALSE,TRUE,0,'active')");
        jdbc.update("INSERT INTO accounts(id,user_id,account_type_id,name,current_balance,currency_code,status,include_in_net_worth,sort_order) VALUES(1,1,1,'测试现金',1000,'CNY','active',TRUE,0),(2,1,1,'测试储蓄',500,'CNY','active',TRUE,1),(3,2,1,'家庭成员账户',200,'CNY','active',TRUE,0)");
        jdbc.update("INSERT INTO categories(id,name,type,icon,level,is_system,sort_order,status) VALUES(1,'测试餐饮','expense','餐饮',1,TRUE,0,'active'),(2,'测试工资','income','工资',1,TRUE,0,'active')");
    }

    private static String sqlType(Class<?> type) {
        if (type == BigDecimal.class) return "DECIMAL(24,6)";
        if (type == LocalDateTime.class) return "TIMESTAMP";
        if (type == LocalDate.class) return "DATE";
        if (type == Boolean.class || type == boolean.class) return "BOOLEAN";
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) return "BIGINT";
        return "VARCHAR(4096)";
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.example.auth.mapper")
    @ComponentScan(basePackages = {"com.example.auth", "com.example.common"}, excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuthServiceApplication.class))
    @Import(Readiness.class)
    static class AuthWeb {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({"com.example.app.mapper", "com.example.tool.mapper", "com.example.food.mapper"})
    @ComponentScan(basePackages = {"com.example.app", "com.example.tool", "com.example.food", "com.example.common"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppServiceApplication.class))
    static class FinanceWeb {}

    @RestController
    static class Readiness {
        @GetMapping("/actuator/health")
        ResponseEntity<String> health() {
            return ResponseEntity.status(ready ? 200 : 503).body(ready ? "ready" : "starting");
        }
    }
}
