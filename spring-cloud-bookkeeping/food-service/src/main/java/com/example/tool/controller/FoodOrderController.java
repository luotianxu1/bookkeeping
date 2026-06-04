package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.FoodOrderCreateRequest;
import com.example.tool.dto.FoodOrderResponse;
import com.example.tool.service.FoodOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tools/food")
@Tag(name = "餐饮模块", description = "餐饮相关的首页、菜品、分类、菜单与后台管理接口")
public class FoodOrderController {

    private final FoodOrderService foodOrderService;

    public FoodOrderController(FoodOrderService foodOrderService) {
        this.foodOrderService = foodOrderService;
    }

    @GetMapping("/orders")
    @Operation(summary = "查询菜单订单列表")
    public Result<List<FoodOrderResponse>> orders(
        @RequestParam("userId") @NotNull Long userId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(foodOrderService.listOrders(userId, status, keyword));
    }

    @PostMapping("/orders")
    @Operation(summary = "创建菜单订单")
    public Result<FoodOrderResponse> createOrder(@Valid @RequestBody FoodOrderCreateRequest request) {
        return Result.ok(foodOrderService.createOrder(request));
    }
}
