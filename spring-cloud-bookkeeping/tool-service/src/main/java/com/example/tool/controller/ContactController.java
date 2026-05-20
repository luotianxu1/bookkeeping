package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.ContactRequest;
import com.example.tool.dto.ContactResponse;
import com.example.tool.service.ContactService;
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
@RequestMapping("/api/tools/contacts")
@Tag(name = "联系人", description = "联系人增删改查接口")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    @Operation(summary = "查询联系人列表")
    public Result<List<ContactResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(contactService.list(userId, status, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询联系人详情")
    public Result<ContactResponse> detail(@PathVariable("id") @NotNull Long id) {
        return contactService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<ContactResponse>fail().code(404).message("联系人不存在"));
    }

    @PostMapping
    @Operation(summary = "新增联系人")
    public Result<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        return Result.ok(contactService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改联系人")
    public Result<ContactResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody ContactRequest request
    ) {
        return contactService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<ContactResponse>fail().code(404).message("联系人不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除联系人")
    public Result<Void> delete(@PathVariable("id") @NotNull Long id) {
        if (!contactService.delete(id)) {
            return Result.<Void>fail().code(404).message("联系人不存在");
        }
        return Result.ok();
    }
}
