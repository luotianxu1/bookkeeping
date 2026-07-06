package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("salary_account_records")
public class SalaryAccountRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_type")
    private String accountType;

    @TableField("record_type")
    private String recordType;

    @TableField("record_month")
    private LocalDate recordMonth;

    private BigDecimal amount;

    @TableField("personal_amount")
    private BigDecimal personalAmount;

    @TableField("company_amount")
    private BigDecimal companyAmount;

    @TableField("balance_after")
    private BigDecimal balanceAfter;

    @TableField("sync_to_current")
    private Boolean syncToCurrent;

    private String note;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
