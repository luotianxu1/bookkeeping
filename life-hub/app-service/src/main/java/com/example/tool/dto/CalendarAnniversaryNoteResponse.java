package com.example.tool.dto;

import lombok.Data;

@Data
public class CalendarAnniversaryNoteResponse {

    private Long id;
    private String title;
    private String occurrenceDate;
    private String remark;
    private String statusLabel;
    private long daysOffset;
}
