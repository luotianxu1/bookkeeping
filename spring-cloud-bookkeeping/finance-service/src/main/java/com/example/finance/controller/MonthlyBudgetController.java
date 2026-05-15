package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.MonthlyBudgetRequest;
import com.example.finance.dto.MonthlyBudgetResponse;
import com.example.finance.service.MonthlyBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/finance/monthly-budgets")
@Tag(name = "月预算", description = "月预算增删改查接口")
public class MonthlyBudgetController {

    private final MonthlyBudgetService monthlyBudgetService;

    public MonthlyBudgetController(MonthlyBudgetService monthlyBudgetService) {
        this.monthlyBudgetService = monthlyBudgetService;
    }

    @GetMapping
    @Operation(summary = "查询月预算列表")
    public Result<List<MonthlyBudgetResponse>> list(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return Result.ok(monthlyBudgetService.list(userId, limit));
    }

    @GetMapping("/current")
    @Operation(summary = "查询指定月份预算")
    public Result<MonthlyBudgetResponse> current(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "budgetMonth", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate budgetMonth
    ) {
        return monthlyBudgetService.getCurrent(userId, budgetMonth)
            .map(Result::ok)
            .orElseGet(() -> Result.ok(null));
    }

    @PostMapping
    @Operation(summary = "新增月预算")
    public Result<MonthlyBudgetResponse> create(@Valid @RequestBody MonthlyBudgetRequest request) {
        return Result.ok(monthlyBudgetService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改月预算")
    public Result<MonthlyBudgetResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody MonthlyBudgetRequest request
    ) {
        return monthlyBudgetService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<MonthlyBudgetResponse>fail().code(404).message("预算不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除月预算")
    public Result<Void> delete(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!monthlyBudgetService.delete(id, userId)) {
            return Result.<Void>fail().code(404).message("预算不存在");
        }
        return Result.ok();
    }
}
