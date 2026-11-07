package com.example.app.dto;

import java.math.BigDecimal;

/** 金额范围按绝对值匹配，汇总仍使用原始收支符号。 */
public record TransactionSearch(String keyword, Long categoryId, BigDecimal minAmount, BigDecimal maxAmount) {
    public TransactionSearch {
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.length() > 100) throw new IllegalArgumentException("搜索内容最多100字");
        if (categoryId != null && categoryId <= 0) throw new IllegalArgumentException("分类无效");
        for (BigDecimal value : new BigDecimal[]{minAmount, maxAmount}) {
            if (value != null && (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 16))
                throw new IllegalArgumentException("金额范围须为非负数，最多两位小数");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0)
            throw new IllegalArgumentException("最低金额不能大于最高金额");
    }
}
