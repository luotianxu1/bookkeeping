package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.TransactionAnalysisResponse;
import com.example.finance.dto.TransactionRequest;
import com.example.finance.dto.TransactionResponse;
import com.example.finance.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/finance/transactions")
@Tag(name = "收支流水", description = "收支流水接口")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "查询收支流水列表")
    public Result<List<TransactionResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(transactionService.list(userId, type, accountId));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "查询指定账户收支流水")
    public Result<List<TransactionResponse>> listByAccount(
        @PathVariable("accountId") Long accountId,
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "type", required = false) String type
    ) {
        return Result.ok(transactionService.list(userId, type, accountId));
    }

    @GetMapping("/analysis")
    @Operation(summary = "查询收支分析")
    public Result<TransactionAnalysisResponse> getAnalysis(
        @RequestParam("userId") Long userId,
        @RequestParam(name = "period", required = false) String period,
        @RequestParam(name = "month", required = false) String month,
        @RequestParam(name = "year", required = false) Integer year
    ) {
        return Result.ok(transactionService.getAnalysis(userId, period, month, year));
    }

    @PostMapping
    @Operation(summary = "新增支出或收入")
    public Result<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        return Result.ok(transactionService.create(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除收支流水")
    public Result<Void> delete(
        @PathVariable("id") Long id,
        @RequestParam(name = "userId") Long userId
    ) {
        if (!transactionService.delete(id, userId)) {
            return Result.<Void>fail().code(404).message("收支记录不存在");
        }
        return Result.ok();
    }
}
