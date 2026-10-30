package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TravelPlanExpenseResponse {

    private Long id;
    private Long travelPlanId;
    private Long travelPlanDayId;
    private String type;
    private String title;
    private BigDecimal amount;
    private Long payerContactId;
    private String payerContactName;
    private String remark;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
