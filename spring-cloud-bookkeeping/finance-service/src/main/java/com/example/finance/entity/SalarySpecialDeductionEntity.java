package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("salary_special_deductions")
public class SalarySpecialDeductionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("tax_year")
    private Integer taxYear;

    @TableField("child_education")
    private BigDecimal childEducation;

    @TableField("continuing_education")
    private BigDecimal continuingEducation;

    @TableField("housing_loan")
    private BigDecimal housingLoan;

    @TableField("housing_rent")
    private BigDecimal housingRent;

    @TableField("elderly_care")
    private BigDecimal elderlyCare;

    @TableField("serious_medical")
    private BigDecimal seriousMedical;

    @TableField("other_deduction")
    private BigDecimal otherDeduction;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
