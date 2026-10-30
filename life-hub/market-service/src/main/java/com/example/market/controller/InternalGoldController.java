package com.example.market.controller;

import com.example.common.result.Result;
import com.example.market.gold.GoldPriceService;
import com.example.market.gold.dto.GoldPriceResponse;
import com.example.market.gold.dto.GoldRealtimePriceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 金价内部接口：供 app-service（金价页适配层、金账户估值、资产趋势、缓存预热任务）
 * 等服务间调用，由 InternalApiKeyFilter 校验 X-Internal-Key，不走用户鉴权。
 */
@RestController
@RequestMapping("/api/internal/gold")
@Tag(name = "金价内部接口", description = "服务间金价数据通道（X-Internal-Key 鉴权）")
public class InternalGoldController {

    private final GoldPriceService goldPriceService;

    public InternalGoldController(GoldPriceService goldPriceService) {
        this.goldPriceService = goldPriceService;
    }

    @GetMapping("/price")
    @Operation(summary = "金价行情（含可选走势图）", description = "range: 1d/7d/30d/3m/1y/3y")
    public Result<GoldPriceResponse> price(
        @RequestParam(name = "range", required = false, defaultValue = "1d") String range,
        @RequestParam(name = "forceRefreshCurrent", required = false, defaultValue = "false") boolean forceRefreshCurrent,
        @RequestParam(name = "includeChart", required = false, defaultValue = "true") boolean includeChart
    ) {
        return Result.ok(goldPriceService.getGoldPrice(range, forceRefreshCurrent, includeChart));
    }

    @GetMapping("/realtime")
    @Operation(summary = "实时金价", description = "forceRefreshCurrent=true 时刷新失败会抛错（保持原语义）")
    public Result<GoldRealtimePriceResponse> realtime(
        @RequestParam(name = "forceRefreshCurrent", required = false, defaultValue = "false") boolean forceRefreshCurrent
    ) throws Exception {
        return Result.ok(goldPriceService.getRealtimePrice(forceRefreshCurrent));
    }

    @GetMapping("/spot-price")
    @Operation(summary = "缓存中的现货金价格（CNY/g）", description = "缓存未预热或无有效价格时返回 null")
    public Result<BigDecimal> spotPrice() {
        return Result.ok(goldPriceService.getCachedSpotPrice());
    }

    @GetMapping("/refresh")
    @Operation(summary = "刷新金价缓存", description = "供 app-service 定时任务预热调用；上游失败时抛错")
    public Result<Map<String, Object>> refresh() throws Exception {
        goldPriceService.refreshCache();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("refreshedAt", LocalDateTime.now());
        return Result.ok(body);
    }
}
