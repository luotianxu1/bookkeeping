package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.MarketNewsResponse;
import com.example.finance.service.MarketNewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/market-news")
@Tag(name = "市场快讯", description = "市场快讯接口")
public class MarketNewsController {

    private final MarketNewsService marketNewsService;

    public MarketNewsController(MarketNewsService marketNewsService) {
        this.marketNewsService = marketNewsService;
    }

    @GetMapping
    @Operation(summary = "查询市场快讯")
    public Result<MarketNewsResponse> list(
        @RequestParam(name = "category", required = false, defaultValue = "all") String category,
        @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(marketNewsService.getMarketNews(category, limit));
    }
}
