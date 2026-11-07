package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.dto.SavingsGoalRequest;
import com.example.app.dto.SavingsGoalResponse;
import com.example.app.dto.SavingsGoalEntryRequest;
import com.example.app.dto.SavingsGoalEntryResponse;
import com.example.app.entity.AccountEntity;
import com.example.app.entity.AccountTypeEntity;
import com.example.app.entity.SavingsGoalEntity;
import com.example.app.entity.SavingsGoalEntryEntity;
import com.example.app.mapper.AccountMapper;
import com.example.app.mapper.AccountTypeMapper;
import com.example.app.mapper.SavingsGoalMapper;
import com.example.app.mapper.SavingsGoalEntryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SavingsGoalService {
    private final SavingsGoalMapper goals;
    private final SavingsGoalEntryMapper entries;
    private final AccountMapper accounts;
    private final AccountTypeMapper types;
    private final AccountingIdempotencyService idempotency;
    private final Clock clock;

    @Autowired
    public SavingsGoalService(SavingsGoalMapper goals, SavingsGoalEntryMapper entries, AccountMapper accounts, AccountTypeMapper types,
        AccountingIdempotencyService idempotency) {
        this(goals, entries, accounts, types, idempotency, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    SavingsGoalService(SavingsGoalMapper goals, SavingsGoalEntryMapper entries, AccountMapper accounts, AccountTypeMapper types,
        AccountingIdempotencyService idempotency, Clock clock) {
        this.goals = goals; this.entries = entries; this.accounts = accounts; this.types = types; this.idempotency = idempotency; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SavingsGoalResponse> list(long userId) {
        var rows = goals.selectList(new LambdaQueryWrapper<SavingsGoalEntity>()
            .eq(SavingsGoalEntity::getUserId, userId).orderByDesc(SavingsGoalEntity::getId));
        if (rows.isEmpty()) return List.of();
        var accountMap = accounts.selectByIds(rows.stream().map(SavingsGoalEntity::getAccountId).toList()).stream()
            .filter(a -> Objects.equals(a.getUserId(), userId))
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        var cashTypes = types.selectList(new LambdaQueryWrapper<AccountTypeEntity>().eq(AccountTypeEntity::getCode, "cash"))
            .stream().map(AccountTypeEntity::getId).collect(Collectors.toSet());
        var balances = entries.balances(userId).stream()
            .collect(Collectors.toMap(SavingsGoalEntryMapper.Balance::goalId, SavingsGoalEntryMapper.Balance::amount));
        Map<Long, BigDecimal> reserved = new HashMap<>();
        rows.forEach(goal -> reserved.merge(goal.getAccountId(), balances.getOrDefault(goal.getId(), BigDecimal.ZERO), BigDecimal::add));
        LocalDate today = LocalDate.now(clock);
        return rows.stream().map(goal -> response(goal, accountMap.get(goal.getAccountId()), cashTypes, today,
            balances.getOrDefault(goal.getId(), BigDecimal.ZERO), reserved.get(goal.getAccountId()))).toList();
    }

    @Transactional
    public SavingsGoalResponse create(long userId, SavingsGoalRequest request) {
        accounts.lockUserForWrite(userId);
        validate(request);
        return idempotency.execute(userId, "savings-goal", request.requestId(), request, SavingsGoalResponse.class, () -> {
            var account = requireAccount(userId, request.accountId());
            var goal = new SavingsGoalEntity();
            goal.setUserId(userId);
            fill(goal, request, account);
            goals.insert(goal);
            return response(goal, account, Set.of(account.getAccountTypeId()), LocalDate.now(clock), BigDecimal.ZERO, accounts.savingsReserved(account.getId()));
        });
    }

    @Transactional
    public SavingsGoalResponse update(long userId, long id, SavingsGoalRequest request) {
        accounts.lockUserForWrite(userId);
        validate(request);
        var goal = requireGoal(userId, id);
        var account = requireAccount(userId, request.accountId());
        if (!Objects.equals(goal.getAccountId(), account.getId()) && entries.selectCount(
            new LambdaQueryWrapper<SavingsGoalEntryEntity>().eq(SavingsGoalEntryEntity::getGoalId, id)
                .isNotNull(SavingsGoalEntryEntity::getTransactionId)) > 0)
            throw new IllegalArgumentException("请先取消流水中的目标关联，再更换账户");
        if (!Objects.equals(goal.getCurrencyCode(), account.getCurrencyCode()) && entries.selectCount(
            new LambdaQueryWrapper<SavingsGoalEntryEntity>().eq(SavingsGoalEntryEntity::getGoalId, id)) > 0)
            throw new IllegalArgumentException("该目标已有存取记录，请选择相同币种的账户");
        fill(goal, request, account);
        goals.updateById(goal);
        accounts.requireSavingsCovered(account.getId(), account.getCurrentBalance());
        return response(goal, account, Set.of(account.getAccountTypeId()), LocalDate.now(clock), entries.savedAmount(id), accounts.savingsReserved(account.getId()));
    }

    @Transactional
    public void delete(long userId, long id) {
        accounts.lockUserForWrite(userId);
        requireGoal(userId, id);
        entries.delete(new LambdaQueryWrapper<SavingsGoalEntryEntity>().eq(SavingsGoalEntryEntity::getGoalId, id));
        goals.deleteById(id);
    }

    public SavingsGoalEntryResponse.Page entries(long userId, long id, Long beforeId) {
        requireGoal(userId, id);
        if (beforeId != null && beforeId <= 0) throw new IllegalArgumentException("记录游标无效");
        var rows = entries.selectList(new LambdaQueryWrapper<SavingsGoalEntryEntity>()
            .eq(SavingsGoalEntryEntity::getGoalId, id).lt(beforeId != null, SavingsGoalEntryEntity::getId, beforeId)
            .orderByDesc(SavingsGoalEntryEntity::getId).last("LIMIT 51"));
        var page = rows.stream().limit(50).map(this::entryResponse).toList();
        return new SavingsGoalEntryResponse.Page(page, rows.size() > 50 ? page.get(49).id() : null);
    }

    private record EntryCommand(long goalId, SavingsGoalEntryRequest entry) {}

    @Transactional
    public SavingsGoalEntryResponse addEntry(long userId, long id, SavingsGoalEntryRequest request) {
        accounts.lockUserForWrite(userId);
        var goal = requireGoal(userId, id);
        validateEntry(request);
        var account = "deposit".equals(request.type()) ? requireAccount(userId, goal.getAccountId()) : null;
        return idempotency.execute(userId, "savings-goal-entry", request.requestId(), new EntryCommand(id, request),
            SavingsGoalEntryResponse.class, () -> {
                BigDecimal saved = entries.savedAmount(id);
                if ("withdrawal".equals(request.type()) && request.amount().compareTo(saved) > 0)
                    throw new IllegalArgumentException("取出金额不能超过该目标已存金额");
                if ("deposit".equals(request.type()) && saved.add(request.amount()).compareTo(new BigDecimal("9999999999999999.99")) > 0)
                    throw new IllegalArgumentException("该目标已存金额超出可记录范围");
                var entry = new SavingsGoalEntryEntity();
                entry.setGoalId(id); entry.setType(request.type()); entry.setAmount(request.amount());
                entry.setOccurredOn(request.occurredOn()); entry.setCreatedAt(LocalDateTime.now(clock));
                entry.setRemark(request.remark() == null || request.remark().isBlank() ? null : request.remark().trim());
                entries.insert(entry);
                if ("deposit".equals(request.type())) accounts.requireSavingsCovered(account.getId(), account.getCurrentBalance());
                return entryResponse(entry);
            });
    }

    private void validateEntry(SavingsGoalEntryRequest request) {
        if (!"deposit".equals(request.type()) && !"withdrawal".equals(request.type()))
            throw new IllegalArgumentException("请选择存入或取出");
        var amount = request.amount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 16)
            throw new IllegalArgumentException("请输入大于0且最多两位小数的金额");
        if (request.occurredOn() == null || request.occurredOn().getYear() < 1000 || request.occurredOn().isAfter(LocalDate.now(clock)))
            throw new IllegalArgumentException("请选择不晚于今天的有效日期");
        if (request.remark() != null && request.remark().length() > 500) throw new IllegalArgumentException("备注最多500字");
    }

    private SavingsGoalEntryResponse entryResponse(SavingsGoalEntryEntity entry) {
        return new SavingsGoalEntryResponse(entry.getId(), entry.getGoalId(), entry.getType(), entry.getAmount(),
            entry.getOccurredOn(), entry.getRemark(), entry.getCreatedAt(), entry.getTransactionId());
    }

    private SavingsGoalEntity requireGoal(long userId, long id) {
        var goal = goals.selectById(id);
        if (goal == null || !Objects.equals(goal.getUserId(), userId)) throw new IllegalArgumentException("储蓄目标不存在");
        return goal;
    }

    private AccountEntity requireAccount(long userId, Long id) {
        var account = id == null ? null : accounts.selectById(id);
        if (account == null || !Objects.equals(account.getUserId(), userId) || !"active".equals(account.getStatus()))
            throw new IllegalArgumentException("请选择本人启用的现金账户");
        var type = types.selectById(account.getAccountTypeId());
        if (type == null || !"cash".equals(type.getCode())) throw new IllegalArgumentException("储蓄目标只能关联现金账户");
        if (account.getCurrencyCode() == null || !account.getCurrencyCode().matches("[A-Z]{3}"))
            throw new IllegalArgumentException("请先设置账户币种");
        return account;
    }

    private void validate(SavingsGoalRequest request) {
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > 80)
            throw new IllegalArgumentException("请输入不超过80字的目标名称");
        var amount = request.targetAmount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 16)
            throw new IllegalArgumentException("请输入大于0且最多两位小数的目标金额");
        if (request.targetDate() != null && (request.targetDate().getYear() < 1000 || request.targetDate().getYear() > 9999))
            throw new IllegalArgumentException("截止日期无效");
        if (request.remark() != null && request.remark().length() > 500) throw new IllegalArgumentException("备注最多500字");
    }

    private void fill(SavingsGoalEntity goal, SavingsGoalRequest request, AccountEntity account) {
        goal.setName(request.name().trim()); goal.setAccountId(account.getId()); goal.setCurrencyCode(account.getCurrencyCode());
        goal.setTargetAmount(request.targetAmount()); goal.setTargetDate(request.targetDate());
        goal.setRemark(request.remark() == null || request.remark().isBlank() ? null : request.remark().trim());
        goal.setUpdatedAt(LocalDateTime.now(clock));
        if (goal.getCreatedAt() == null) goal.setCreatedAt(goal.getUpdatedAt());
    }

    private SavingsGoalResponse response(SavingsGoalEntity goal, AccountEntity account, Set<Long> cashTypes, LocalDate today, BigDecimal current, BigDecimal reserved) {
        boolean available = account != null && "active".equals(account.getStatus()) && cashTypes.contains(account.getAccountTypeId())
            && Objects.equals(goal.getCurrencyCode(), account.getCurrencyCode()) && account.getCurrentBalance() != null;
        BigDecimal remaining = goal.getTargetAmount().subtract(current).max(BigDecimal.ZERO);
        BigDecimal percent = current.multiply(BigDecimal.valueOf(100)).divide(goal.getTargetAmount(), 2, RoundingMode.DOWN)
            .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        String status = remaining.signum() == 0 ? "completed"
            : goal.getTargetDate() != null && goal.getTargetDate().isBefore(today) ? "overdue" : "saving";
        Integer months = goal.getTargetDate() == null || goal.getTargetDate().isBefore(today) ? null
            : (int) ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(goal.getTargetDate())) + 1;
        BigDecimal monthly = months == null ? null : remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.UP);
        return new SavingsGoalResponse(goal.getId(), goal.getName(), goal.getAccountId(), account == null ? null : account.getName(),
            goal.getCurrencyCode(), goal.getTargetAmount(), goal.getTargetDate(), goal.getRemark(), available, current, remaining,
            percent, status, months, monthly, today, available ? account.getCurrentBalance() : null, reserved,
            available ? account.getCurrentBalance().subtract(reserved).max(BigDecimal.ZERO) : null);
    }
}
