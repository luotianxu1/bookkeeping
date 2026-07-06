package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalaryAccountRecordRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "记录金额不能为空")
    @DecimalMin(value = "0.00", message = "记录金额不能小于0")
    private BigDecimal amount;

    @NotNull(message = "发生月份不能为空")
    private LocalDate recordMonth;

    @Size(max = 16, message = "记录类型不能超过16个字符")
    private String recordType;

    @Size(max = 32, message = "影响方式不能超过32个字符")
    private String impactMode;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String note;
}
