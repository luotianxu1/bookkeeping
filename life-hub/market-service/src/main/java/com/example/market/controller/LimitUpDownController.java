package com.example.market.controller;

import com.example.common.result.Result;
import com.example.market.dto.LimitUpDownResponse;
import com.example.market.service.LimitUpDownService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/limit-up-down")
@Tag(name = "涨跌停数据", description = "A股涨停、跌停和炸板统计")
public class LimitUpDownController {

    private final LimitUpDownService limitUpDownService;

    public LimitUpDownController(LimitUpDownService limitUpDownService) {
        this.limitUpDownService = limitUpDownService;
    }

    @GetMapping
    @Operation(summary = "查询A股涨跌停数据")
    public Result<LimitUpDownResponse> getLimitUpDown() {
        return Result.ok(limitUpDownService.getLimitUpDown());
    }
}
