package com.example.tool.client;

import com.example.app.dto.internal.PhotographyIncomeRequest;
import com.example.app.entity.AccountEntity;
import com.example.app.service.PhotographyAccountingService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资金操作门面：服务合并前经 HTTP 调 finance-service 内部接口，
 * 合并后同一 JVM 内直接委托 PhotographyAccountingService。
 * 错误处理语义不变——业务错误原样抛给调用方。
 */
@Component
public class FinanceClient {

    public record CashAccountView(Long id, String name) {
    }

    private final PhotographyAccountingService photographyAccountingService;

    public FinanceClient(PhotographyAccountingService photographyAccountingService) {
        this.photographyAccountingService = photographyAccountingService;
    }

    /** 校验现金账户（归属+现金类型+可用），失败抛 IllegalArgumentException */
    public CashAccountView requireCashAccount(Long userId, Long accountId) {
        AccountEntity account = photographyAccountingService.requireCashAccount(userId, accountId);
        return new CashAccountView(account.getId(), account.getName());
    }

    /** 批量获取账户名称（展示用），查询失败返回空 map（不影响订单列表展示） */
    public Map<Long, String> loadAccountNames(Long userId, Set<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, String> names = new LinkedHashMap<>();
            for (PhotographyAccountingService.CashAccountView view
                : photographyAccountingService.loadCashAccounts(userId, List.copyOf(accountIds))) {
                names.put(view.id(), view.name());
            }
            return names;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /** 创建摄影收入流水并原子加余额，返回流水 id */
    public Long createPhotographyIncome(
        Long userId,
        Long accountId,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String title,
        String remark
    ) {
        PhotographyIncomeRequest request = new PhotographyIncomeRequest(
            userId, accountId, amount, occurredAt, title, remark
        );
        return photographyAccountingService.createPhotographyIncome(request);
    }

    /** 作废关联收入流水并原子扣回余额 */
    public void voidPhotographyIncome(Long transactionId, Long userId) {
        photographyAccountingService.voidPhotographyIncome(transactionId, userId);
    }
}
