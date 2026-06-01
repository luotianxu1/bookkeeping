package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AccountResponse {

    private Long id;
    private Long userId;
    private Long accountTypeId;
    private Long contactId;
    private String accountTypeCode;
    private String accountTypeName;
    private String name;
    private String icon;
    private String color;
    private String currencyCode;
    private BigDecimal currentBalance;
    private BigDecimal loanTotalAmount;
    private BigDecimal loanInterestRate;
    private BigDecimal loanInterestAmount;
    private Integer loanTotalPeriods;
    private Integer loanRepaymentDay;
    private LocalDate loanStartDate;
    private LocalDateTime loanSettledAt;
    private Boolean includeInNetWorth;
    private Integer sortOrder;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
