package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.app.entity.InvestmentProductEntity;
import org.apache.ibatis.annotations.Insert;

public interface InvestmentProductMapper extends BaseMapper<InvestmentProductEntity> {
    /** 由归一化唯一索引裁决并发；已有产品的名称、行情和分红数据均保持原值。 */
    @Insert("""
        INSERT INTO investment_products(product_type, market, exchange_code, symbol, name, short_name,
            currency_code, unit_name, price_precision, is_stable_dividend, predicted_annual_dividend_per_unit,
            dividend_stable_years, status, remark)
        VALUES(#{productType}, #{market}, #{exchangeCode}, #{symbol}, #{name}, #{shortName},
            #{currencyCode}, #{unitName}, #{pricePrecision}, #{stableDividend}, #{predictedAnnualDividendPerUnit},
            #{dividendStableYears}, #{status}, #{remark})
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertIfAbsent(InvestmentProductEntity product);
}
