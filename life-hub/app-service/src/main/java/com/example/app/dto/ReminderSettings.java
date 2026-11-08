package com.example.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReminderSettings(
    @Min(0) @Max(30) int advanceDays,
    boolean todoEnabled,
    boolean anniversaryEnabled,
    boolean renewalEnabled,
    boolean liabilityEnabled,
    boolean investmentEnabled,
    boolean budgetEnabled,
    boolean reimbursementEnabled,
    Boolean termDepositEnabled
) {
    public ReminderSettings {
        if (termDepositEnabled == null) termDepositEnabled = true;
    }
    public static ReminderSettings defaults() {
        return new ReminderSettings(3, true, true, true, true, true, true, true, true);
    }
}
