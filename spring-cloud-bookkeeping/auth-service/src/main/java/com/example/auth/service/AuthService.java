package com.example.auth.service;

import com.example.auth.model.UserAccount;
import com.example.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public AuthService(PasswordEncoder passwordEncoder, UserRepository userRepository) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    public Optional<UserAccount> authenticate(String login, String password) {
        return userRepository.findActiveByLogin(login)
            .filter(user -> user.passwordHash() != null)
            .filter(user -> passwordEncoder.matches(password, user.passwordHash()));
    }

    public void markLoginSuccess(Long userId) {
        userRepository.updateLastLoginAt(userId);
    }
}
