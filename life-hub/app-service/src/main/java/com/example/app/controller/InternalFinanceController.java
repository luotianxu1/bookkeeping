package com.example.app.controller;

import com.example.common.result.Result;
import com.example.app.dto.internal.PhotographyIncomeRequest;
import com.example.app.entity.AccountEntity;
import com.example.app.service.PhotographyAccountingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 仅供内部服务调用的资金接口（X-Internal-Key 鉴权，见 InternalApiKeyFilter）。
 * 服务合并后主要作为兼容入口保留，逻辑统一在 PhotographyAccountingService。
 */
@RestController
@RequestMapping("/api/internal/finance")
@Tag(name = "内部-资金接口", description = "服务间调用，外部前端禁止访问")
public class InternalFinanceController {

    private final PhotographyAccountingService photographyAccountingService;

    public InternalFinanceController(PhotographyAccountingService photographyAccountingService) {
        this.photographyAccountingService = photographyAccountingService;
    }

    @GetMapping("/cash-accounts/{accountId}")
    @Operation(summary = "校验并获取现金账户（归属+类型+状态）")
    public Result<PhotographyAccountingService.CashAccountView> cashAccount(
        @PathVariable("accountId") Long accountId,
        @RequestParam("userId") Long userId
    ) {
        AccountEntity account = photographyAccountingService.requireCashAccount(userId, accountId);
        return Result.ok(new PhotographyAccountingService.CashAccountView(account.getId(), userId, account.getName()));
    }

    @GetMapping("/cash-accounts")
    @Operation(summary = "批量获取现金账户名称")
    public Result<List<PhotographyAccountingService.CashAccountView>> cashAccounts(
        @RequestParam("userId") Long userId,
        @RequestParam("ids") List<Long> ids
    ) {
        List<PhotographyAccountingService.CashAccountView> views =
            photographyAccountingService.loadCashAccounts(userId, ids);
        return Result.ok(views);
    }

    @PostMapping("/photography-income")
    @Operation(summary = "创建摄影收入流水并原子加余额")
    public Result<Map<String, Long>> photographyIncome(@RequestBody PhotographyIncomeRequest request) {
        Long transactionId = photographyAccountingService.createPhotographyIncome(request);
        return Result.ok(Map.of("transactionId", transactionId));
    }

    @PostMapping("/photography-income/{transactionId}/void")
    @Operation(summary = "作废关联收入流水并原子扣回余额")
    public Result<Map<String, Object>> voidPhotographyIncome(
        @PathVariable("transactionId") Long transactionId,
        @RequestParam("userId") Long userId
    ) {
        PhotographyAccountingService.VoidIncomeResult result =
            photographyAccountingService.voidPhotographyIncome(transactionId, userId);
        return Result.ok(Map.of(
            "accountId", result.accountId(),
            "amount", result.refundedAmount()
        ));
    }
}
