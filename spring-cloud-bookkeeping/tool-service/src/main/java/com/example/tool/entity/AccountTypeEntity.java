package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("account_types")
public class AccountTypeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String category;

    @TableField("balance_direction")
    private String balanceDirection;

    @TableField("include_in_net_worth_default")
    private Boolean includeInNetWorthDefault;

    @TableField("allow_overdraft")
    private Boolean allowOverdraft;

    @TableField("is_system")
    private Boolean system;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;
    private String remark;
}
