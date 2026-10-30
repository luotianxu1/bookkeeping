package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContactResponse {

    private Long id;
    private Long userId;
    private String name;
    private String phone;
    private String remark;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
