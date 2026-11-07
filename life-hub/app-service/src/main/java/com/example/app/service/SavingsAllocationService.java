package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.dto.SavingsAllocation;
import com.example.app.entity.SavingsGoalEntryEntity;
import com.example.app.entity.TransactionEntity;
import com.example.app.mapper.SavingsGoalEntryMapper;
import com.example.app.mapper.SavingsGoalMapper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** 调用方持有用户账务锁；流水分配、目标记录及现金余额在同一事务中提交。 */
@Service
public class SavingsAllocationService {
    private final SavingsGoalMapper goals;
    private final SavingsGoalEntryMapper entries;

    public SavingsAllocationService(SavingsGoalMapper goals, SavingsGoalEntryMapper entries) {
        this.goals = goals; this.entries = entries;
    }

    public List<SavingsAllocation> allocations(long transactionId) {
        return linkedEntries(transactionId).stream().map(e -> new SavingsAllocation(e.getGoalId(), e.getAmount())).toList();
    }

    private List<SavingsGoalEntryEntity> linkedEntries(long transactionId) {
        return entries.selectList(new LambdaQueryWrapper<SavingsGoalEntryEntity>()
            .eq(SavingsGoalEntryEntity::getTransactionId, transactionId).orderByAsc(SavingsGoalEntryEntity::getId));
    }

    /** 替换本笔分配后再校验目标，避免修改流水时先撤销造成暂时负数。 */
    public Set<Long> replace(long userId, long transactionId, TransactionEntity transaction, List<SavingsAllocation> requested) {
        var previous = linkedEntries(transactionId);
        List<SavingsAllocation> next = transaction == null ? List.of() : requested == null
            ? previous.stream().map(e -> new SavingsAllocation(e.getGoalId(), e.getAmount())).toList() : requested;
        if (next.size() > 100) throw new IllegalArgumentException("单笔最多关联100个储蓄目标");
        Set<Long> affected = previous.stream().map(SavingsGoalEntryEntity::getGoalId).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        BigDecimal deposits = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO;
        var newEntries = new ArrayList<SavingsGoalEntryEntity>();
        for (var allocation : next) {
            if (allocation == null || allocation.goalId() == null || allocation.amount() == null
                || allocation.amount().signum() <= 0 || allocation.amount().stripTrailingZeros().scale() > 2
                || allocation.amount().precision() - allocation.amount().scale() > 16)
                throw new IllegalArgumentException("请输入有效的目标分配金额，最多两位小数");
            if (!seen.add(allocation.goalId())) throw new IllegalArgumentException("同一笔流水不能重复选择同一个目标");
            var goal = goals.selectById(allocation.goalId());
            if (goal == null || !Objects.equals(goal.getUserId(), userId)) throw new IllegalArgumentException("储蓄目标不存在");
            boolean deposit;
            if ("transfer".equals(transaction.getType())) {
                if (Objects.equals(goal.getAccountId(), transaction.getToAccountId())) deposit = true;
                else if (Objects.equals(goal.getAccountId(), transaction.getFromAccountId())) deposit = false;
                else throw new IllegalArgumentException("只能选择转出或转入账户关联的储蓄目标");
            } else {
                if (!Objects.equals(goal.getAccountId(), transaction.getAccountId()))
                    throw new IllegalArgumentException("储蓄目标必须关联本笔收支的现金账户");
                deposit = "income".equals(transaction.getType());
            }
            if (!Objects.equals(goal.getCurrencyCode(), transaction.getCurrencyCode()))
                throw new IllegalArgumentException("目标与流水币种必须一致");
            if (deposit) deposits = deposits.add(allocation.amount()); else withdrawals = withdrawals.add(allocation.amount());
            var entry = new SavingsGoalEntryEntity();
            entry.setGoalId(goal.getId()); entry.setTransactionId(transactionId); entry.setType(deposit ? "deposit" : "withdrawal");
            entry.setAmount(allocation.amount()); entry.setOccurredOn(transaction.getOccurredAt().toLocalDate());
            entry.setRemark(transaction.getRemark()); entry.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            newEntries.add(entry); affected.add(goal.getId());
        }
        if (transaction != null && (deposits.compareTo(transaction.getAmount()) > 0 || withdrawals.compareTo(transaction.getAmount()) > 0))
            throw new IllegalArgumentException("目标计入或扣减总额不能超过本笔流水金额");
        entries.delete(new LambdaQueryWrapper<SavingsGoalEntryEntity>().eq(SavingsGoalEntryEntity::getTransactionId, transactionId));
        newEntries.forEach(entries::insert);
        Set<Long> accountIds = new HashSet<>();
        for (Long goalId : affected) {
            var goal = goals.selectById(goalId);
            if (goal == null || !Objects.equals(goal.getUserId(), userId)) throw new IllegalArgumentException("储蓄目标不存在");
            BigDecimal saved = entries.savedAmount(goalId);
            if (saved.signum() < 0) throw new IllegalArgumentException("目标「" + goal.getName() + "」已存金额不足，请先调整关联存取记录");
            if (saved.compareTo(new BigDecimal("9999999999999999.99")) > 0) throw new IllegalArgumentException("目标已存金额超出可记录范围");
            accountIds.add(goal.getAccountId());
        }
        return accountIds;
    }
}
