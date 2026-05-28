package com.example.tool.dto;

import lombok.Data;

@Data
public class CalendarDayResponse {

    private String date;
    private Integer day;
    private boolean currentMonth;
    private boolean weekend;
    private boolean today;
    private boolean selected;
    private int anniversaryCount;
}
