package com.example.market.controller;

import com.example.common.result.Result;
import com.example.market.service.UsOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/market/us/overview")
@Tag(name = "美股行情", description = "美股行情页指数卡聚合（现价 + 当日分时）")
public class UsOverviewController {

    private final UsOverviewService usOverviewService;

    public UsOverviewController(UsOverviewService usOverviewService) {
        this.usOverviewService = usOverviewService;
    }

    @GetMapping
    @Operation(summary = "查询美股行情页指数卡聚合数据", description = "标普500（腾讯快照+SPY分时折算）与纳斯达克100（QQQ代理），15s 缓存")
    public Result<Map<String, Object>> getOverview() {
        return Result.ok(usOverviewService.getOverview());
    }
}
