package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.entity.PhotographyOrderEntity;
import com.example.tool.mapper.PhotographyOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 摄影订单账户引用统计：服务合并前经 HTTP 调 tool-service 内部接口，
 * 合并后直接查本地 mapper。调用失败按 0 处理（与原 fail-open 行为一致），仅记录告警。
 */
@Component
public class ToolServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ToolServiceClient.class);

    private final PhotographyOrderMapper photographyOrderMapper;

    public ToolServiceClient(PhotographyOrderMapper photographyOrderMapper) {
        this.photographyOrderMapper = photographyOrderMapper;
    }

    /** 统计引用该现金账户的摄影订单数；查询失败返回 0（fail-open） */
    public long countPhotographyOrderAccountReferences(Long accountId) {
        if (accountId == null) {
            return 0L;
        }
        try {
            Long count = photographyOrderMapper.selectCount(new LambdaQueryWrapper<PhotographyOrderEntity>()
                .eq(PhotographyOrderEntity::getDepositAccountId, accountId)
                .or()
                .eq(PhotographyOrderEntity::getFinalAccountId, accountId));
            return count == null ? 0L : count;
        } catch (Exception ex) {
            log.warn("查询摄影订单账户引用失败（fail-open 返回 0）: accountId={}", accountId, ex);
            return 0L;
        }
    }
}
