package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.app.entity.AccountEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

public interface AccountMapper extends BaseMapper<AccountEntity> {

    @Select("SELECT COALESCE(SUM(principal),0) FROM term_deposits WHERE account_id=#{id} AND status='active'")
    BigDecimal termDepositPrincipal(Long id);

    @Select("SELECT COUNT(*) FROM term_deposits WHERE account_id=#{id} OR funding_account_id=#{id} OR payout_account_id=#{id}")
    long termDepositReferences(Long id);

    @Select("SELECT COUNT(*) FROM term_deposit_operations WHERE cash_account_id=#{id}")
    long termOperationReferences(Long id);

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

    @Select("SELECT COALESCE(SUM(CASE WHEN e.type='deposit' THEN e.amount ELSE -e.amount END),0) "
        + "FROM savings_goals g JOIN savings_goal_entries e ON e.goal_id=g.id WHERE g.account_id=#{id}")
    BigDecimal savingsReserved(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM savings_goal_entries e JOIN transactions t ON t.id=e.transaction_id "
        + "WHERE t.account_id=#{id} OR t.from_account_id=#{id} OR t.to_account_id=#{id}")
    long savingsLinkedTransactions(@Param("id") Long id);

    /** 调用方已持有用户账务锁；手动调账和目标分配也使用同一预留金额校验。 */
    default void requireSavingsCovered(Long id, BigDecimal balanceAfter) {
        BigDecimal reserved = savingsReserved(id);
        if (reserved.signum() > 0 && balanceAfter.compareTo(reserved) < 0) {
            throw new IllegalArgumentException("账户余额不能低于储蓄目标已存总额，还需从目标释放 "
                + reserved.subtract(balanceAfter).setScale(2, java.math.RoundingMode.UP).toPlainString());
        }
    }

    default int addBalance(Long id, BigDecimal delta) {
        return delta.signum() < 0 ? subtractBalanceIfSufficient(id, delta.negate()) : increaseBalance(id, delta);
    }

    @Update("UPDATE accounts SET current_balance = current_balance + #{delta} WHERE id = #{id}")
    int increaseBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /** 所有扣款入口统一保护储蓄预留，判断和扣减在数据库原子完成。 */
    @Update("UPDATE accounts SET current_balance = current_balance - #{amount} "
        + "WHERE id = #{id} AND #{amount} >= 0 AND current_balance >= #{amount} "
        + "AND current_balance - #{amount} >= (SELECT COALESCE(SUM(CASE WHEN e.type='deposit' THEN e.amount ELSE -e.amount END),0) "
        + "FROM savings_goals g JOIN savings_goal_entries e ON e.goal_id=g.id WHERE g.account_id=accounts.id)")
    int deductAvailableBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    default int subtractBalanceIfSufficient(Long id, BigDecimal amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("扣款金额不能小于0");
        int updated = deductAvailableBalance(id, amount);
        if (updated == 0) {
            var account = selectById(id);
            if (account != null) requireSavingsCovered(id, account.getCurrentBalance().subtract(amount));
        }
        return updated;
    }
}
