package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.HumanRelationRecordRequest;
import com.example.finance.dto.HumanRelationRecordResponse;
import com.example.finance.dto.HumanRelationSummaryResponse;
import com.example.finance.service.HumanRelationAccountService;
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
@RequestMapping("/api/finance/human-relation-accounts")
@Tag(name = "人情账户", description = "人情账户汇总接口")
public class HumanRelationAccountController {

    private final HumanRelationAccountService humanRelationAccountService;

    public HumanRelationAccountController(HumanRelationAccountService humanRelationAccountService) {
        this.humanRelationAccountService = humanRelationAccountService;
    }

    @GetMapping("/summary")
    @Operation(summary = "查询人情账户汇总")
    public Result<HumanRelationSummaryResponse> summary(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(humanRelationAccountService.summary(userId, accountId));
    }

    @GetMapping("/records")
    @Operation(summary = "查询人情记录列表")
    public Result<List<HumanRelationRecordResponse>> listRecords(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(humanRelationAccountService.listRecords(userId, accountId));
    }

    @PostMapping("/records")
    @Operation(summary = "新增人情记录")
    public Result<HumanRelationRecordResponse> createRecord(@Valid @RequestBody HumanRelationRecordRequest request) {
        return Result.ok(humanRelationAccountService.createRecord(request));
    }

    @PutMapping("/records/{id}")
    @Operation(summary = "修改人情记录")
    public Result<HumanRelationRecordResponse> updateRecord(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody HumanRelationRecordRequest request
    ) {
        return humanRelationAccountService.updateRecord(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<HumanRelationRecordResponse>fail().code(404).message("人情记录不存在"));
    }

    @DeleteMapping("/records/{id}")
    @Operation(summary = "删除人情记录")
    public Result<Void> deleteRecord(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!humanRelationAccountService.deleteRecord(id, userId)) {
            return Result.<Void>fail().code(404).message("人情记录不存在");
        }
        return Result.ok();
    }
}
