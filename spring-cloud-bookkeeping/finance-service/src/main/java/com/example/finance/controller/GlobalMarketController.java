package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.GlobalMarketKLineItemResponse;
import com.example.finance.dto.GlobalMarketQuoteResponse;
import com.example.finance.service.GlobalMarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/finance/global-market")
@Tag(name = "全球行情", description = "全球品种行情：东方财富统一主源，新浪/腾讯/FRED 按品种补充")
public class GlobalMarketController {

    private static final String DEFAULT_SYMBOLS = "hf_OIL";
    /** 上限仅防滥用：新浪/东财报价均已合并为批量请求，品种数增加不会放大上游请求数 */
    private static final int MAX_SYMBOL_COUNT = 30;

    private final GlobalMarketService globalMarketService;

    public GlobalMarketController(GlobalMarketService globalMarketService) {
        this.globalMarketService = globalMarketService;
    }

    @GetMapping("/quotes")
    @Operation(summary = "查询全球品种实时行情", description = "symbols 为品种代码（如 SH000001/SPX/hf_OIL/US10Y），东财批量接口一次返回")
    public Result<List<GlobalMarketQuoteResponse>> quotes(
        @RequestParam(name = "symbols", required = false, defaultValue = DEFAULT_SYMBOLS) String symbols
    ) {
        return Result.ok(globalMarketService.getQuotes(parseSymbols(symbols)));
    }

    @GetMapping("/kline")
    @Operation(summary = "查询全球品种K线", description = "symbol 为品种代码；period 取分时/分钟/日/周/月/年；days 为返回K线数量上限")
    public Result<List<GlobalMarketKLineItemResponse>> kline(
        @RequestParam(name = "symbol") String symbol,
        @RequestParam(name = "period", required = false, defaultValue = "day") String period,
        @RequestParam(name = "days", required = false, defaultValue = "90") Integer days
    ) {
        String validated = symbol == null ? "" : symbol.trim();
        if (!validated.matches("[A-Za-z0-9_]{1,20}")) {
            throw new IllegalArgumentException("行情品种代码不合法");
        }
        int limit = Math.max(10, Math.min(1200, days == null ? 90 : days));
        List<GlobalMarketKLineItemResponse> items = switch (period) {
            case "m1" -> globalMarketService.getMinuteKLine(validated, 1, limit);
            case "m5" -> globalMarketService.getMinuteKLine(validated, 5, limit);
            case "m15" -> globalMarketService.getMinuteKLine(validated, 15, limit);
            case "m30" -> globalMarketService.getMinuteKLine(validated, 30, limit);
            case "m60" -> globalMarketService.getMinuteKLine(validated, 60, limit);
            case "week" -> globalMarketService.getAggregatedKLine(validated, GlobalMarketService.AggregationPeriod.WEEK, limit);
            case "month" -> globalMarketService.getAggregatedKLine(validated, GlobalMarketService.AggregationPeriod.MONTH, limit);
            case "year" -> globalMarketService.getAggregatedKLine(validated, GlobalMarketService.AggregationPeriod.YEAR, limit);
            case "day" -> globalMarketService.getDailyKLine(validated, limit);
            default -> throw new IllegalArgumentException("不支持的K线周期: " + period);
        };
        return Result.ok(items);
    }

    private List<String> parseSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return List.of(DEFAULT_SYMBOLS);
        }
        // 注意：新浪品种代码大小写敏感（hf_OIL ≠ hf_oil），保留原始大小写
        List<String> validated = Arrays.stream(symbols.trim().split(","))
            .map(String::trim)
            .filter(value -> value.matches("[A-Za-z0-9_]{1,20}"))
            .limit(MAX_SYMBOL_COUNT)
            .toList();
        if (validated.isEmpty()) {
            throw new IllegalArgumentException("行情品种代码不合法");
        }
        return validated;
    }
}
