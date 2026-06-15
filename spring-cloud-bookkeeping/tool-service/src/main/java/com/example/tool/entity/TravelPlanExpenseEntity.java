package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("travel_plan_expenses")
public class TravelPlanExpenseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("travel_plan_id")
    private Long travelPlanId;

    @TableField("travel_plan_day_id")
    private Long travelPlanDayId;

    private String type;
    private String title;
    private BigDecimal amount;

    @TableField("payer_contact_id")
    private Long payerContactId;

    private String remark;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
