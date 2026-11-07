package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.app.entity.InvestmentPositionEntity;

public interface InvestmentPositionMapper extends BaseMapper<InvestmentPositionEntity> {
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(CASE WHEN #{goldPrice} IS NOT NULL THEN holding_quantity * #{goldPrice} "
        + "WHEN subscription_status='pending' THEN COALESCE(cost_amount,0) ELSE COALESCE(market_value,0) END),0) "
        + "FROM investment_positions WHERE user_id=#{userId} AND account_id=#{accountId} AND status='active'")
    java.math.BigDecimal sumAccountValue(@org.apache.ibatis.annotations.Param("userId") Long userId,
        @org.apache.ibatis.annotations.Param("accountId") Long accountId,
        @org.apache.ibatis.annotations.Param("goldPrice") java.math.BigDecimal goldPrice);

}
