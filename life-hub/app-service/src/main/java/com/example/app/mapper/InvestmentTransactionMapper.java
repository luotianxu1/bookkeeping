package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.app.entity.InvestmentTransactionEntity;

public interface InvestmentTransactionMapper extends BaseMapper<InvestmentTransactionEntity> {
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(COALESCE(amount,0)+COALESCE(fee_amount,0)+COALESCE(tax_amount,0)),0) "
        + "FROM investment_transactions WHERE user_id=#{userId} AND account_id=#{accountId} AND status='normal' "
        + "AND settlement_status='pending' AND trade_type='buy' AND (remark IS NULL OR remark != #{initialRemark})")
    java.math.BigDecimal sumPendingBuyAmount(@org.apache.ibatis.annotations.Param("userId") Long userId,
        @org.apache.ibatis.annotations.Param("accountId") Long accountId,
        @org.apache.ibatis.annotations.Param("initialRemark") String initialRemark);

}
