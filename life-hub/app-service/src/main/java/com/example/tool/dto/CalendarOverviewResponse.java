package com.example.tool.dto;

import lombok.Data;

import java.util.List;

@Data
public class CalendarOverviewResponse {

    private String view;
    private String anchor;
    private String selectedDate;
    private String title;
    private String subtitle;
    private List<CalendarDayResponse> days;
    private List<CalendarMonthResponse> months;
    private List<CalendarAnniversaryNoteResponse> anniversaries;
}
