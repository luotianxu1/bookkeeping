package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.MarketStatusResponse;
import com.example.finance.service.MarketStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/market-status")
@Tag(name = "大盘状态", description = "A股主要指数、市场宽度和强弱状态")
public class MarketStatusController {

    private final MarketStatusService marketStatusService;

    public MarketStatusController(MarketStatusService marketStatusService) {
        this.marketStatusService = marketStatusService;
    }

    @GetMapping
    @Operation(summary = "查询A股大盘状态")
    public Result<MarketStatusResponse> getMarketStatus() {
        return Result.ok(marketStatusService.getMarketStatus());
    }
}
