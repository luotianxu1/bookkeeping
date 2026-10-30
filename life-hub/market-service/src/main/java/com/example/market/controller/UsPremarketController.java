package com.example.market.controller;

import com.example.common.result.Result;
import com.example.market.dto.UsPremarketResponse;
import com.example.market.service.UsPremarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/us/premarket")
@Tag(name = "美股盘前", description = "美股盘前核心股票和市场排行榜")
public class UsPremarketController {

    private final UsPremarketService usPremarketService;

    public UsPremarketController(UsPremarketService usPremarketService) {
        this.usPremarketService = usPremarketService;
    }

    @GetMapping
    @Operation(summary = "查询美股盘前行情")
    public Result<UsPremarketResponse> getPremarket() {
        return Result.ok(usPremarketService.getPremarket());
    }
}
