package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.WeightRecordRequest;
import com.example.tool.dto.WeightRecordResponse;
import com.example.tool.service.WeightRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tools/weight-records")
@Tag(name = "体重打卡", description = "体重打卡记录接口")
public class WeightRecordController {

    private final WeightRecordService weightRecordService;

    public WeightRecordController(WeightRecordService weightRecordService) {
        this.weightRecordService = weightRecordService;
    }

    @GetMapping
    @Operation(summary = "查询最近体重记录（按日期倒序）")
    public Result<List<WeightRecordResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return Result.ok(weightRecordService.listRecent(userId, limit));
    }

    @PostMapping
    @Operation(summary = "保存体重打卡（同日覆盖更新）")
    public Result<WeightRecordResponse> save(@Valid @RequestBody WeightRecordRequest request) {
        return Result.ok(weightRecordService.save(request));
    }
}
