package com.example.livetest;

import com.example.auth.AuthServiceApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/** Optional browser fixture: production auth/RBAC controllers, services and security on isolated H2. Never deploy. */
public final class PcTestServer {
    private static volatile boolean ready;
    public static void main(String[] args) throws Exception {
        try (var context = new SpringApplicationBuilder(AuthWeb.class).run(
            "--spring.config.location=optional:classpath:/pc-test.properties",
            "--server.address=127.0.0.1", "--server.port=18883",
            "--spring.datasource.url=jdbc:h2:mem:pc_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=0;LOCK_TIMEOUT=10000",
            "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.datasource.driver-class-name=org.h2.Driver",
            "--app.jwt.secret=pc-browser-test-only-secret-at-least-32-bytes", "--app.jwt.expiration-seconds=3600",
            "--auth.cookie.secure=false", "--internal.api-key=pc-browser-internal-only", "--spring.profiles.active=live-e2e",
            "--springdoc.api-docs.enabled=false", "--spring.main.banner-mode=off", "--logging.level.root=WARN"
        )) {
            var data = context.getBean(DataSource.class);
            String users = Files.readString(Path.of("../database/001_create_database_and_users.mysql.sql"));
            execute(data, users.substring(users.indexOf("CREATE TABLE"), users.indexOf("-- 首次初始化")));
            for (String file : new String[]{"050_create_account_security.mysql.sql", "051_create_admin_roles.mysql.sql"}) {
                execute(data, Files.readString(Path.of("../database", file)));
            }
            var jdbc = context.getBean(JdbcTemplate.class);
            String hash = context.getBean(PasswordEncoder.class).encode("pc-test-password");
            jdbc.update("INSERT INTO users(username,password_hash,display_name,role_name,email) VALUES('pc-admin',?,'测试管理员','super_admin','admin@example.test'),('pc-user',?,'普通成员','user','member@example.test')", hash, hash);
            ready = true;
            System.out.println("PC_E2E_READY: auth=18883 (isolated H2, production RBAC)");
            new CountDownLatch(1).await();
        }
    }
    private static void execute(DataSource data, String sql) throws Exception {
        String ddl = sql.replaceAll("(?i)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        try (var connection = data.getConnection()) { ScriptUtils.executeSqlScript(connection, new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))); }
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.example.auth.mapper")
    @ComponentScan(basePackages = {"com.example.auth", "com.example.common"}, excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuthServiceApplication.class))
    @Import(Readiness.class)
    static class AuthWeb {}

    @RestController
    static class Readiness {
        @GetMapping("/actuator/health")
        ResponseEntity<String> health() { return ResponseEntity.status(ready ? 200 : 503).body(ready ? "ready" : "starting"); }
    }
    private PcTestServer() {}
}
