package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.MonthlyBudgetRequest;
import com.example.finance.dto.MonthlyBudgetResponse;
import com.example.finance.entity.MonthlyBudgetEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.MonthlyBudgetMapper;
import com.example.finance.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MonthlyBudgetService {

    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String ACTIVE_STATUS = "active";
    private static final String DELETED_STATUS = "deleted";
    private static final String EXPENSE_TYPE = "expense";
    private static final String NORMAL_TRANSACTION_STATUS = "normal";

    private final MonthlyBudgetMapper monthlyBudgetMapper;
    private final TransactionMapper transactionMapper;

    public MonthlyBudgetService(MonthlyBudgetMapper monthlyBudgetMapper, TransactionMapper transactionMapper) {
        this.monthlyBudgetMapper = monthlyBudgetMapper;
        this.transactionMapper = transactionMapper;
    }

    public List<MonthlyBudgetResponse> list(Long userId, Integer limit) {
        LambdaQueryWrapper<MonthlyBudgetEntity> wrapper = new LambdaQueryWrapper<MonthlyBudgetEntity>()
            .eq(userId != null, MonthlyBudgetEntity::getUserId, userId)
            .eq(MonthlyBudgetEntity::getStatus, ACTIVE_STATUS)
            .orderByDesc(MonthlyBudgetEntity::getBudgetMonth)
            .orderByDesc(MonthlyBudgetEntity::getId);

        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + Math.min(limit, 120));
        }

        return monthlyBudgetMapper.selectList(wrapper).stream()
            .map(this::toResponse)
            .toList();
    }

    public Optional<MonthlyBudgetResponse> getCurrent(Long userId, LocalDate budgetMonth) {
        return Optional.ofNullable(findActiveBudget(userId, normalizeMonth(budgetMonth)))
            .map(this::toResponse);
    }

    @Transactional
    public MonthlyBudgetResponse create(MonthlyBudgetRequest request) {
        LocalDate month = normalizeMonth(request.getBudgetMonth());
        if (findActiveBudget(request.getUserId(), month) != null) {
            throw new IllegalArgumentException("该月份预算已存在，请修改当前预算");
        }

        MonthlyBudgetEntity entity = new MonthlyBudgetEntity();
        fillEntity(entity, request, month);
        entity.setStatus(ACTIVE_STATUS);
        monthlyBudgetMapper.insert(entity);

        return toResponse(monthlyBudgetMapper.selectById(entity.getId()));
    }

    @Transactional
    public Optional<MonthlyBudgetResponse> update(Long id, MonthlyBudgetRequest request) {
        MonthlyBudgetEntity entity = monthlyBudgetMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus())) {
            return Optional.empty();
        }

        LocalDate month = normalizeMonth(request.getBudgetMonth());
        MonthlyBudgetEntity exists = findActiveBudget(request.getUserId(), month);
        if (exists != null && !exists.getId().equals(id)) {
            throw new IllegalArgumentException("该月份预算已存在，请修改当前预算");
        }

        fillEntity(entity, request, month);
        monthlyBudgetMapper.updateById(entity);

        return Optional.of(toResponse(monthlyBudgetMapper.selectById(id)));
    }

    @Transactional
    public boolean delete(Long id, Long userId) {
        MonthlyBudgetEntity entity = monthlyBudgetMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !userId.equals(entity.getUserId())) {
            return false;
        }

        entity.setStatus(DELETED_STATUS);
        monthlyBudgetMapper.updateById(entity);
        return true;
    }

    private void fillEntity(MonthlyBudgetEntity entity, MonthlyBudgetRequest request, LocalDate month) {
        entity.setUserId(request.getUserId());
        entity.setBudgetMonth(month);
        entity.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setRemark(request.getRemark());
    }

    private MonthlyBudgetEntity findActiveBudget(Long userId, LocalDate month) {
        return monthlyBudgetMapper.selectOne(new LambdaQueryWrapper<MonthlyBudgetEntity>()
            .eq(MonthlyBudgetEntity::getUserId, userId)
            .eq(MonthlyBudgetEntity::getBudgetMonth, month)
            .eq(MonthlyBudgetEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1"));
    }

    private LocalDate normalizeMonth(LocalDate month) {
        LocalDate normalized = month == null ? LocalDate.now() : month;
        return normalized.withDayOfMonth(1);
    }

    private MonthlyBudgetResponse toResponse(MonthlyBudgetEntity entity) {
        BigDecimal usedAmount = calculateUsedAmount(entity.getUserId(), entity.getBudgetMonth());
        BigDecimal amount = entity.getAmount() == null ? BigDecimal.ZERO : entity.getAmount();
        BigDecimal remainingAmount = amount.subtract(usedAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal usagePercent = BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            usagePercent = usedAmount.multiply(BigDecimal.valueOf(100))
                .divide(amount, 2, RoundingMode.HALF_UP);
        }

        MonthlyBudgetResponse response = new MonthlyBudgetResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setBudgetMonth(entity.getBudgetMonth());
        response.setAmount(amount);
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setUsedAmount(usedAmount);
        response.setRemainingAmount(remainingAmount);
        response.setUsagePercent(usagePercent);
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private BigDecimal calculateUsedAmount(Long userId, LocalDate month) {
        LocalDateTime startTime = month.atStartOfDay();
        LocalDateTime endTime = month.plusMonths(1).atStartOfDay();
        return transactionMapper.selectList(new LambdaQueryWrapper<TransactionEntity>()
                .eq(TransactionEntity::getUserId, userId)
                .eq(TransactionEntity::getType, EXPENSE_TYPE)
                .eq(TransactionEntity::getStatus, NORMAL_TRANSACTION_STATUS)
                .ge(TransactionEntity::getOccurredAt, startTime)
                .lt(TransactionEntity::getOccurredAt, endTime))
            .stream()
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }
}
