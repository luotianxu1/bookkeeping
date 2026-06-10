package com.example.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestmentDividendForecastRequest {

    @NotBlank(message = "产品类型不能为空")
    private String productType;

    @NotBlank(message = "产品代码不能为空")
    @Size(max = 64, message = "产品代码不能超过64个字符")
    private String symbol;

    @Size(max = 120, message = "产品名称不能超过120个字符")
    private String name;

    @Size(max = 32, message = "市场不能超过32个字符")
    private String market;

    @Size(max = 32, message = "交易所编码不能超过32个字符")
    private String exchangeCode;

    @Size(min = 3, max = 3, message = "币种编码必须为3个字符")
    private String currencyCode;

    @Size(max = 16, message = "单位名称不能超过16个字符")
    private String unitName;

    private BigDecimal latestPrice;
    private BigDecimal holdingQuantity;
    private BigDecimal holdingAmount;
}
