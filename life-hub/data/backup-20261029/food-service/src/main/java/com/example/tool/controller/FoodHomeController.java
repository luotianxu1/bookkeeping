package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.FoodHomeResponse;
import com.example.tool.service.FoodHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tools/food")
@Tag(name = "餐饮模块", description = "餐饮相关的首页、菜品、分类、菜单与后台管理接口")
public class FoodHomeController {

    private final FoodHomeService foodHomeService;

    public FoodHomeController(FoodHomeService foodHomeService) {
        this.foodHomeService = foodHomeService;
    }

    @GetMapping("/home")
    @Operation(summary = "查询餐饮首页数据")
    public Result<FoodHomeResponse> home(@RequestParam("userId") @NotNull Long userId) {
        return Result.ok(foodHomeService.getHome(userId));
    }
}
