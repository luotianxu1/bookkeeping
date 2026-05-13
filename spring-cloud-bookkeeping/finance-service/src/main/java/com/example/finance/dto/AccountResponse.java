package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AccountResponse {

    private Long id;
    private Long userId;
    private Long accountTypeId;
    private String accountTypeCode;
    private String accountTypeName;
    private String name;
    private String icon;
    private String color;
    private String currencyCode;
    private BigDecimal currentBalance;
    private Boolean includeInNetWorth;
    private Integer sortOrder;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
