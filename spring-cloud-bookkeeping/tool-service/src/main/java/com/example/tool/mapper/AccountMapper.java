package com.example.tool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.tool.entity.AccountEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface AccountMapper extends BaseMapper<AccountEntity> {

    /** 无条件加余额，返回受影响行数 */
    @Update("UPDATE accounts SET current_balance = current_balance + #{delta} WHERE id = #{id}")
    int addBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /** 余额充足时扣减，余额不足返回 0；数据库层原子完成判断与扣减 */
    @Update("UPDATE accounts SET current_balance = current_balance - #{amount} "
        + "WHERE id = #{id} AND current_balance >= #{amount}")
    int subtractBalanceIfSufficient(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
