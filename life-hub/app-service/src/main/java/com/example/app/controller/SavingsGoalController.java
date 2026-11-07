package com.example.app.controller;

import com.example.app.dto.SavingsGoalRequest;
import com.example.app.dto.SavingsGoalResponse;
import com.example.app.dto.SavingsGoalEntryRequest;
import com.example.app.dto.SavingsGoalEntryResponse;
import com.example.app.service.SavingsGoalService;
import com.example.common.result.Result;
import com.example.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/finance/savings-goals")
public class SavingsGoalController {
    private final SavingsGoalService service;
    public SavingsGoalController(SavingsGoalService service) { this.service = service; }

    @GetMapping
    public Result<List<SavingsGoalResponse>> list() { return Result.ok(service.list(SecurityUtils.requireCurrentUserId())); }

    @PostMapping
    public Result<SavingsGoalResponse> create(@Valid @RequestBody SavingsGoalRequest request) {
        return Result.ok(service.create(SecurityUtils.requireCurrentUserId(), request));
    }

    @PutMapping("/{id}")
    public Result<SavingsGoalResponse> update(@PathVariable("id") long id, @Valid @RequestBody SavingsGoalRequest request) {
        return Result.ok(service.update(SecurityUtils.requireCurrentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        service.delete(SecurityUtils.requireCurrentUserId(), id); return Result.ok();
    }

    @GetMapping("/{id}/entries")
    public Result<SavingsGoalEntryResponse.Page> entries(@PathVariable("id") long id,
        @RequestParam(value = "beforeId", required = false) Long beforeId) {
        return Result.ok(service.entries(SecurityUtils.requireCurrentUserId(), id, beforeId));
    }

    @PostMapping("/{id}/entries")
    public Result<SavingsGoalEntryResponse> addEntry(@PathVariable("id") long id,
        @Valid @RequestBody SavingsGoalEntryRequest request) {
        return Result.ok(service.addEntry(SecurityUtils.requireCurrentUserId(), id, request));
    }
}
