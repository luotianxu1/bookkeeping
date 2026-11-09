package com.example.app.config;

import com.example.common.security.FamilyScopeChecker;
import com.example.common.security.JdbcFamilyScopeChecker;
import com.example.common.security.JdbcUserStatusChecker;
import com.example.common.security.UserStatusChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 安全校验 Bean 装配：app-service 持有业务库连接，
 * 用户状态与家庭范围判定直接走 JDBC 实现（common 里为纯接口）。
 */
@Configuration
public class SecurityCheckerConfig {

    @Bean
    public com.example.common.security.SessionChecker sessionChecker(JdbcTemplate jdbcTemplate) {
        return new com.example.common.security.JdbcSessionChecker(jdbcTemplate);
    }

    @Bean
    public UserStatusChecker userStatusChecker(JdbcTemplate jdbcTemplate) {
        return new JdbcUserStatusChecker(jdbcTemplate);
    }

    @Bean
    public FamilyScopeChecker familyScopeChecker(JdbcTemplate jdbcTemplate) {
        return new JdbcFamilyScopeChecker(jdbcTemplate);
    }
}
