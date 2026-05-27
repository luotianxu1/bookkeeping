package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AnniversaryResponse {

    private Long id;
    private Long userId;
    private String title;
    private LocalDate anniversaryDate;
    private String remark;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
