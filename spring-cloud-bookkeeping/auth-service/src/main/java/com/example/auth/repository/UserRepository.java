package com.example.auth.repository;

import com.example.auth.model.UserAccount;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findActiveByLogin(String login) {
        String sql = """
            SELECT
              id,
              username,
              phone,
              email,
              password_hash,
              display_name,
              avatar_url,
              status,
              role_name,
              last_login_at
            FROM users
            WHERE status = 'active'
              AND (username = ? OR phone = ? OR email = ?)
            LIMIT 1
            """;

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapUser, login, login, login));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public void updateLastLoginAt(Long userId) {
        jdbcTemplate.update("UPDATE users SET last_login_at = NOW(3) WHERE id = ?", userId);
    }

    private UserAccount mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserAccount(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getString("avatar_url"),
            rs.getString("status"),
            rs.getString("role_name"),
            rs.getTimestamp("last_login_at") == null ? null : rs.getTimestamp("last_login_at").toLocalDateTime()
        );
    }
}
