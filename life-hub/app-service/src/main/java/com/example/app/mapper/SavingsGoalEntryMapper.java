package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.app.entity.SavingsGoalEntryEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;

public interface SavingsGoalEntryMapper extends BaseMapper<SavingsGoalEntryEntity> {
    record Balance(Long goalId, BigDecimal amount) {}

    @Select("""
        SELECT e.goal_id, SUM(CASE WHEN e.type='deposit' THEN e.amount ELSE -e.amount END) AS amount
        FROM savings_goal_entries e JOIN savings_goals g ON g.id=e.goal_id
        WHERE g.user_id=#{userId} GROUP BY e.goal_id
        """)
    List<Balance> balances(@Param("userId") long userId);

    @Select("""
        SELECT COALESCE(SUM(CASE WHEN type='deposit' THEN amount ELSE -amount END),0)
        FROM savings_goal_entries WHERE goal_id=#{goalId}
        """)
    BigDecimal savedAmount(@Param("goalId") long goalId);
}
