package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.FoodIngredientRequest;
import com.example.tool.dto.FoodIngredientResponse;
import com.example.tool.service.FoodIngredientService;
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
@RequestMapping("/api/tools/food")
@Tag(name = "餐饮模块", description = "餐饮相关的首页、菜品、分类、菜单与后台管理接口")
public class FoodIngredientController {

    private final FoodIngredientService foodIngredientService;

    public FoodIngredientController(FoodIngredientService foodIngredientService) {
        this.foodIngredientService = foodIngredientService;
    }

    @GetMapping("/ingredients")
    @Operation(summary = "查询食材列表")
    public Result<List<FoodIngredientResponse>> ingredients(
        @RequestParam("userId") @NotNull Long userId,
        @RequestParam(name = "categoryId", required = false) Long categoryId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(foodIngredientService.listIngredients(userId, categoryId, status, keyword));
    }

    @PostMapping("/ingredients")
    @Operation(summary = "新增食材")
    public Result<FoodIngredientResponse> createIngredient(@Valid @RequestBody FoodIngredientRequest request) {
        return Result.ok(foodIngredientService.createIngredient(request));
    }

    @PutMapping("/ingredients/{id}")
    @Operation(summary = "修改食材")
    public Result<FoodIngredientResponse> updateIngredient(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody FoodIngredientRequest request
    ) {
        return foodIngredientService.updateIngredient(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<FoodIngredientResponse>fail().code(404).message("食材不存在"));
    }

    @DeleteMapping("/ingredients/{id}")
    @Operation(summary = "删除食材")
    public Result<Void> deleteIngredient(@PathVariable("id") @NotNull Long id) {
        return foodIngredientService.deleteIngredient(id)
            ? Result.ok()
            : Result.<Void>fail().code(404).message("食材不存在");
    }
}
