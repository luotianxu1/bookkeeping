package com.example.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReminderActionRequest(
    @NotNull @Size(min = 1, max = 200) List<@NotBlank @Size(max = 120) String> keys,
    @NotNull Action action,
    @Min(1) @Max(43200) Integer snoozeMinutes
) {
    public enum Action { read, unread, snooze, resume }
}
