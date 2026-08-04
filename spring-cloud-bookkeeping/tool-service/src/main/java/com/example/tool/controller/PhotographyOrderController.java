package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.PhotographyOrderCollectFinalRequest;
import com.example.tool.dto.PhotographyOrderOverviewResponse;
import com.example.tool.dto.PhotographyOrderRequest;
import com.example.tool.dto.PhotographyOrderResponse;
import com.example.tool.service.PhotographyOrderService;
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
@RequestMapping("/api/tools/photography-orders")
@Tag(name = "摄影订单", description = "摄影订单管理接口")
public class PhotographyOrderController {

    private final PhotographyOrderService photographyOrderService;

    public PhotographyOrderController(PhotographyOrderService photographyOrderService) {
        this.photographyOrderService = photographyOrderService;
    }

    @GetMapping
    @Operation(summary = "查询摄影订单列表")
    public Result<List<PhotographyOrderResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(photographyOrderService.list(userId, status, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询摄影订单详情")
    public Result<PhotographyOrderResponse> detail(@PathVariable("id") @NotNull Long id) {
        return photographyOrderService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<PhotographyOrderResponse>fail().code(404).message("摄影订单不存在"));
    }

    @GetMapping("/overview")
    @Operation(summary = "查询摄影订单总览")
    public Result<PhotographyOrderOverviewResponse> overview(
        @RequestParam("userId") @NotNull Long userId,
        @RequestParam(name = "view", required = false) String view,
        @RequestParam(name = "anchor", required = false) String anchor,
        @RequestParam(name = "selectedDate", required = false) String selectedDate
    ) {
        return Result.ok(photographyOrderService.overview(userId, view, anchor, selectedDate));
    }

    @PostMapping
    @Operation(summary = "新增摄影订单")
    public Result<PhotographyOrderResponse> create(@Valid @RequestBody PhotographyOrderRequest request) {
        return Result.ok(photographyOrderService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改摄影订单")
    public Result<PhotographyOrderResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody PhotographyOrderRequest request
    ) {
        return photographyOrderService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<PhotographyOrderResponse>fail().code(404).message("摄影订单不存在"));
    }

    @PostMapping("/{id}/collect-final")
    @Operation(summary = "收取摄影订单尾款")
    public Result<PhotographyOrderResponse> collectFinal(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody PhotographyOrderCollectFinalRequest request
    ) {
        return Result.ok(photographyOrderService.collectFinal(id, request));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消摄影订单拍摄")
    public Result<PhotographyOrderResponse> cancel(
        @PathVariable("id") @NotNull Long id,
        @RequestParam("userId") @NotNull Long userId
    ) {
        return Result.ok(photographyOrderService.cancel(id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除摄影订单")
    public Result<Void> delete(
        @PathVariable("id") @NotNull Long id,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!photographyOrderService.delete(id, userId)) {
            return Result.<Void>fail().code(404).message("摄影订单不存在");
        }
        return Result.ok();
    }
}
