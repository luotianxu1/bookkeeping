package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.AccountTypeRequest;
import com.example.finance.dto.AccountTypeResponse;
import com.example.finance.dto.AccountTypeSortOrderRequest;
import com.example.finance.service.AccountTypeService;
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
@RequestMapping("/api/finance/account-types")
@Tag(name = "账户类型", description = "账户类型增删改查接口")
public class AccountTypeController {

    private final AccountTypeService accountTypeService;

    public AccountTypeController(AccountTypeService accountTypeService) {
        this.accountTypeService = accountTypeService;
    }

    @GetMapping
    @Operation(summary = "查询账户类型列表")
    public Result<List<AccountTypeResponse>> list(
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "status", required = false) String status
    ) {
        return Result.ok(accountTypeService.list(category, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询账户类型详情")
    public Result<AccountTypeResponse> detail(@PathVariable("id") @NotNull Long id) {
        return accountTypeService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<AccountTypeResponse>fail().code(404).message("账户类型不存在"));
    }

    @PostMapping
    @Operation(summary = "新增账户类型")
    public Result<AccountTypeResponse> create(@Valid @RequestBody AccountTypeRequest request) {
        return Result.ok(accountTypeService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改账户类型")
    public Result<AccountTypeResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody AccountTypeRequest request
    ) {
        return accountTypeService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<AccountTypeResponse>fail().code(404).message("账户类型不存在"));
    }

    @PutMapping("/actions/sort-orders")
    @Operation(summary = "保存账户类型排序")
    public Result<Void> updateSortOrders(@Valid @RequestBody AccountTypeSortOrderRequest request) {
        accountTypeService.updateSortOrders(request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除账户类型")
    public Result<Void> delete(@PathVariable("id") @NotNull Long id) {
        if (!accountTypeService.delete(id)) {
            return Result.<Void>fail().code(404).message("账户类型不存在");
        }
        return Result.ok();
    }
}
