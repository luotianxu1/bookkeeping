package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountSecurityRequests {
    private AccountSecurityRequests() {}
    public record Profile(@NotBlank @Size(max = 80) String displayName) {}
    public record Password(@NotBlank @Size(max = 64) String currentPassword,
                           @NotBlank @Size(min = 6, max = 64) String newPassword) {}
    public record RecoveryCode(@NotBlank @Size(max = 64) String currentPassword) {}
    public record Reset(@NotBlank @Size(max = 64) String username,
                        @NotBlank @Size(max = 100) String recoveryCode,
                        @NotBlank @Size(min = 6, max = 64) String newPassword) {}
}
