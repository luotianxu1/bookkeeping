package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("accounts")
public class AccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_type_id")
    private Long accountTypeId;

    private String name;
    private String icon;
    private String color;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("current_balance")
    private BigDecimal currentBalance;

    @TableField("include_in_net_worth")
    private Boolean includeInNetWorth;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
