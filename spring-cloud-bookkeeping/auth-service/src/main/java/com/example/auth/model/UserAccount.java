package com.example.auth.model;

import java.time.LocalDateTime;

public record UserAccount(
    Long id,
    String username,
    String phone,
    String email,
    String passwordHash,
    String displayName,
    String avatarUrl,
    String status,
    String roleName,
    Long familyId,
    LocalDateTime lastLoginAt
) {
}
