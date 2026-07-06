package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SalarySettingsResponse {

    private Long id;
    private Long userId;
    private BigDecimal monthlyGrossSalary;
    private BigDecimal transportSubsidy;
    private BigDecimal mealSubsidy;
    private BigDecimal annualBonus;
    private Integer payDay;
    private BigDecimal socialSecurityBase;
    private BigDecimal housingFundBase;
    private BigDecimal housingFundPersonalRate;
    private BigDecimal housingFundCompanyRate;
    private BigDecimal pensionPersonalRate;
    private BigDecimal pensionCompanyRate;
    private BigDecimal medicalPersonalRate;
    private BigDecimal medicalCompanyRate;
    private BigDecimal medicalFixedAmount;
    private BigDecimal unemploymentPersonalRate;
    private BigDecimal unemploymentCompanyRate;
    private BigDecimal taxFreeThreshold;
    private Integer taxYear;
    private BigDecimal childEducation;
    private BigDecimal continuingEducation;
    private BigDecimal housingLoan;
    private BigDecimal housingRent;
    private BigDecimal elderlyCare;
    private BigDecimal seriousMedical;
    private BigDecimal otherDeduction;
    private String remark;
    private BigDecimal monthlyTakeHome;
    private BigDecimal monthlyTax;
    private BigDecimal monthlySpecialDeductionTotal;
    private LocalDateTime updatedAt;
}
