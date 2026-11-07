package com.example.app.controller;

import com.example.app.dto.*;
import com.example.app.service.ExpenseRecoveryService;
import com.example.common.result.Result;
import com.example.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/finance/expense-recoveries")
public class ExpenseRecoveryController {
    private final ExpenseRecoveryService service;
    public ExpenseRecoveryController(ExpenseRecoveryService service) { this.service = service; }
    @GetMapping
    public Result<ExpenseRecoveryResponse.Page> list(@RequestParam(defaultValue = "all") String state,
        @RequestParam(required = false) Long transactionId, @RequestParam(defaultValue = "1") int page) {
        return Result.ok(service.list(SecurityUtils.requireCurrentUserId(), state, transactionId, page));
    }
    @GetMapping("/{id}")
    public Result<ExpenseRecoveryResponse> detail(@PathVariable long id) {
        return Result.ok(service.detail(SecurityUtils.requireCurrentUserId(), id));
    }
    public record Plan(@NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal amount) {}
    @PutMapping("/{id}/plan")
    public Result<ExpenseRecoveryResponse> plan(@PathVariable long id, @Valid @RequestBody Plan request) {
        return Result.ok(service.plan(SecurityUtils.requireCurrentUserId(), id, request.amount()));
    }
    @PostMapping
    public Result<ExpenseRecoveryResponse.Receipt> receive(@Valid @RequestBody ExpenseRecoveryRequest request) {
        return Result.ok(service.receive(SecurityUtils.requireCurrentUserId(), request));
    }
}
