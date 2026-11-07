package com.example.app.mapper;

import com.example.app.dto.ReminderSettings;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 只读取业务状态，不创建账单、触发扣款或访问外部行情。所有查询均限定当前用户。 */
public interface ReminderMapper {
    @Data
    class Source {
        private Long id;
        private String title;
        private String detail;
        private LocalDateTime dueAt;
        private BigDecimal amount;
        private String currencyCode;
        private String targetPath;
        private boolean failed;
    }

    @Data
    class State {
        private String reminderKey;
        private LocalDateTime readAt;
        private LocalDateTime snoozedUntil;
    }

    @Select("""
        SELECT id, title, remark AS detail, due_at,
          CONCAT('/tools/todo-items?reminderId=', id) AS target_path
        FROM todo_items WHERE user_id = #{userId} AND status = 'pending' AND due_at < #{end}
        """)
    List<Source> todos(@Param("userId") long userId, @Param("end") LocalDateTime end);

    @Select("""
        SELECT id, title, remark AS detail, anniversary_date AS due_at,
          CONCAT('/tools/anniversaries?reminderId=', id) AS target_path
        FROM anniversaries WHERE user_id = #{userId}
        """)
    List<Source> anniversaries(long userId);

    @Select("""
        SELECT id, name AS title, next_billing_date AS due_at, amount, currency_code,
          last_charge_status = 'failed' AS failed, last_charge_message AS detail,
          CONCAT('/finance/renewals?reminderId=', id) AS target_path
        FROM renewal_subscriptions
        WHERE user_id = #{userId} AND status = 'active' AND next_billing_date < #{end}
        """)
    List<Source> renewals(@Param("userId") long userId, @Param("end") LocalDateTime end);

    @Select("""
        SELECT r.id, a.name AS title, r.occurred_at AS due_at, r.amount, r.currency_code,
          CONCAT('/finance/accounts/liability/', a.id) AS target_path
        FROM liability_records r JOIN accounts a ON a.id = r.account_id AND a.user_id = r.user_id
        WHERE r.user_id = #{userId} AND r.status = 'active' AND r.repayment_status = 'pending'
          AND r.repayment_type = 'monthly' AND a.status = 'active' AND a.loan_settled_at IS NULL
          AND r.occurred_at < #{end}
        """)
    List<Source> liabilities(@Param("userId") long userId, @Param("end") LocalDateTime end);

    @Select("""
        SELECT p.id, product.name AS title, p.next_execute_date AS due_at, p.amount, p.currency_code,
          CONCAT('/finance/accounts/investment/detail?positionId=', p.position_id) AS target_path
        FROM investment_auto_invest_plans p
        JOIN investment_positions pos ON pos.id = p.position_id AND pos.user_id = p.user_id
        JOIN investment_products product ON product.id = p.product_id
        JOIN accounts a ON a.id = p.account_id AND a.user_id = p.user_id
        WHERE p.user_id = #{userId} AND p.status = 'active' AND pos.status = 'active'
          AND a.status = 'active' AND p.next_execute_date < #{end}
        """)
    List<Source> investments(@Param("userId") long userId, @Param("end") LocalDateTime end);

    @Select("""
        SELECT t.id, t.title, f.recipient AS detail, f.expected_date AS due_at, t.currency_code,
          t.reimbursement_amount - COALESCE(r.received,0) AS amount,
          CONCAT('/finance/expense-recoveries?transactionId=', t.id) AS target_path
        FROM transactions t
        JOIN expense_recovery_followups f ON f.transaction_id=t.id AND f.user_id=t.user_id
        LEFT JOIN (
          SELECT original_transaction_id, user_id, SUM(-amount) AS received FROM transactions
          WHERE user_id=#{userId} AND status='normal' AND recovery_kind='reimbursement' AND original_transaction_id IS NOT NULL
          GROUP BY original_transaction_id, user_id
        ) r ON r.original_transaction_id=t.id AND r.user_id=t.user_id
        WHERE t.user_id=#{userId} AND t.type='expense' AND t.status='normal' AND t.original_transaction_id IS NULL
          AND t.reimbursement_amount > COALESCE(r.received,0) AND f.expected_date < #{end}
        """)
    List<Source> reimbursements(@Param("userId") long userId, @Param("end") LocalDateTime end);

    @Select("""
        SELECT advance_days, todo_enabled, anniversary_enabled, renewal_enabled, liability_enabled, investment_enabled, budget_enabled, reimbursement_enabled
        FROM reminder_settings WHERE user_id = #{userId}
        """)
    ReminderSettings settings(long userId);

    @Insert("""
        INSERT INTO reminder_settings
          (user_id, advance_days, todo_enabled, anniversary_enabled, renewal_enabled, liability_enabled, investment_enabled, budget_enabled, reimbursement_enabled)
        VALUES (#{userId}, #{settings.advanceDays}, #{settings.todoEnabled}, #{settings.anniversaryEnabled},
          #{settings.renewalEnabled}, #{settings.liabilityEnabled}, #{settings.investmentEnabled}, #{settings.budgetEnabled}, #{settings.reimbursementEnabled})
        ON DUPLICATE KEY UPDATE advance_days = VALUES(advance_days), todo_enabled = VALUES(todo_enabled),
          anniversary_enabled = VALUES(anniversary_enabled), renewal_enabled = VALUES(renewal_enabled),
          liability_enabled = VALUES(liability_enabled), investment_enabled = VALUES(investment_enabled), budget_enabled = VALUES(budget_enabled),
          reimbursement_enabled = VALUES(reimbursement_enabled)
        """)
    void saveSettings(@Param("userId") long userId, @Param("settings") ReminderSettings settings);

    @Select("""
        <script>
        SELECT reminder_key, read_at, snoozed_until FROM reminder_states
        WHERE user_id = #{userId} AND reminder_key IN
        <foreach collection="keys" item="key" open="(" separator="," close=")">#{key}</foreach>
        </script>
        """)
    List<State> states(@Param("userId") long userId, @Param("keys") List<String> keys);

    @Insert("""
        INSERT INTO reminder_states (user_id, reminder_key, read_at)
        VALUES (#{userId}, #{key}, #{readAt})
        ON DUPLICATE KEY UPDATE read_at = VALUES(read_at)
        """)
    void setRead(@Param("userId") long userId, @Param("key") String key, @Param("readAt") LocalDateTime readAt);

    @Insert("""
        INSERT INTO reminder_states (user_id, reminder_key, snoozed_until)
        VALUES (#{userId}, #{key}, #{until})
        ON DUPLICATE KEY UPDATE read_at = NULL, snoozed_until = VALUES(snoozed_until)
        """)
    void setSnooze(@Param("userId") long userId, @Param("key") String key, @Param("until") LocalDateTime until);
}
