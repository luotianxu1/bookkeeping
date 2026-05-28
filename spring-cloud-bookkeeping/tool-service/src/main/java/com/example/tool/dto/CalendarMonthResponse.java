package com.example.tool.dto;

import lombok.Data;

import java.util.List;

@Data
public class CalendarMonthResponse {

    private String key;
    private String label;
    private int daysInMonth;
    private boolean current;
    private boolean selected;
    private List<CalendarDayResponse> days;
}
