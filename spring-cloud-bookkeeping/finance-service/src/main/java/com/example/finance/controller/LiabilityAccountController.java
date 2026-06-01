package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.LiabilityAccountSummaryResponse;
import com.example.finance.dto.LiabilityPrepaymentRequest;
import com.example.finance.dto.LiabilityRepaymentRequest;
import com.example.finance.dto.LiabilityRecordRequest;
import com.example.finance.dto.LiabilityRecordResponse;
import com.example.finance.service.LiabilityAccountService;
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
@RequestMapping("/api/finance/liability-accounts")
@Tag(name = "负债账户", description = "负债账户汇总接口")
public class LiabilityAccountController {

    private final LiabilityAccountService liabilityAccountService;

    public LiabilityAccountController(LiabilityAccountService liabilityAccountService) {
        this.liabilityAccountService = liabilityAccountService;
    }

    @GetMapping("/summary")
    @Operation(summary = "查询负债账户汇总")
    public Result<LiabilityAccountSummaryResponse> summary(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(liabilityAccountService.summary(userId, accountId));
    }

    @GetMapping("/records")
    @Operation(summary = "查询负债记录列表")
    public Result<List<LiabilityRecordResponse>> listRecords(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(liabilityAccountService.listRecords(userId, accountId));
    }

    @PostMapping("/records")
    @Operation(summary = "新增负债记录")
    public Result<LiabilityRecordResponse> createRecord(@Valid @RequestBody LiabilityRecordRequest request) {
        return Result.ok(liabilityAccountService.createRecord(request));
    }

    @PutMapping("/records/{id}")
    @Operation(summary = "修改负债记录")
    public Result<LiabilityRecordResponse> updateRecord(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody LiabilityRecordRequest request
    ) {
        return liabilityAccountService.updateRecord(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<LiabilityRecordResponse>fail().code(404).message("负债记录不存在"));
    }

    @PostMapping("/records/{id}/repay")
    @Operation(summary = "负债账单还款")
    public Result<LiabilityRecordResponse> repayRecord(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody LiabilityRepaymentRequest request
    ) {
        return liabilityAccountService.repayRecord(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<LiabilityRecordResponse>fail().code(404).message("负债记录不存在"));
    }

    @PostMapping("/{accountId}/prepay")
    @Operation(summary = "负债账户提前还款")
    public Result<Void> prepayAccount(
        @PathVariable("accountId") @NotNull Long accountId,
        @Valid @RequestBody LiabilityPrepaymentRequest request
    ) {
        liabilityAccountService.prepayAccount(accountId, request);
        return Result.ok();
    }

    @DeleteMapping("/records/{id}")
    @Operation(summary = "删除负债记录")
    public Result<Void> deleteRecord(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!liabilityAccountService.deleteRecord(id, userId)) {
            return Result.<Void>fail().code(404).message("负债记录不存在");
        }
        return Result.ok();
    }
}
