package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("travel_plan_days")
public class TravelPlanDayEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("travel_plan_id")
    private Long travelPlanId;

    @TableField("day_index")
    private Integer dayIndex;

    private String title;

    @TableField("travel_date")
    private LocalDate travelDate;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
