package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.AnniversaryRequest;
import com.example.tool.dto.AnniversaryResponse;
import com.example.tool.service.AnniversaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tools/anniversaries")
@Tag(name = "纪念日", description = "纪念日增删改查接口")
public class AnniversaryController {

    private final AnniversaryService anniversaryService;

    public AnniversaryController(AnniversaryService anniversaryService) {
        this.anniversaryService = anniversaryService;
    }

    @GetMapping
    @Operation(summary = "查询纪念日列表")
    public Result<List<AnniversaryResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "scope", required = false) String scope,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(anniversaryService.list(userId, scope, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询纪念日详情")
    public Result<AnniversaryResponse> detail(@PathVariable("id") @NotNull Long id) {
        return anniversaryService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<AnniversaryResponse>fail().code(404).message("纪念日不存在"));
    }

    @PostMapping
    @Operation(summary = "新增纪念日")
    public Result<AnniversaryResponse> create(@Valid @RequestBody AnniversaryRequest request) {
        return Result.ok(anniversaryService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改纪念日")
    public Result<AnniversaryResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody AnniversaryRequest request
    ) {
        return anniversaryService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<AnniversaryResponse>fail().code(404).message("纪念日不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除纪念日")
    public Result<Void> delete(
        @PathVariable("id") @NotNull Long id,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!anniversaryService.delete(id, userId)) {
            return Result.<Void>fail().code(404).message("纪念日不存在");
        }
        return Result.ok();
    }
}
