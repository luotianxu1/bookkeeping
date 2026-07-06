package com.example.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalarySettingsRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "月度税前工资不能为空")
    @DecimalMin(value = "0.00", message = "月度税前工资不能小于0")
    private BigDecimal monthlyGrossSalary;

    @NotNull(message = "交通补贴不能为空")
    @DecimalMin(value = "0.00", message = "交通补贴不能小于0")
    private BigDecimal transportSubsidy;

    @NotNull(message = "餐补不能为空")
    @DecimalMin(value = "0.00", message = "餐补不能小于0")
    private BigDecimal mealSubsidy;

    @NotNull(message = "年内累计奖金不能为空")
    @DecimalMin(value = "0.00", message = "年内累计奖金不能小于0")
    private BigDecimal annualBonus;

    @NotNull(message = "发薪日不能为空")
    @Min(value = 1, message = "发薪日必须在1到31之间")
    @Max(value = 31, message = "发薪日必须在1到31之间")
    private Integer payDay;

    @NotNull(message = "社保基数不能为空")
    @DecimalMin(value = "0.00", message = "社保基数不能小于0")
    private BigDecimal socialSecurityBase;

    @NotNull(message = "公积金基数不能为空")
    @DecimalMin(value = "0.00", message = "公积金基数不能小于0")
    private BigDecimal housingFundBase;

    @NotNull(message = "公积金个人比例不能为空")
    @DecimalMin(value = "0.00", message = "公积金个人比例不能小于0")
    private BigDecimal housingFundPersonalRate;

    @NotNull(message = "公积金单位比例不能为空")
    @DecimalMin(value = "0.00", message = "公积金单位比例不能小于0")
    private BigDecimal housingFundCompanyRate;

    @NotNull(message = "养老个人比例不能为空")
    @DecimalMin(value = "0.00", message = "养老个人比例不能小于0")
    private BigDecimal pensionPersonalRate;

    @NotNull(message = "养老单位比例不能为空")
    @DecimalMin(value = "0.00", message = "养老单位比例不能小于0")
    private BigDecimal pensionCompanyRate;

    @NotNull(message = "医保个人比例不能为空")
    @DecimalMin(value = "0.00", message = "医保个人比例不能小于0")
    private BigDecimal medicalPersonalRate;

    @NotNull(message = "医保单位比例不能为空")
    @DecimalMin(value = "0.00", message = "医保单位比例不能小于0")
    private BigDecimal medicalCompanyRate;

    @NotNull(message = "医保固定金额不能为空")
    @DecimalMin(value = "0.00", message = "医保固定金额不能小于0")
    private BigDecimal medicalFixedAmount;

    @NotNull(message = "失业个人比例不能为空")
    @DecimalMin(value = "0.00", message = "失业个人比例不能小于0")
    private BigDecimal unemploymentPersonalRate;

    @NotNull(message = "失业单位比例不能为空")
    @DecimalMin(value = "0.00", message = "失业单位比例不能小于0")
    private BigDecimal unemploymentCompanyRate;

    @NotNull(message = "个税起征点不能为空")
    @DecimalMin(value = "0.00", message = "个税起征点不能小于0")
    private BigDecimal taxFreeThreshold;

    @NotNull(message = "专项附加扣除年度不能为空")
    @Min(value = 2000, message = "纳税年度不正确")
    private Integer taxYear;

    @NotNull(message = "子女教育扣除不能为空")
    @DecimalMin(value = "0.00", message = "子女教育扣除不能小于0")
    private BigDecimal childEducation;

    @NotNull(message = "继续教育扣除不能为空")
    @DecimalMin(value = "0.00", message = "继续教育扣除不能小于0")
    private BigDecimal continuingEducation;

    @NotNull(message = "住房贷款扣除不能为空")
    @DecimalMin(value = "0.00", message = "住房贷款扣除不能小于0")
    private BigDecimal housingLoan;

    @NotNull(message = "住房租金扣除不能为空")
    @DecimalMin(value = "0.00", message = "住房租金扣除不能小于0")
    private BigDecimal housingRent;

    @NotNull(message = "赡养老人扣除不能为空")
    @DecimalMin(value = "0.00", message = "赡养老人扣除不能小于0")
    private BigDecimal elderlyCare;

    @NotNull(message = "大病医疗扣除不能为空")
    @DecimalMin(value = "0.00", message = "大病医疗扣除不能小于0")
    private BigDecimal seriousMedical;

    @NotNull(message = "其他扣除不能为空")
    @DecimalMin(value = "0.00", message = "其他扣除不能小于0")
    private BigDecimal otherDeduction;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
