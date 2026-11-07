package com.example.app.controller;
import com.example.app.dto.*;
import com.example.app.service.CategoryBudgetService;
import com.example.common.result.Result;
import com.example.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
@RestController
@RequestMapping("/api/finance/category-budgets")
public class CategoryBudgetController {
    private final CategoryBudgetService service;
    public CategoryBudgetController(CategoryBudgetService service) { this.service = service; }
    @GetMapping
    public Result<List<CategoryBudgetResponse>> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return Result.ok(service.list(SecurityUtils.requireCurrentUserId(), month));
    }
    @PutMapping
    public Result<Void> save(@Valid @RequestBody CategoryBudgetRequest request) {
        service.save(SecurityUtils.requireCurrentUserId(), request); return Result.ok();
    }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        service.delete(SecurityUtils.requireCurrentUserId(), id); return Result.ok();
    }
}
