package com.example.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccountTypeResponse {

    private Long id;
    private String code;
    private String name;
    private String category;
    private String balanceDirection;
    private Boolean includeInNetWorthDefault;
    private Boolean allowOverdraft;
    private Boolean system;
    private Integer sortOrder;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
