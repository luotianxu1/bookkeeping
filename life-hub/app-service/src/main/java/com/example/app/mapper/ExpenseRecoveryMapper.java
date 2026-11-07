package com.example.app.mapper;

import com.example.app.dto.ExpenseRecoveryResponse;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface ExpenseRecoveryMapper {
    String BASE = """
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        LEFT JOIN (
          SELECT original_transaction_id, user_id,
            SUM(CASE WHEN recovery_kind = 'refund' THEN -amount ELSE 0 END) AS refunded,
            SUM(CASE WHEN recovery_kind = 'reimbursement' THEN -amount ELSE 0 END) AS reimbursed
          FROM transactions WHERE status = 'normal' AND original_transaction_id IS NOT NULL AND user_id = #{userId}
          GROUP BY original_transaction_id, user_id
        ) r ON r.original_transaction_id = t.id AND r.user_id = t.user_id
        WHERE t.user_id = #{userId} AND t.type = 'expense' AND t.status = 'normal' AND t.original_transaction_id IS NULL
        <if test="transactionId != null">AND t.id = #{transactionId}</if>
        <if test="state == 'pending'">AND t.reimbursement_amount > 0 AND COALESCE(r.reimbursed,0) = 0</if>
        <if test="state == 'partial'">AND COALESCE(r.reimbursed,0) > 0 AND r.reimbursed &lt; t.reimbursement_amount</if>
        <if test="state == 'received'">AND t.reimbursement_amount > 0 AND COALESCE(r.reimbursed,0) &gt;= t.reimbursement_amount</if>
        """;
    @Select("<script>SELECT COUNT(*) " + BASE + "</script>")
    long count(@Param("userId") long userId, @Param("state") String state, @Param("transactionId") Long transactionId);

    @Select("""
        <script>SELECT t.id, t.title, c.name AS category_name, a.name AS account_name, t.currency_code,
        t.occurred_at, t.amount, COALESCE(t.reimbursement_amount,0) AS reimbursement_amount,
        COALESCE(r.refunded,0) AS refunded_amount, COALESCE(r.reimbursed,0) AS reimbursed_amount
        """ + BASE + " ORDER BY t.occurred_at DESC, t.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ExpenseRecoveryResponse> list(@Param("userId") long userId, @Param("state") String state,
        @Param("transactionId") Long transactionId, @Param("offset") int offset, @Param("limit") int limit);
}
