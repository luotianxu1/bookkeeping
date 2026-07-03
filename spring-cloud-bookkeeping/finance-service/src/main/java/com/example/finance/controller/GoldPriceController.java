package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.GoldPriceResponse;
import com.example.finance.dto.GoldRealtimePriceResponse;
import com.example.finance.service.GoldPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/gold-prices")
@Tag(name = "金价", description = "金价行情接口")
public class GoldPriceController {

    private final GoldPriceService goldPriceService;

    public GoldPriceController(GoldPriceService goldPriceService) {
        this.goldPriceService = goldPriceService;
    }

    @GetMapping("/realtime")
    @Operation(summary = "查询实时金价")
    public Result<GoldRealtimePriceResponse> realtime() {
        return Result.ok(goldPriceService.getRealtimePrice());
    }

    @GetMapping
    @Operation(summary = "查询金价行情")
    public Result<GoldPriceResponse> detail(
        @RequestParam(name = "range", required = false, defaultValue = "1d") String range,
        @RequestParam(name = "forceRefreshCurrent", required = false, defaultValue = "false") boolean forceRefreshCurrent
    ) {
        return Result.ok(goldPriceService.getGoldPrice(range, forceRefreshCurrent));
    }
}
