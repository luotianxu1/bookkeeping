package com.example.app.dto.internal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** tool-service 内部调用：摄影收入入账请求 */
public record PhotographyIncomeRequest(
    Long userId,
    Long accountId,
    BigDecimal amount,
    LocalDateTime occurredAt,
    String title,
    String remark
) {
}
