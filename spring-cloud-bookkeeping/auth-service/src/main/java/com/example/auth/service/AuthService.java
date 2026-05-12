package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.auth.entity.UserEntity;
import com.example.auth.mapper.UserMapper;
import com.example.auth.model.UserAccount;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    private static final String ACTIVE_STATUS = "active";

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AuthService(PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    public Optional<UserAccount> authenticate(String login, String password) {
        return findActiveByLogin(login)
            .filter(user -> user.passwordHash() != null)
            .filter(user -> passwordEncoder.matches(password, user.passwordHash()));
    }

    public Optional<UserAccount> getCurrentUser(String username) {
        return findActiveByLogin(username);
    }

    public void markLoginSuccess(Long userId) {
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .set(UserEntity::getLastLoginAt, LocalDateTime.now());
        userMapper.update(null, wrapper);
    }

    private Optional<UserAccount> findActiveByLogin(String login) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getStatus, ACTIVE_STATUS)
            .and(query -> query
                .eq(UserEntity::getUsername, login)
                .or()
                .eq(UserEntity::getPhone, login)
                .or()
                .eq(UserEntity::getEmail, login)
            )
            .last("LIMIT 1");

        return Optional.ofNullable(userMapper.selectOne(wrapper)).map(this::toUserAccount);
    }

    private UserAccount toUserAccount(UserEntity entity) {
        return new UserAccount(
            entity.getId(),
            entity.getUsername(),
            entity.getPhone(),
            entity.getEmail(),
            entity.getPasswordHash(),
            entity.getDisplayName(),
            entity.getAvatarUrl(),
            entity.getStatus(),
            entity.getRoleName(),
            entity.getLastLoginAt()
        );
    }
}
