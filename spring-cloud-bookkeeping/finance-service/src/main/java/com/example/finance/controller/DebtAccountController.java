package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.DebtAccountSummaryResponse;
import com.example.finance.service.DebtAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(debtAccountService.summary(userId));
    }
}
