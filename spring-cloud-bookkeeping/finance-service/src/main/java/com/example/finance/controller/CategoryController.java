package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.CategoryRequest;
import com.example.finance.dto.CategoryResponse;
import com.example.finance.service.CategoryService;
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
@RequestMapping("/api/finance/categories")
@Tag(name = "分类", description = "收支分类增删改查接口")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "查询分类列表")
    public Result<List<CategoryResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "status", required = false) String status
    ) {
        return Result.ok(categoryService.list(userId, type, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询分类详情")
    public Result<CategoryResponse> detail(@PathVariable("id") @NotNull Long id) {
        return categoryService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<CategoryResponse>fail().code(404).message("分类不存在"));
    }

    @PostMapping
    @Operation(summary = "新增分类")
    public Result<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改分类")
    public Result<CategoryResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody CategoryRequest request
    ) {
        return categoryService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<CategoryResponse>fail().code(404).message("分类不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public Result<Void> delete(@PathVariable("id") @NotNull Long id) {
        if (!categoryService.delete(id)) {
            return Result.<Void>fail().code(404).message("分类不存在");
        }
        return Result.ok();
    }
}
