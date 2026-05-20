package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.DebtAccountSummaryResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DebtAccountService {

    private static final String ACTIVE_STATUS = "active";
    private static final Set<String> DEBT_ACCOUNT_CODES = Set.of("debt", "loan_receivable", "loan_payable");

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;

    public DebtAccountService(AccountMapper accountMapper, AccountTypeMapper accountTypeMapper) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
    }

    public DebtAccountSummaryResponse summary(Long userId) {
        List<AccountEntity> accounts = loadDebtAccounts(userId);
        if (accounts.isEmpty()) {
            return emptySummary();
        }

        BigDecimal totalAmount = accounts.stream()
            .map(account -> defaultAmount(account.getCurrentBalance()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        DebtAccountSummaryResponse response = new DebtAccountSummaryResponse();
        response.setTotalAmount(totalAmount);
        response.setAccountCount(accounts.size());
        return response;
    }

    private List<AccountEntity> loadDebtAccounts(Long userId) {
        List<AccountTypeEntity> debtTypes = loadDebtTypes();
        if (debtTypes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> debtTypeIds = debtTypes.stream()
            .map(AccountTypeEntity::getId)
            .collect(Collectors.toSet());

        return accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(userId != null, AccountEntity::getUserId, userId)
            .in(AccountEntity::getAccountTypeId, debtTypeIds)
            .eq(AccountEntity::getStatus, ACTIVE_STATUS)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId));
    }

    private List<AccountTypeEntity> loadDebtTypes() {
        return accountTypeMapper.selectList(new LambdaQueryWrapper<AccountTypeEntity>()
            .in(AccountTypeEntity::getCode, DEBT_ACCOUNT_CODES)
            .eq(AccountTypeEntity::getStatus, ACTIVE_STATUS)
            .orderByAsc(AccountTypeEntity::getSortOrder)
            .orderByAsc(AccountTypeEntity::getId));
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private DebtAccountSummaryResponse emptySummary() {
        DebtAccountSummaryResponse response = new DebtAccountSummaryResponse();
        response.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setAccountCount(0);
        return response;
    }
}
