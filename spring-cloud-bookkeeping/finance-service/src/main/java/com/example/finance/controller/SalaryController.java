package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.SalaryAccountPageResponse;
import com.example.finance.dto.SalaryAccountRecordRequest;
import com.example.finance.dto.SalaryInitialBalanceRequest;
import com.example.finance.dto.SalaryMonthPageResponse;
import com.example.finance.dto.SalaryMonthRecordRequest;
import com.example.finance.dto.SalaryOverviewResponse;
import com.example.finance.dto.SalarySettingsRequest;
import com.example.finance.dto.SalarySettingsResponse;
import com.example.finance.dto.SalaryTaxPageResponse;
import com.example.finance.service.SalaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

@Validated
@RestController
@RequestMapping("/api/finance/salary")
@Tag(name = "工资管理", description = "工资管理、社保公积金医保和税务页面接口")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping("/overview")
    @Operation(summary = "查询工资管理首页")
    public Result<SalaryOverviewResponse> overview(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "month", required = false) String month
    ) {
        return Result.ok(salaryService.getOverview(userId, month));
    }

    @GetMapping("/settings")
    @Operation(summary = "查询工资设置")
    public Result<SalarySettingsResponse> settings(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "taxYear", required = false) Integer taxYear
    ) {
        return Result.ok(salaryService.getSettings(userId, taxYear));
    }

    @PutMapping("/settings")
    @Operation(summary = "保存工资设置")
    public Result<SalarySettingsResponse> saveSettings(@Valid @RequestBody SalarySettingsRequest request) {
        return Result.ok(salaryService.saveSettings(request));
    }

    @GetMapping("/records")
    @Operation(summary = "查询工资明细页")
    public Result<SalaryMonthPageResponse> salaryMonthPage(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "year", required = false) Integer year
    ) {
        return Result.ok(salaryService.getSalaryMonthPage(userId, year));
    }

    @PostMapping("/records")
    @Operation(summary = "新增工资月记录")
    public Result<SalaryMonthPageResponse> createSalaryMonthRecord(@Valid @RequestBody SalaryMonthRecordRequest request) {
        return Result.ok(salaryService.createSalaryMonthRecord(request));
    }

    @PutMapping("/records/{recordId}")
    @Operation(summary = "修改工资月记录")
    public Result<SalaryMonthPageResponse> updateSalaryMonthRecord(
        @PathVariable("recordId") @NotNull Long recordId,
        @Valid @RequestBody SalaryMonthRecordRequest request
    ) {
        return Result.ok(salaryService.updateSalaryMonthRecord(recordId, request));
    }

    @DeleteMapping("/records/{recordId}")
    @Operation(summary = "删除工资月记录")
    public Result<SalaryMonthPageResponse> deleteSalaryMonthRecord(
        @PathVariable("recordId") @NotNull Long recordId,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(salaryService.deleteSalaryMonthRecord(recordId, userId));
    }

    @GetMapping("/accounts/{accountType}")
    @Operation(summary = "查询工资账户页")
    public Result<SalaryAccountPageResponse> accountPage(
        @PathVariable("accountType") String accountType,
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "year", required = false) Integer year
    ) {
        return Result.ok(salaryService.getAccountPage(userId, accountType, year));
    }

    @PutMapping("/accounts/{accountType}/initial-balance")
    @Operation(summary = "设置工资账户初始值")
    public Result<SalaryAccountPageResponse> saveInitialBalance(
        @PathVariable("accountType") String accountType,
        @Valid @RequestBody SalaryInitialBalanceRequest request
    ) {
        return Result.ok(salaryService.saveInitialBalance(accountType, request));
    }

    @PostMapping("/accounts/{accountType}/records")
    @Operation(summary = "新增工资账户记录")
    public Result<SalaryAccountPageResponse> createRecord(
        @PathVariable("accountType") String accountType,
        @Valid @RequestBody SalaryAccountRecordRequest request
    ) {
        return Result.ok(salaryService.createAccountRecord(accountType, request));
    }

    @PutMapping("/accounts/{accountType}/records/{recordId}")
    @Operation(summary = "修改工资账户记录")
    public Result<SalaryAccountPageResponse> updateRecord(
        @PathVariable("accountType") String accountType,
        @PathVariable("recordId") @NotNull Long recordId,
        @Valid @RequestBody SalaryAccountRecordRequest request
    ) {
        return Result.ok(salaryService.updateAccountRecord(accountType, recordId, request));
    }

    @DeleteMapping("/accounts/{accountType}/records/{recordId}")
    @Operation(summary = "删除工资账户记录")
    public Result<SalaryAccountPageResponse> deleteRecord(
        @PathVariable("accountType") String accountType,
        @PathVariable("recordId") @NotNull Long recordId,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(salaryService.deleteAccountRecord(accountType, recordId, userId));
    }

    @GetMapping("/tax")
    @Operation(summary = "查询工资税务页")
    public Result<SalaryTaxPageResponse> taxPage(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "year", required = false) Integer year
    ) {
        return Result.ok(salaryService.getTaxPage(userId, year));
    }
}
