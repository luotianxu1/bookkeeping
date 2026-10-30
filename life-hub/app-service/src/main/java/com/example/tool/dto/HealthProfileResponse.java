package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HealthProfileResponse {

    private Long id;
    private Long userId;
    private Integer gender;
    private Integer age;
    private Double heightCm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
