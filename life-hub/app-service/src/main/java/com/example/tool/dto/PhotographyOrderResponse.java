package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PhotographyOrderResponse {

    private Long id;
    private String orderNo;
    private Long userId;
    private String contactInfo;
    private String orderType;
    private String status;
    private LocalDateTime shootAt;
    private BigDecimal totalAmount;
    private BigDecimal depositAmount;
    private BigDecimal finalAmount;
    private Long depositAccountId;
    private String depositAccountName;
    private Long depositTransactionId;
    private LocalDateTime depositReceivedAt;
    private Long finalAccountId;
    private String finalAccountName;
    private Long finalTransactionId;
    private LocalDateTime finalReceivedAt;
    private String address;
    private String remark;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
