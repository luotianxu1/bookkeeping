package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.TodoItemRequest;
import com.example.tool.dto.TodoItemResponse;
import com.example.tool.dto.TodoItemStatusRequest;
import com.example.tool.service.TodoItemService;
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
@RequestMapping("/api/tools/todo-items")
@Tag(name = "待办事项", description = "待办事项增删改查接口")
public class TodoItemController {

    private final TodoItemService todoItemService;

    public TodoItemController(TodoItemService todoItemService) {
        this.todoItemService = todoItemService;
    }

    @GetMapping
    @Operation(summary = "查询待办事项列表")
    public Result<List<TodoItemResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "dueScope", required = false) String dueScope,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(todoItemService.list(userId, status, dueScope, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询待办事项详情")
    public Result<TodoItemResponse> detail(@PathVariable("id") @NotNull Long id) {
        return todoItemService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<TodoItemResponse>fail().code(404).message("待办事项不存在"));
    }

    @PostMapping
    @Operation(summary = "新增待办事项")
    public Result<TodoItemResponse> create(@Valid @RequestBody TodoItemRequest request) {
        return Result.ok(todoItemService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改待办事项")
    public Result<TodoItemResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody TodoItemRequest request
    ) {
        return todoItemService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<TodoItemResponse>fail().code(404).message("待办事项不存在"));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新待办事项状态")
    public Result<TodoItemResponse> updateStatus(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody TodoItemStatusRequest request
    ) {
        return todoItemService.updateStatus(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<TodoItemResponse>fail().code(404).message("待办事项不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除待办事项")
    public Result<Void> delete(
        @PathVariable("id") @NotNull Long id,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!todoItemService.delete(id, userId)) {
            return Result.<Void>fail().code(404).message("待办事项不存在");
        }
        return Result.ok();
    }
}
