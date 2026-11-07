package com.example.app.controller;

import com.example.app.dto.ReminderActionRequest;
import com.example.app.dto.ReminderResponse;
import com.example.app.dto.ReminderSettings;
import com.example.app.service.ReminderService;
import com.example.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tools/reminders")
@Tag(name = "统一提醒", description = "当前登录用户的站内提醒及个人设置")
public class ReminderController {
    private final ReminderService service;

    public ReminderController(ReminderService service) { this.service = service; }

    @GetMapping
    public Result<ReminderResponse> list() { return Result.ok(service.list()); }

    @PostMapping("/actions")
    public Result<Void> act(@Valid @RequestBody ReminderActionRequest request) {
        service.act(request);
        return Result.ok();
    }

    @GetMapping("/settings")
    public Result<ReminderSettings> settings() { return Result.ok(service.settings()); }

    @PutMapping("/settings")
    public Result<Void> saveSettings(@Valid @RequestBody ReminderSettings request) {
        service.saveSettings(request);
        return Result.ok();
    }
}
