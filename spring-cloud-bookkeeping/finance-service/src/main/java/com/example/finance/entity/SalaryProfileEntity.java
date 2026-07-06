package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("salary_profiles")
public class SalaryProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("monthly_gross_salary")
    private BigDecimal monthlyGrossSalary;

    @TableField("transport_subsidy")
    private BigDecimal transportSubsidy;

    @TableField("meal_subsidy")
    private BigDecimal mealSubsidy;

    @TableField("annual_bonus")
    private BigDecimal annualBonus;

    @TableField("pay_day")
    private Integer payDay;

    @TableField("social_security_base")
    private BigDecimal socialSecurityBase;

    @TableField("housing_fund_base")
    private BigDecimal housingFundBase;

    @TableField("housing_fund_personal_rate")
    private BigDecimal housingFundPersonalRate;

    @TableField("housing_fund_company_rate")
    private BigDecimal housingFundCompanyRate;

    @TableField("pension_personal_rate")
    private BigDecimal pensionPersonalRate;

    @TableField("pension_company_rate")
    private BigDecimal pensionCompanyRate;

    @TableField("medical_personal_rate")
    private BigDecimal medicalPersonalRate;

    @TableField("medical_company_rate")
    private BigDecimal medicalCompanyRate;

    @TableField("medical_fixed_amount")
    private BigDecimal medicalFixedAmount;

    @TableField("unemployment_personal_rate")
    private BigDecimal unemploymentPersonalRate;

    @TableField("unemployment_company_rate")
    private BigDecimal unemploymentCompanyRate;

    @TableField("tax_free_threshold")
    private BigDecimal taxFreeThreshold;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
