package com.example.auth.dto;

import java.util.List;

public record FamilyOverviewResponse(
    boolean hasFamily,
    String inviteCode,
    int memberCount,
    List<FamilyMemberResponse> members
) {
}
