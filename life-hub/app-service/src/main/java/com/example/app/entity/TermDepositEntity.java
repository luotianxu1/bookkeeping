package com.example.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;

@Data
@TableName("term_deposits")
public class TermDepositEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long accountId;
    private Long fundingAccountId;
    private Long payoutAccountId;
    private Long parentDepositId;
    private String name;
    private String currencyCode;
    private BigDecimal principal;
    private BigDecimal annualRate;
    private LocalDate startDate;
    private LocalDate maturityDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer termMonths;
    private String maturityAction;
    private String openingMode;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
