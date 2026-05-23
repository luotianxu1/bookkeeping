package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.DebtAccountSummaryResponse;
import com.example.finance.dto.DebtRecordRequest;
import com.example.finance.dto.DebtRecordResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.DebtRecordEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.DebtRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DebtAccountService {

    private static final String ACTIVE_STATUS = "active";
    private static final String VOIDED_STATUS = "voided";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String DIRECTION_PAYABLE = "payable";
    private static final String DIRECTION_RECEIVABLE = "receivable";
    private static final Set<String> DEBT_ACCOUNT_CODES = Set.of("debt", "loan_receivable", "loan_payable");
    private static final Set<String> DEBT_DIRECTIONS = Set.of(DIRECTION_PAYABLE, DIRECTION_RECEIVABLE);

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final DebtRecordMapper debtRecordMapper;

    public DebtAccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        DebtRecordMapper debtRecordMapper
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.debtRecordMapper = debtRecordMapper;
    }

    public DebtAccountSummaryResponse summary(Long userId, Long accountId) {
        List<AccountEntity> accounts = accountId == null
            ? loadDebtAccounts(userId)
            : List.of(requireDebtAccount(userId, accountId));
        List<DebtRecordEntity> records = loadDebtRecords(userId, accountId);
        if (accounts.isEmpty()) {
            return emptySummary();
        }

        BigDecimal payableTotal = sumByDirection(records, DIRECTION_PAYABLE);
        BigDecimal receivableTotal = sumByDirection(records, DIRECTION_RECEIVABLE);

        DebtAccountSummaryResponse response = new DebtAccountSummaryResponse();
        response.setNetAmount(receivableTotal.subtract(payableTotal).setScale(2, RoundingMode.HALF_UP));
        response.setPayableTotal(payableTotal);
        response.setReceivableTotal(receivableTotal);
        response.setAccountCount(accounts.size());
        response.setRecordCount(records.size());
        return response;
    }

    public List<DebtRecordResponse> listRecords(Long userId, Long accountId) {
        List<DebtRecordEntity> records = loadDebtRecords(userId, accountId);
        if (records.isEmpty()) {
            return List.of();
        }

        Set<Long> accountIds = records.stream()
            .map(DebtRecordEntity::getAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Set<Long> fundingAccountIds = records.stream()
            .map(DebtRecordEntity::getFundingAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Map<Long, AccountEntity> accountMap = accountIds.isEmpty()
            ? Collections.emptyMap()
            : accountMapper.selectBatchIds(accountIds).stream()
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Map<Long, AccountEntity> fundingAccountMap = fundingAccountIds.isEmpty()
            ? Collections.emptyMap()
            : accountMapper.selectBatchIds(fundingAccountIds).stream()
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));

        return records.stream()
            .map(record -> toResponse(
                record,
                accountMap.get(record.getAccountId()),
                fundingAccountMap.get(record.getFundingAccountId())
            ))
            .toList();
    }

    @Transactional
    public DebtRecordResponse createRecord(DebtRecordRequest request) {
        String direction = requireDirection(request.getDirection());
        AccountEntity account = requireDebtAccount(request.getUserId(), request.getAccountId());
        AccountEntity fundingAccount = findCashFundingAccount(request.getUserId(), request.getFundingAccountId());
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        DebtRecordEntity entity = new DebtRecordEntity();
        entity.setUserId(request.getUserId());
        entity.setAccountId(account.getId());
        entity.setFundingAccountId(fundingAccount == null ? null : fundingAccount.getId());
        entity.setDirection(direction);
        entity.setAmount(amount);
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setRemark(request.getRemark());
        entity.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now());
        entity.setStatus(ACTIVE_STATUS);

        applyFundingAccountChange(fundingAccount, direction, amount);
        debtRecordMapper.insert(entity);

        return toResponse(debtRecordMapper.selectById(entity.getId()), account, fundingAccount);
    }

    @Transactional
    public Optional<DebtRecordResponse> updateRecord(Long id, DebtRecordRequest request) {
        DebtRecordEntity entity = debtRecordMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }

        String direction = requireDirection(request.getDirection());
        AccountEntity account = requireDebtAccount(request.getUserId(), request.getAccountId());
        AccountEntity fundingAccount = findCashFundingAccount(request.getUserId(), request.getFundingAccountId());
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        rollbackFundingAccountChange(entity);

        entity.setAccountId(account.getId());
        entity.setFundingAccountId(fundingAccount == null ? null : fundingAccount.getId());
        entity.setDirection(direction);
        entity.setAmount(amount);
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setRemark(request.getRemark());
        entity.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : entity.getOccurredAt());

        applyFundingAccountChange(fundingAccount, direction, amount);
        debtRecordMapper.updateById(entity);

        return Optional.of(toResponse(debtRecordMapper.selectById(id), account, fundingAccount));
    }

    @Transactional
    public boolean deleteRecord(Long id, Long userId) {
        DebtRecordEntity entity = debtRecordMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !userId.equals(entity.getUserId())) {
            return false;
        }

        rollbackFundingAccountChange(entity);
        entity.setStatus(VOIDED_STATUS);
        debtRecordMapper.updateById(entity);
        return true;
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

    private List<DebtRecordEntity> loadDebtRecords(Long userId, Long accountId) {
        return debtRecordMapper.selectList(new LambdaQueryWrapper<DebtRecordEntity>()
            .eq(userId != null, DebtRecordEntity::getUserId, userId)
            .eq(accountId != null, DebtRecordEntity::getAccountId, accountId)
            .eq(DebtRecordEntity::getStatus, ACTIVE_STATUS)
            .orderByDesc(DebtRecordEntity::getOccurredAt)
            .orderByDesc(DebtRecordEntity::getId));
    }

    private AccountEntity requireDebtAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId()) || !ACTIVE_STATUS.equals(account.getStatus())) {
            throw new IllegalArgumentException("债务账户不存在");
        }

        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !DEBT_ACCOUNT_CODES.contains(accountType.getCode())) {
            throw new IllegalArgumentException("请选择有效的债务账户");
        }
        return account;
    }

    private AccountEntity requireCashFundingAccount(Long userId, Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("请选择现金账户");
        }
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId()) || !ACTIVE_STATUS.equals(account.getStatus())) {
            throw new IllegalArgumentException("现金账户不存在");
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("请选择有效的现金账户");
        }
        return account;
    }

    private AccountEntity findCashFundingAccount(Long userId, Long accountId) {
        if (accountId == null) {
            return null;
        }
        return requireCashFundingAccount(userId, accountId);
    }

    private String requireDirection(String direction) {
        if (!StringUtils.hasText(direction)) {
            throw new IllegalArgumentException("请选择借入或借出");
        }
        String normalizedDirection = direction.trim().toLowerCase();
        if (!DEBT_DIRECTIONS.contains(normalizedDirection)) {
            throw new IllegalArgumentException("债务方向无效");
        }
        return normalizedDirection;
    }

    private BigDecimal sumByDirection(List<DebtRecordEntity> records, String direction) {
        return records.stream()
            .filter(record -> direction.equals(record.getDirection()))
            .map(DebtRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private void applyFundingAccountChange(AccountEntity fundingAccount, String direction, BigDecimal amount) {
        if (fundingAccount == null) {
            return;
        }
        if (DIRECTION_RECEIVABLE.equals(direction)) {
            deductFundingAccount(fundingAccount, amount);
            return;
        }
        creditFundingAccount(fundingAccount, amount);
    }

    private void rollbackFundingAccountChange(DebtRecordEntity entity) {
        if (entity.getFundingAccountId() == null || entity.getAmount() == null) {
            return;
        }
        AccountEntity fundingAccount = requireCashFundingAccount(entity.getUserId(), entity.getFundingAccountId());
        BigDecimal amount = entity.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (DIRECTION_RECEIVABLE.equals(entity.getDirection())) {
            creditFundingAccount(fundingAccount, amount);
            return;
        }
        deductFundingAccount(fundingAccount, amount);
    }

    private void deductFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        BigDecimal nextBalance = currentBalance.subtract(amount.setScale(2, RoundingMode.HALF_UP));
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("现金账户余额不足");
        }
        account.setCurrentBalance(nextBalance);
        accountMapper.updateById(account);
    }

    private void creditFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        account.setCurrentBalance(currentBalance.add(amount.setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP));
        accountMapper.updateById(account);
    }

    private DebtRecordResponse toResponse(DebtRecordEntity entity, AccountEntity account, AccountEntity fundingAccount) {
        DebtRecordResponse response = new DebtRecordResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setAccountId(entity.getAccountId());
        response.setContactId(account == null ? null : account.getContactId());
        response.setAccountName(account == null ? null : account.getName());
        response.setFundingAccountId(entity.getFundingAccountId());
        response.setFundingAccountName(fundingAccount == null ? null : fundingAccount.getName());
        response.setDirection(entity.getDirection());
        response.setAmount(entity.getAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setRemark(entity.getRemark());
        response.setOccurredAt(entity.getOccurredAt());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private DebtAccountSummaryResponse emptySummary() {
        DebtAccountSummaryResponse response = new DebtAccountSummaryResponse();
        response.setNetAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setPayableTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setReceivableTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setAccountCount(0);
        response.setRecordCount(0);
        return response;
    }
}
