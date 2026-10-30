package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.dto.FreeEstimateResponse;
import com.example.app.entity.AccountEntity;
import com.example.app.entity.AccountTypeEntity;
import com.example.app.entity.TransactionEntity;
import com.example.app.mapper.AccountMapper;
import com.example.app.mapper.AccountTypeMapper;
import com.example.app.mapper.TransactionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FreeEstimateService {

    private static final String ACTIVE_STATUS = "active";
    private static final String NORMAL_STATUS = "normal";
    private static final String CASH_ACCOUNT_TYPE = "cash";
    private static final String TYPE_INCOME = "income";
    private static final String TYPE_EXPENSE = "expense";

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final TransactionMapper transactionMapper;
    private final AccountService accountService;

    public FreeEstimateService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        TransactionMapper transactionMapper,
        AccountService accountService
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.transactionMapper = transactionMapper;
        this.accountService = accountService;
    }

    public FreeEstimateResponse estimate(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusMonths(11).withDayOfMonth(1);
        List<Long> cashAccountIds = loadCashAccountIds(userId);
        List<TransactionEntity> transactions = cashAccountIds.isEmpty()
            ? Collections.emptyList()
            : transactionMapper.selectList(new LambdaQueryWrapper<TransactionEntity>()
                .eq(TransactionEntity::getUserId, userId)
                .eq(TransactionEntity::getStatus, NORMAL_STATUS)
                .in(TransactionEntity::getType, List.of(TYPE_INCOME, TYPE_EXPENSE))
                .in(TransactionEntity::getAccountId, cashAccountIds)
                .ge(TransactionEntity::getOccurredAt, windowStart.atStartOfDay())
                .orderByAsc(TransactionEntity::getOccurredAt)
                .orderByAsc(TransactionEntity::getId));

        BigDecimal incomeTotal = sumByType(transactions, TYPE_INCOME);
        BigDecimal expenseTotal = sumByType(transactions, TYPE_EXPENSE);
        LocalDate firstTransactionDate = transactions.stream()
            .map(TransactionEntity::getOccurredAt)
            .filter(item -> item != null)
            .map(item -> item.toLocalDate())
            .min(Comparator.naturalOrder())
            .orElse(null);
        LocalDate lastTransactionDate = transactions.stream()
            .map(TransactionEntity::getOccurredAt)
            .filter(item -> item != null)
            .map(item -> item.toLocalDate())
            .max(Comparator.naturalOrder())
            .orElse(null);

        int dataMonths = calculateDataMonths(firstTransactionDate, lastTransactionDate);
        BigDecimal historicalMonthlyIncome = divide(incomeTotal, dataMonths);
        BigDecimal historicalMonthlyExpense = divide(expenseTotal, dataMonths);
        BigDecimal monthlyExpense = historicalMonthlyExpense;
        BigDecimal monthlyIncome = historicalMonthlyIncome;

        FreeEstimateResponse response = new FreeEstimateResponse();
        response.setUserId(userId);
        response.setAsOfDate(today);
        response.setCurrentNetAssets(scale(accountService.calculateTotalAssets(userId, ACTIVE_STATUS)));
        response.setHistoricalMonthlyIncome(scale(historicalMonthlyIncome));
        response.setHistoricalMonthlyExpense(scale(historicalMonthlyExpense));
        response.setMonthlyExpense(scale(monthlyExpense));
        response.setMonthlyIncome(scale(monthlyIncome));
        response.setMonthlySurplus(scale(monthlyIncome.subtract(monthlyExpense)));
        response.setDataStartDate(firstTransactionDate);
        response.setDataEndDate(lastTransactionDate);
        response.setDataMonths(dataMonths);
        response.setIncomeTransactionCount(countByType(transactions, TYPE_INCOME));
        response.setExpenseTransactionCount(countByType(transactions, TYPE_EXPENSE));
        return response;
    }

    private List<Long> loadCashAccountIds(Long userId) {
        List<AccountEntity> accounts = accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUserId, userId)
            .eq(AccountEntity::getStatus, ACTIVE_STATUS));
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> accountTypeIds = accounts.stream()
            .map(AccountEntity::getAccountTypeId)
            .filter(item -> item != null)
            .collect(Collectors.toSet());
        Map<Long, AccountTypeEntity> accountTypes = accountTypeMapper.selectByIds(accountTypeIds).stream()
            .collect(Collectors.toMap(AccountTypeEntity::getId, Function.identity()));
        return accounts.stream()
            .filter(account -> {
                AccountTypeEntity accountType = accountTypes.get(account.getAccountTypeId());
                return accountType != null && CASH_ACCOUNT_TYPE.equals(accountType.getCode());
            })
            .map(AccountEntity::getId)
            .toList();
    }

    private int calculateDataMonths(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return 1;
        }
        return Math.max(
            1,
            (int) (ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(endDate)) + 1)
        );
    }

    private BigDecimal sumByType(List<TransactionEntity> transactions, String type) {
        return transactions.stream()
            .filter(item -> type.equals(item.getType()))
            .map(TransactionEntity::getAmount)
            .filter(item -> item != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int countByType(List<TransactionEntity> transactions, String type) {
        return (int) transactions.stream()
            .filter(item -> type.equals(item.getType()))
            .count();
    }

    private BigDecimal divide(BigDecimal amount, int divisor) {
        if (amount == null || divisor <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
