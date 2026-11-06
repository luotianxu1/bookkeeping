package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.app.entity.AccountEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

public interface AccountMapper extends BaseMapper<AccountEntity> {

    @Select("SELECT id FROM users WHERE id = #{userId} FOR UPDATE")
    Long lockUser(@Param("userId") Long userId);

    /** 同一用户的账务写入串行；锁由数据库事务释放，可跨线程、跨服务实例生效。 */
    default void lockUserForWrite(Long userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("账务写入必须在事务中执行");
        }
        if (userId == null || lockUser(userId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    /** 无条件加余额（收入/回滚支出），返回受影响行数 */
    @Update("UPDATE accounts SET current_balance = current_balance + #{delta} WHERE id = #{id}")
    int addBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /** 余额充足时扣减（支出/回滚收入），余额不足返回 0；数据库层原子完成判断与扣减 */
    @Update("UPDATE accounts SET current_balance = current_balance - #{amount} "
        + "WHERE id = #{id} AND current_balance >= #{amount}")
    int subtractBalanceIfSufficient(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
