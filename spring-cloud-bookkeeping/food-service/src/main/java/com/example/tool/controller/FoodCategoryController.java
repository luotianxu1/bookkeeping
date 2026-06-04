package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.FoodCategoryRequest;
import com.example.tool.dto.FoodCategoryResponse;
import com.example.tool.service.FoodCategoryService;
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
public class FoodCategoryController {

    private final FoodCategoryService foodCategoryService;

    public FoodCategoryController(FoodCategoryService foodCategoryService) {
        this.foodCategoryService = foodCategoryService;
    }

    @GetMapping("/categories")
    @Operation(summary = "查询餐饮分类列表")
    public Result<List<FoodCategoryResponse>> categories(
        @RequestParam("userId") @NotNull Long userId,
        @RequestParam(name = "categoryType", required = false) String categoryType,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "status", required = false) String status
    ) {
        return Result.ok(foodCategoryService.listCategories(userId, categoryType, keyword, status));
    }

    @PostMapping("/categories")
    @Operation(summary = "新增餐饮分类")
    public Result<FoodCategoryResponse> createCategory(@Valid @RequestBody FoodCategoryRequest request) {
        return Result.ok(foodCategoryService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "修改餐饮分类")
    public Result<FoodCategoryResponse> updateCategory(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody FoodCategoryRequest request
    ) {
        return foodCategoryService.updateCategory(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<FoodCategoryResponse>fail().code(404).message("分类不存在"));
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "删除餐饮分类")
    public Result<Void> deleteCategory(@PathVariable("id") @NotNull Long id) {
        return foodCategoryService.deleteCategory(id)
            ? Result.ok()
            : Result.<Void>fail().code(404).message("分类不存在");
    }
}
