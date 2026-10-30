package com.example.auth.dto;

public record CurrentUserResponse(
    Long id,
    String username,
    String phone,
    String email,
    String displayName,
    String avatarUrl,
    String roleName
) {
}
