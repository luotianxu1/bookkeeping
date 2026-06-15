package com.example.tool.entity;

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

    @TableField("poi_name")
    private String poiName;

    @TableField("poi_id")
    private String poiId;

    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;

    @TableField("start_time")
    private LocalTime startTime;

    @TableField("transport_mode")
    private String transportMode;

    @TableField("distance_meters")
    private Integer distanceMeters;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    private String remark;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
