package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinFamilyRequest(
    @NotBlank(message = "家庭邀请码不能为空")
    String inviteCode
) {
}
