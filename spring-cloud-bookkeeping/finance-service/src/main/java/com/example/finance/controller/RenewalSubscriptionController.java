package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.RenewalSubscriptionRequest;
import com.example.finance.dto.RenewalSubscriptionResponse;
import com.example.finance.dto.RenewalSubscriptionSummaryResponse;
import com.example.finance.service.RenewalSubscriptionService;
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
@RequestMapping("/api/finance/renewal-subscriptions")
@Tag(name = "固定支出", description = "固定支出管理与自动扣款接口")
public class RenewalSubscriptionController {

    private final RenewalSubscriptionService renewalSubscriptionService;

    public RenewalSubscriptionController(RenewalSubscriptionService renewalSubscriptionService) {
        this.renewalSubscriptionService = renewalSubscriptionService;
    }

    @GetMapping
    @Operation(summary = "查询固定支出列表")
    public Result<List<RenewalSubscriptionResponse>> list(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "status", required = false) String status
    ) {
        return Result.ok(renewalSubscriptionService.list(userId, status));
    }

    @GetMapping("/summary")
    @Operation(summary = "查询固定支出汇总")
    public Result<RenewalSubscriptionSummaryResponse> summary(
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(renewalSubscriptionService.summary(userId));
    }

    @PostMapping
    @Operation(summary = "新增固定支出")
    public Result<RenewalSubscriptionResponse> create(@Valid @RequestBody RenewalSubscriptionRequest request) {
        return Result.ok(renewalSubscriptionService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改固定支出")
    public Result<RenewalSubscriptionResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody RenewalSubscriptionRequest request
    ) {
        return renewalSubscriptionService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<RenewalSubscriptionResponse>fail().code(404).message("固定支出不存在"));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "暂停固定支出")
    public Result<RenewalSubscriptionResponse> pause(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return renewalSubscriptionService.pause(id, userId)
            .map(Result::ok)
            .orElseGet(() -> Result.<RenewalSubscriptionResponse>fail().code(404).message("固定支出不存在"));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "恢复固定支出")
    public Result<RenewalSubscriptionResponse> resume(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return renewalSubscriptionService.resume(id, userId)
            .map(Result::ok)
            .orElseGet(() -> Result.<RenewalSubscriptionResponse>fail().code(404).message("固定支出不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除固定支出")
    public Result<Void> delete(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!renewalSubscriptionService.delete(id, userId)) {
            return Result.<Void>fail().code(404).message("固定支出不存在");
        }
        return Result.ok();
    }
}
