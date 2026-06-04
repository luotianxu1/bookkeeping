package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.FoodDishRequest;
import com.example.tool.dto.FoodDishResponse;
import com.example.tool.service.FoodDishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/tools/food")
@Tag(name = "餐饮模块", description = "餐饮相关的首页、菜品、分类、菜单与后台管理接口")
public class FoodDishController {

    private final FoodDishService foodDishService;

    public FoodDishController(FoodDishService foodDishService) {
        this.foodDishService = foodDishService;
    }

    @GetMapping("/dishes")
    @Operation(summary = "查询菜品列表")
    public Result<List<FoodDishResponse>> dishes(
        @RequestParam("userId") @NotNull Long userId,
        @RequestParam(name = "categoryId", required = false) Long categoryId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(foodDishService.listDishes(userId, categoryId, status, keyword));
    }

    @GetMapping("/dishes/{id}")
    @Operation(summary = "查询菜品详情")
    public Result<FoodDishResponse> dishDetail(@PathVariable("id") @NotNull Long id) {
        return foodDishService.getDishById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<FoodDishResponse>fail().code(404).message("菜品不存在"));
    }

    @PostMapping("/dishes")
    @Operation(summary = "新增菜品")
    public Result<FoodDishResponse> createDish(@Valid @RequestBody FoodDishRequest request) {
        return Result.ok(foodDishService.createDish(request));
    }

    @PutMapping("/dishes/{id}")
    @Operation(summary = "修改菜品")
    public Result<FoodDishResponse> updateDish(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody FoodDishRequest request
    ) {
        return foodDishService.updateDish(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<FoodDishResponse>fail().code(404).message("菜品不存在"));
    }
}
