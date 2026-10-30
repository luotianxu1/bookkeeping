package com.example.market.gold;

import com.example.common.result.Result;
import com.example.market.gold.dto.GoldPriceResponse;
import com.example.market.gold.dto.GoldRealtimePriceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 金价 API（2026-10-30 自 app-service 迁入，接口参数与响应结构保持一致）。
 *
 * <p>数据口径不变：金投网黄金9999（CNY/g）+ 伦敦金（USD/oz）+ 品牌首饰金价 + 走势图。</p>
 */
@RestController
@RequestMapping("/api/market/gold-prices")
@Tag(name = "金价", description = "金价行情接口（金投网源，自 app-service 迁入）")
public class GoldPriceController {

    private final GoldPriceService goldPriceService;

    public GoldPriceController(GoldPriceService goldPriceService) {
        this.goldPriceService = goldPriceService;
    }

    @GetMapping("/realtime")
    @Operation(summary = "查询实时金价")
    public Result<GoldRealtimePriceResponse> realtime(
        @RequestParam(name = "forceRefreshCurrent", required = false, defaultValue = "false") boolean forceRefreshCurrent
    ) throws Exception {
        return Result.ok(goldPriceService.getRealtimePrice(forceRefreshCurrent));
    }

    @GetMapping
    @Operation(summary = "查询金价行情", description = "range: 1d/7d/30d/3m/1y/3y；includeChart=false 时不返回走势点")
    public Result<GoldPriceResponse> detail(
        @RequestParam(name = "range", required = false, defaultValue = "1d") String range,
        @RequestParam(name = "forceRefreshCurrent", required = false, defaultValue = "false") boolean forceRefreshCurrent,
        @RequestParam(name = "includeChart", required = false, defaultValue = "true") boolean includeChart
    ) {
        return Result.ok(goldPriceService.getGoldPrice(range, forceRefreshCurrent, includeChart));
    }
}
