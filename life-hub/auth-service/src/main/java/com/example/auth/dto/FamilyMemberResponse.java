package com.example.auth.dto;

public record FamilyMemberResponse(
    Long userId,
    String displayName,
    String role,
    String status,
    boolean canUnbind
) {
}
