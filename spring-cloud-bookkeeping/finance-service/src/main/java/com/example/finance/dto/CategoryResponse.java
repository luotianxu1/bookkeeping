package com.example.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryResponse {

    private Long id;
    private Long userId;
    private String name;
    private String type;
    private String icon;
    private String color;
    private Boolean system;
    private Integer sortOrder;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
