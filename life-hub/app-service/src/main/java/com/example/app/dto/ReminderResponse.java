package com.example.app.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReminderResponse(List<Item> items, long unreadCount, long snoozedCount) {
    public record Item(
        String key, String type, String title, String description,
        OffsetDateTime dueAt, boolean allDay, String urgency, String targetPath,
        boolean read, OffsetDateTime snoozedUntil
    ) { }
}
