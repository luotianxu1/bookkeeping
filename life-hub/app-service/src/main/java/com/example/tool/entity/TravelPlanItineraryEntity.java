package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("travel_plan_itineraries")
public class TravelPlanItineraryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("travel_plan_day_id")
    private Long travelPlanDayId;

    private String type;
    private String title;

    // 允许清空地点名称等可选字段：默认 NOT_NULL 策略会把 null 字段从 UPDATE 的 SET 中剔除
    @TableField(value = "poi_name", updateStrategy = FieldStrategy.ALWAYS)
    private String poiName;

    @TableField(value = "poi_id", updateStrategy = FieldStrategy.ALWAYS)
    private String poiId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal longitude;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal latitude;

    @TableField(value = "start_time", updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime startTime;

    @TableField(value = "transport_mode", updateStrategy = FieldStrategy.ALWAYS)
    private String transportMode;

    @TableField(value = "distance_meters", updateStrategy = FieldStrategy.ALWAYS)
    private Integer distanceMeters;

    @TableField(value = "duration_seconds", updateStrategy = FieldStrategy.ALWAYS)
    private Integer durationSeconds;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
