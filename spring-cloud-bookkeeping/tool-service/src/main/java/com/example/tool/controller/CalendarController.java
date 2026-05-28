package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.CalendarOverviewResponse;
import com.example.tool.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tools/calendar")
@Tag(name = "日历", description = "日历概览接口")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    @Operation(summary = "查询日历概览")
    public Result<CalendarOverviewResponse> overview(
        @RequestParam("userId") @NotNull Long userId,
        @RequestParam(name = "view", required = false) String view,
        @RequestParam(name = "anchor", required = false) String anchor,
        @RequestParam(name = "selectedDate", required = false) String selectedDate
    ) {
        return Result.ok(calendarService.overview(userId, view, anchor, selectedDate));
    }
}
