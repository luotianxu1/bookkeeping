package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.FreeEstimateResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.InvestmentFixedExpenseEntity;
import com.example.finance.entity.MonthlyBudgetEntity;
import com.example.finance.entity.RenewalSubscriptionEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.InvestmentFixedExpenseMapper;
import com.example.finance.mapper.MonthlyBudgetMapper;
import com.example.finance.mapper.RenewalSubscriptionMapper;
import com.example.finance.mapper.TransactionMapper;
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
    private static final String CYCLE_MONTHLY = "monthly";
    private static final String CYCLE_QUARTERLY = "quarterly";
    private static final String CYCLE_YEARLY = "yearly";

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final TransactionMapper transactionMapper;
    private final MonthlyBudgetMapper monthlyBudgetMapper;
    private final RenewalSubscriptionMapper renewalSubscriptionMapper;
    private final InvestmentFixedExpenseMapper investmentFixedExpenseMapper;
    private final AccountService accountService;

    public FreeEstimateService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        TransactionMapper transactionMapper,
        MonthlyBudgetMapper monthlyBudgetMapper,
        RenewalSubscriptionMapper renewalSubscriptionMapper,
        InvestmentFixedExpenseMapper investmentFixedExpenseMapper,
        AccountService accountService
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.transactionMapper = transactionMapper;
        this.monthlyBudgetMapper = monthlyBudgetMapper;
        this.renewalSubscriptionMapper = renewalSubscriptionMapper;
        this.investmentFixedExpenseMapper = investmentFixedExpenseMapper;
        this.accountService = accountService;
    }

    public FreeEstimateResponse estimate(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        LocalDate today = LocalDate.now();
        List<Long> cashAccountIds = loadCashAccountIds(userId);
        List<TransactionEntity> transactions = cashAccountIds.isEmpty()
            ? Collections.emptyList()
            : transactionMapper.selectList(new LambdaQueryWrapper<TransactionEntity>()
                .eq(TransactionEntity::getUserId, userId)
                .eq(TransactionEntity::getStatus, NORMAL_STATUS)
                .in(TransactionEntity::getType, List.of(TYPE_INCOME, TYPE_EXPENSE))
                .in(TransactionEntity::getAccountId, cashAccountIds)
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
        BigDecimal budgetMonthlyExpense = loadBudgetMonthlyExpense(userId);
        BigDecimal recurringMonthlyExpense = loadRecurringMonthlyExpense(userId);
        BigDecimal monthlyExpense = max(
            historicalMonthlyExpense,
            budgetMonthlyExpense,
            recurringMonthlyExpense
        );
        BigDecimal monthlyIncome = historicalMonthlyIncome;

        FreeEstimateResponse response = new FreeEstimateResponse();
        response.setUserId(userId);
        response.setAsOfDate(today);
        response.setCurrentNetAssets(scale(accountService.calculateTotalAssets(userId, ACTIVE_STATUS)));
        response.setHistoricalMonthlyIncome(scale(historicalMonthlyIncome));
        response.setHistoricalMonthlyExpense(scale(historicalMonthlyExpense));
        response.setBudgetMonthlyExpense(scale(budgetMonthlyExpense));
        response.setRecurringMonthlyExpense(scale(recurringMonthlyExpense));
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

    private BigDecimal loadBudgetMonthlyExpense(Long userId) {
        MonthlyBudgetEntity budget = monthlyBudgetMapper.selectOne(new LambdaQueryWrapper<MonthlyBudgetEntity>()
            .eq(MonthlyBudgetEntity::getUserId, userId)
            .eq(MonthlyBudgetEntity::getStatus, ACTIVE_STATUS)
            .orderByDesc(MonthlyBudgetEntity::getBudgetMonth)
            .orderByDesc(MonthlyBudgetEntity::getId)
            .last("LIMIT 1"));
        return budget == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : scale(budget.getAmount());
    }

    private BigDecimal loadRecurringMonthlyExpense(Long userId) {
        BigDecimal renewalExpense = renewalSubscriptionMapper.selectList(new LambdaQueryWrapper<RenewalSubscriptionEntity>()
                .eq(RenewalSubscriptionEntity::getUserId, userId)
                .eq(RenewalSubscriptionEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .map(this::toMonthlyRenewalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fixedExpense = investmentFixedExpenseMapper.selectList(new LambdaQueryWrapper<InvestmentFixedExpenseEntity>()
                .eq(InvestmentFixedExpenseEntity::getUserId, userId)
                .eq(InvestmentFixedExpenseEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .map(InvestmentFixedExpenseEntity::getAmount)
            .filter(item -> item != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return renewalExpense.add(fixedExpense).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toMonthlyRenewalAmount(RenewalSubscriptionEntity subscription) {
        BigDecimal amount = subscription.getAmount() == null ? BigDecimal.ZERO : subscription.getAmount();
        String cycle = subscription.getBillingCycle();
        if (CYCLE_QUARTERLY.equals(cycle)) {
            return amount.divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
        }
        if (CYCLE_YEARLY.equals(cycle)) {
            return amount.divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
        }
        return amount;
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

    private BigDecimal max(BigDecimal... values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(result) > 0) {
                result = value;
            }
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
