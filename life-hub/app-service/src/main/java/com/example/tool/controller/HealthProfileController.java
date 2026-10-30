package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.HealthProfileRequest;
import com.example.tool.dto.HealthProfileResponse;
import com.example.tool.service.HealthProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tools/health-profile")
@Tag(name = "健康档案", description = "计算体脂率所需的身体数据（身高、年龄、性别）")
public class HealthProfileController {

    private final HealthProfileService healthProfileService;

    public HealthProfileController(HealthProfileService healthProfileService) {
        this.healthProfileService = healthProfileService;
    }

    @GetMapping
    @Operation(summary = "查询健康档案（未设置时返回空）")
    public Result<HealthProfileResponse> get(@RequestParam(name = "userId", required = false) Long userId) {
        return Result.ok(healthProfileService.get(userId).orElse(null));
    }

    @PutMapping
    @Operation(summary = "保存健康档案（按用户 upsert）")
    public Result<HealthProfileResponse> save(@Valid @RequestBody HealthProfileRequest request) {
        return Result.ok(healthProfileService.save(request));
    }
}
