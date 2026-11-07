package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.dto.ExpenseRecoveryRequest;
import com.example.app.dto.ExpenseRecoveryResponse;
import com.example.app.dto.ExpenseRecoveryResponse.Receipt;
import com.example.app.entity.TransactionEntity;
import com.example.app.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseRecoveryService {
    private final TransactionMapper transactions;
    private final ExpenseRecoveryMapper recoveries;
    private final AccountMapper accounts;
    private final AccountTypeMapper accountTypes;
    private final AccountingIdempotencyService idempotency;
    public ExpenseRecoveryService(TransactionMapper transactions, ExpenseRecoveryMapper recoveries,
        AccountMapper accounts, AccountTypeMapper accountTypes, AccountingIdempotencyService idempotency) {
        this.transactions = transactions; this.recoveries = recoveries; this.accounts = accounts;
        this.accountTypes = accountTypes; this.idempotency = idempotency;
    }

    public ExpenseRecoveryResponse.Page list(long userId, String state, Long transactionId, int page) {
        if (!Set.of("all", "pending", "partial", "received").contains(state)) throw new IllegalArgumentException("报销状态无效");
        long total = recoveries.count(userId, state, transactionId);
        int pages = Math.max(1, (int) Math.ceil(total / 30.0));
        int current = Math.max(1, Math.min(page, pages));
        var items = recoveries.list(userId, state, transactionId, (current - 1) * 30, 30);
        items.forEach(this::complete);
        return new ExpenseRecoveryResponse.Page(items, total, current, pages);
    }

    public ExpenseRecoveryResponse detail(long userId, long id) {
        var items = recoveries.list(userId, "all", id, 0, 1);
        if (items.isEmpty()) throw new IllegalArgumentException("原支出不存在");
        var result = items.get(0);
        complete(result);
        var receipts = receipts(userId, id);
        var ids = receipts.stream().map(TransactionEntity::getAccountId).collect(Collectors.toSet());
        Map<Long,String> names = ids.isEmpty() ? Map.of() : accounts.selectByIds(ids).stream()
            .collect(Collectors.toMap(a -> a.getId(), a -> a.getName()));
        result.setReceipts(receipts.stream().map(r -> new Receipt(r.getId(), r.getRecoveryKind(), r.getAmount().negate(),
            r.getAccountId(), names.get(r.getAccountId()), r.getOccurredAt(), r.getRemark())).toList());
        return result;
    }

    @Transactional
    public ExpenseRecoveryResponse plan(long userId, long id, BigDecimal amount) {
        accounts.lockUserForWrite(userId);
        money(amount, true);
        var original = original(userId, id);
        var current = detail(userId, id);
        if (amount.compareTo(current.getReimbursedAmount()) < 0
            || amount.compareTo(original.getAmount().subtract(current.getRefundedAmount())) > 0) {
            throw new IllegalArgumentException("计划报销金额不能小于已到账金额，或超过扣除退款后的支出");
        }
        original.setReimbursementAmount(amount);
        transactions.updateById(original);
        return detail(userId, id);
    }

    @Transactional
    public Receipt receive(long userId, ExpenseRecoveryRequest request) {
        accounts.lockUserForWrite(userId);
        return idempotency.execute(userId, "expense-recovery", request.requestId(), request, Receipt.class, () -> {
            money(request.amount(), false);
            if (!Set.of("refund", "reimbursement").contains(request.kind())) throw new IllegalArgumentException("到账类型无效");
            var original = original(userId, request.transactionId());
            var current = detail(userId, request.transactionId());
            boolean refund = "refund".equals(request.kind());
            BigDecimal limit = refund ? original.getAmount().subtract(current.getRefundedAmount()).subtract(current.getReimbursementAmount())
                : current.getPendingAmount();
            if (request.amount().compareTo(limit) > 0) {
                throw new IllegalArgumentException(refund ? "退款超过可退金额，请先调整报销计划" : "到账金额超过待报销金额，请先设置报销计划");
            }
            if (request.occurredAt() == null || request.occurredAt().isBefore(original.getOccurredAt())
                || request.occurredAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("到账时间不能早于原支出或晚于现在");
            var account = accounts.selectById(request.accountId());
            if (account == null || !Objects.equals(account.getUserId(), userId) || !"active".equals(account.getStatus()))
                throw new IllegalArgumentException("到账账户不可用");
            var type = accountTypes.selectById(account.getAccountTypeId());
            if (type == null || !"cash".equals(type.getCode())) throw new IllegalArgumentException("请选择现金账户");
            if (!Objects.equals(account.getCurrencyCode(), original.getCurrencyCode())) throw new IllegalArgumentException("到账账户币种必须与原支出一致");
            var receipt = new TransactionEntity();
            receipt.setUserId(userId); receipt.setTransactionNo("RC" + UUID.randomUUID().toString().replace("-", ""));
            receipt.setType("expense"); receipt.setAmount(request.amount().negate()); receipt.setCurrencyCode(original.getCurrencyCode());
            receipt.setAccountId(account.getId()); receipt.setCategoryId(original.getCategoryId()); receipt.setStatus("normal");
            receipt.setOriginalTransactionId(original.getId()); receipt.setRecoveryKind(request.kind());
            receipt.setReimbursementAmount(BigDecimal.ZERO);
            String title = (refund ? "退款 · " : "报销到账 · ") + original.getTitle();
            receipt.setTitle(title.substring(0, Math.min(120, title.length())));
            receipt.setOccurredAt(request.occurredAt()); receipt.setRemark(request.remark());
            transactions.insert(receipt);
            if (accounts.addBalance(account.getId(), request.amount()) != 1) throw new IllegalArgumentException("到账账户不可用");
            return new Receipt(receipt.getId(), request.kind(), request.amount(), account.getId(), account.getName(), request.occurredAt(), request.remark());
        });
    }

    private TransactionEntity original(long userId, long id) {
        var t = transactions.selectById(id);
        if (t == null || !Objects.equals(t.getUserId(), userId) || !"normal".equals(t.getStatus())
            || !"expense".equals(t.getType()) || t.getOriginalTransactionId() != null)
            throw new IllegalArgumentException("原支出不存在");
        return t;
    }
    private List<TransactionEntity> receipts(long userId, long id) {
        return transactions.selectList(new LambdaQueryWrapper<TransactionEntity>().eq(TransactionEntity::getUserId, userId)
            .eq(TransactionEntity::getOriginalTransactionId, id).eq(TransactionEntity::getStatus, "normal")
            .orderByDesc(TransactionEntity::getOccurredAt).orderByDesc(TransactionEntity::getId));
    }
    private void complete(ExpenseRecoveryResponse result) {
        result.setRecoverableAmount(result.getAmount().subtract(result.getRefundedAmount()).subtract(result.getReimbursementAmount()));
        result.setPendingAmount(result.getReimbursementAmount().subtract(result.getReimbursedAmount()));
        result.setReimbursementStatus(result.getReimbursementAmount().signum() == 0 ? "none"
            : result.getReimbursedAmount().signum() == 0 ? "pending" : result.getPendingAmount().signum() > 0 ? "partial" : "received");
    }
    private void money(BigDecimal amount, boolean allowZero) {
        if (amount == null || amount.signum() < (allowZero ? 0 : 1) || amount.scale() > 2 || amount.precision() - amount.scale() > 16)
            throw new IllegalArgumentException("请输入有效金额，最多两位小数");
    }
}
