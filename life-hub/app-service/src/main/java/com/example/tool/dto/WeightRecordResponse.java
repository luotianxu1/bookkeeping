package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class WeightRecordResponse {

    private Long id;
    private Long userId;
    private LocalDate recordDate;
    private Double weight;
    // 体脂率(%)，未设置健康档案时为空
    private Double bodyFatRate;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
