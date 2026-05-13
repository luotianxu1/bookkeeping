package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.AccountRequest;
import com.example.finance.dto.AccountResponse;
import com.example.finance.service.AccountService;
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
@RequestMapping("/api/finance/accounts")
@Tag(name = "账户", description = "账户增删改查接口")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "查询账户列表")
    public Result<List<AccountResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "accountTypeId", required = false) Long accountTypeId,
        @RequestParam(name = "status", required = false) String status
    ) {
        return Result.ok(accountService.list(userId, accountTypeId, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询账户详情")
    public Result<AccountResponse> detail(@PathVariable("id") @NotNull Long id) {
        return accountService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<AccountResponse>fail().code(404).message("账户不存在"));
    }

    @PostMapping
    @Operation(summary = "新增账户")
    public Result<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        return Result.ok(accountService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改账户")
    public Result<AccountResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody AccountRequest request
    ) {
        return accountService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<AccountResponse>fail().code(404).message("账户不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除账户")
    public Result<Void> delete(@PathVariable("id") @NotNull Long id) {
        if (!accountService.delete(id)) {
            return Result.<Void>fail().code(404).message("账户不存在");
        }
        return Result.ok();
    }
}
