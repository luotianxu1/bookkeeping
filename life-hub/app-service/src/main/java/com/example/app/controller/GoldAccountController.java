package com.example.app.controller;

import com.example.common.result.Result;
import com.example.app.dto.GoldAccountHoldingResponse;
import com.example.app.dto.GoldAccountSummaryResponse;
import com.example.app.dto.GoldLiquidationResponse;
import com.example.app.service.GoldAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/finance/gold-accounts")
@Tag(name = "黄金账户", description = "黄金账户汇总、持仓、清仓记录接口")
public class GoldAccountController {

    private final GoldAccountService goldAccountService;

    public GoldAccountController(GoldAccountService goldAccountService) {
        this.goldAccountService = goldAccountService;
    }

    @GetMapping("/summary")
    @Operation(summary = "查询黄金账户汇总")
    public Result<GoldAccountSummaryResponse> summary(
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(goldAccountService.summary(userId));
    }

    @GetMapping("/holdings")
    @Operation(summary = "查询黄金持仓列表")
    public Result<List<GoldAccountHoldingResponse>> holdings(
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(goldAccountService.holdings(userId));
    }

    @GetMapping("/liquidations")
    @Operation(summary = "查询黄金清仓记录")
    public Result<GoldLiquidationResponse> liquidations(
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(goldAccountService.liquidations(userId));
    }
}
