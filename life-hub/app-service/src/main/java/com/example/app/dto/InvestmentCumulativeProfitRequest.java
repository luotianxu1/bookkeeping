package com.example.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 手动校准持仓累计收益请求：展示口径含浮盈，差额写入已实现收益。 */
@Data
public class InvestmentCumulativeProfitRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "累计收益不能为空")
    private BigDecimal cumulativeProfit;
}
