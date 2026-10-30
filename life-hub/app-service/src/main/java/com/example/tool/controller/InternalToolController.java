package com.example.tool.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.result.Result;
import com.example.tool.entity.PhotographyOrderEntity;
import com.example.tool.mapper.PhotographyOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 仅供内部服务调用的接口（X-Internal-Key 鉴权，见 InternalApiKeyFilter）。
 * finance-service（现 app-service）删除账户前通过这里统计摄影订单的账户引用，替代跨服务直查表。
 */
@RestController
@RequestMapping("/api/internal/tool")
@Tag(name = "内部-摄影订单", description = "服务间调用，外部前端禁止访问")
public class InternalToolController {

    private final PhotographyOrderMapper photographyOrderMapper;

    public InternalToolController(PhotographyOrderMapper photographyOrderMapper) {
        this.photographyOrderMapper = photographyOrderMapper;
    }

    @GetMapping("/photography-orders/account-references")
    @Operation(summary = "统计引用指定现金账户的摄影订单数")
    public Result<Map<String, Long>> accountReferences(@RequestParam("accountId") Long accountId) {
        if (accountId == null) {
            return Result.ok(Map.of("count", 0L));
        }
        Long count = photographyOrderMapper.selectCount(new LambdaQueryWrapper<PhotographyOrderEntity>()
            .eq(PhotographyOrderEntity::getDepositAccountId, accountId)
            .or()
            .eq(PhotographyOrderEntity::getFinalAccountId, accountId));
        return Result.ok(Map.of("count", count == null ? 0L : count));
    }
}
