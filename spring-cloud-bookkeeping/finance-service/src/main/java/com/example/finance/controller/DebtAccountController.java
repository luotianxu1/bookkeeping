package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.DebtAccountSummaryResponse;
import com.example.finance.dto.DebtRecordRequest;
import com.example.finance.dto.DebtRecordResponse;
import com.example.finance.service.DebtAccountService;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/finance/debt-accounts")
@Tag(name = "债务账户", description = "债务账户汇总接口")
public class DebtAccountController {

    private final DebtAccountService debtAccountService;

    public DebtAccountController(DebtAccountService debtAccountService) {
        this.debtAccountService = debtAccountService;
    }

    @GetMapping("/summary")
    @Operation(summary = "查询债务账户汇总")
    public Result<DebtAccountSummaryResponse> summary(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(debtAccountService.summary(userId, accountId));
    }

    @GetMapping("/records")
    @Operation(summary = "查询债务记录列表")
    public Result<List<DebtRecordResponse>> listRecords(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(debtAccountService.listRecords(userId, accountId));
    }

    @PostMapping("/records")
    @Operation(summary = "新增债务记录")
    public Result<DebtRecordResponse> createRecord(@Valid @RequestBody DebtRecordRequest request) {
        return Result.ok(debtAccountService.createRecord(request));
    }

    @PutMapping("/records/{id}")
    @Operation(summary = "修改债务记录")
    public Result<DebtRecordResponse> updateRecord(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody DebtRecordRequest request
    ) {
        return debtAccountService.updateRecord(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<DebtRecordResponse>fail().code(404).message("债务记录不存在"));
    }

    @DeleteMapping("/records/{id}")
    @Operation(summary = "删除债务记录")
    public Result<Void> deleteRecord(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!debtAccountService.deleteRecord(id, userId)) {
            return Result.<Void>fail().code(404).message("债务记录不存在");
        }
        return Result.ok();
    }
}
