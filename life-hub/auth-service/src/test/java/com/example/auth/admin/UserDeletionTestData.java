package com.example.auth.admin;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Production table definitions, retaining foreign keys and checks, with only H2 syntax adaptations. */
final class UserDeletionTestData {
    private final JdbcTemplate jdbc;

    UserDeletionTestData(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void createSchema() throws Exception {
        try (var files = Files.list(Path.of("../../database"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".sql")).sorted().toList()) {
                var tables = Pattern.compile("(?ms)^CREATE TABLE IF NOT EXISTS \\w+ \\(.*?\\) ENGINE=.*?;")
                    .matcher(Files.readString(file));
                while (tables.find()) {
                    String ddl = tables.group().replaceAll("ENGINE=.*?;", ";")
                        .replaceAll("(?i)\\s+CHARACTER SET \\w+", "")
                        .replaceAll("(?i)\\s+COLLATE \\w+", "")
                        .replace(" STORED", "")
                        .replace("IF(status = 'active', 1, NULL)", "CASE WHEN status = 'active' THEN 1 ELSE NULL END");
                    jdbc.execute(ddl);
                }
            }
        }
    }

    void insertSharedData() {
        jdbc.update("INSERT INTO account_types(id,code,name) VALUES(1,'cash','现金')");
        jdbc.update("INSERT INTO categories(id,name,type,icon,is_system) VALUES(999,'公共支出','expense','food',1)");
        jdbc.update("INSERT INTO investment_products(id,product_type,symbol,name) VALUES(1,'fund','TEST','共享基金')");
        jdbc.update("INSERT INTO investment_price_quotes(product_id,quote_date,latest_price) VALUES(1,CURRENT_DATE,1)");
        jdbc.update("INSERT INTO investment_dividend_plans(id,product_id,dividend_year,dividend_per_unit) VALUES(1,1,2026,1)");
    }

    void insertPersonalData(long userId) throws Exception {
        String sql = Files.readString(Path.of("src/test/resources/admin-user-data.sql"))
            .replace("${id}", Long.toString(userId)).replace("${child}", Long.toString(userId + 100));
        for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.update(statement);
    }

    Map<String, List<Map<String, Object>>> snapshotExcludingUser(long userId) {
        var snapshot = new TreeMap<String, List<Map<String, Object>>>();
        var tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name", String.class);
        for (String table : tables) {
            var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?", String.class, table);
            String where = table.equals("users") ? " WHERE id <> " + userId
                : columns.contains("user_id") ? " WHERE user_id <> " + userId + " OR user_id IS NULL" : "";
            // Every table has an ID or an ownership key in its first column; seeded rows are unique there.
            snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + where + " ORDER BY 1"));
        }
        return snapshot;
    }
}
