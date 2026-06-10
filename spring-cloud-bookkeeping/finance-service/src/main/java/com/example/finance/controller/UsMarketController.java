package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.UsMarketResponse;
import com.example.finance.service.UsMarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/us-market-indices")
@Tag(name = "美股指数", description = "标普500和纳指100行情接口")
public class UsMarketController {

    private final UsMarketService usMarketService;

    public UsMarketController(UsMarketService usMarketService) {
        this.usMarketService = usMarketService;
    }

    @GetMapping
    @Operation(summary = "查询美股主要指数行情")
    public Result<UsMarketResponse> detail() {
        return Result.ok(usMarketService.getOverview());
    }
}
