package com.example.app.controller;

import com.example.app.dto.TermDepositDtos.*;
import com.example.app.service.TermDepositService;
import com.example.common.result.Result;
import com.example.common.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/finance/term-deposits")
public class TermDepositController {
    private final TermDepositService service;
    public TermDepositController(TermDepositService service) { this.service=service; }
    @GetMapping public Result<List<View>> list(@RequestParam(value="accountId",required=false) Long accountId) { return Result.ok(service.list(user(),accountId)); }
    @GetMapping("/{id}") public Result<Detail> detail(@PathVariable("id") long id) { return Result.ok(service.detail(user(),id)); }
    @PostMapping public Result<View> create(@RequestBody Create request) { return Result.ok(service.create(user(),request)); }
    @PutMapping("/{id}") public Result<View> edit(@PathVariable("id") long id,@RequestBody Edit request) { return Result.ok(service.edit(user(),id,request)); }
    @PostMapping("/{id}/settle") public Result<View> settle(@PathVariable("id") long id,@RequestBody Settle request) { return Result.ok(service.settle(user(),id,request)); }
    @PostMapping("/{id}/reverse") public Result<View> reverse(@PathVariable("id") long id,@RequestBody Reverse request) { return Result.ok(service.reverse(user(),id,request)); }
    @PostMapping("/{id}/cancel") public Result<View> cancel(@PathVariable("id") long id,@RequestBody Cancel request) { return Result.ok(service.cancel(user(),id,request)); }
    private long user() { return SecurityUtils.requireCurrentUserId(); }
}
