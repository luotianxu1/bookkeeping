package com.example.auth.config;

import com.example.common.security.FamilyScopeChecker;
import com.example.common.security.JdbcFamilyScopeChecker;
import com.example.common.security.JdbcUserStatusChecker;
import com.example.common.security.UserStatusChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 安全校验 Bean 装配：auth-service 持有业务库连接，
 * 用户状态与家庭范围判定直接走 JDBC 实现（common 里为纯接口），
 * 并通过 /api/internal/auth/** 提供给无库服务（market-service）远程查询。
 */
@Configuration
public class SecurityCheckerConfig {

    @Bean
    public UserStatusChecker userStatusChecker(JdbcTemplate jdbcTemplate) {
        return new JdbcUserStatusChecker(jdbcTemplate);
    }

    @Bean
    public FamilyScopeChecker familyScopeChecker(JdbcTemplate jdbcTemplate) {
        return new JdbcFamilyScopeChecker(jdbcTemplate);
    }
}
