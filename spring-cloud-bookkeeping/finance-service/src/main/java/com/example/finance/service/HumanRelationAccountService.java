package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.HumanRelationRecordRequest;
import com.example.finance.dto.HumanRelationRecordResponse;
import com.example.finance.dto.HumanRelationSummaryResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.HumanRelationRecordEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.HumanRelationRecordMapper;
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
public class HumanRelationAccountService {

    private static final String ACTIVE_STATUS = "active";
    private static final String VOIDED_STATUS = "voided";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String HUMAN_RELATION_ACCOUNT_CODE = "human_relation";
    private static final String DIRECTION_OUTGOING = "outgoing";
    private static final String DIRECTION_INCOMING = "incoming";
    private static final Set<String> HUMAN_RELATION_DIRECTIONS = Set.of(DIRECTION_OUTGOING, DIRECTION_INCOMING);

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final HumanRelationRecordMapper humanRelationRecordMapper;

    public HumanRelationAccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        HumanRelationRecordMapper humanRelationRecordMapper
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.humanRelationRecordMapper = humanRelationRecordMapper;
    }

    public HumanRelationSummaryResponse summary(Long userId, Long accountId) {
        List<AccountEntity> accounts = accountId == null
            ? loadHumanRelationAccounts(userId)
            : List.of(requireHumanRelationAccount(userId, accountId));
        List<HumanRelationRecordEntity> records = loadHumanRelationRecords(userId, accountId);
        if (accounts.isEmpty()) {
            return emptySummary();
        }

        BigDecimal outgoingTotal = sumByDirection(records, DIRECTION_OUTGOING);
        BigDecimal incomingTotal = sumByDirection(records, DIRECTION_INCOMING);

        HumanRelationSummaryResponse response = new HumanRelationSummaryResponse();
        response.setNetAmount(outgoingTotal.subtract(incomingTotal).setScale(2, RoundingMode.HALF_UP));
        response.setOutgoingTotal(outgoingTotal);
        response.setIncomingTotal(incomingTotal);
        response.setAccountCount(accounts.size());
        response.setRecordCount(records.size());
        return response;
    }

    public List<HumanRelationRecordResponse> listRecords(Long userId, Long accountId) {
        List<HumanRelationRecordEntity> records = loadHumanRelationRecords(userId, accountId);
        if (records.isEmpty()) {
            return List.of();
        }

        Set<Long> accountIds = records.stream()
            .map(HumanRelationRecordEntity::getAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Set<Long> fundingAccountIds = records.stream()
            .map(HumanRelationRecordEntity::getFundingAccountId)
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
    public HumanRelationRecordResponse createRecord(HumanRelationRecordRequest request) {
        String direction = requireDirection(request.getDirection());
        AccountEntity account = requireHumanRelationAccount(request.getUserId(), request.getAccountId());
        AccountEntity fundingAccount = findCashFundingAccount(request.getUserId(), request.getFundingAccountId());
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        HumanRelationRecordEntity entity = new HumanRelationRecordEntity();
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
        humanRelationRecordMapper.insert(entity);

        return toResponse(humanRelationRecordMapper.selectById(entity.getId()), account, fundingAccount);
    }

    @Transactional
    public Optional<HumanRelationRecordResponse> updateRecord(Long id, HumanRelationRecordRequest request) {
        HumanRelationRecordEntity entity = humanRelationRecordMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }

        String direction = requireDirection(request.getDirection());
        AccountEntity account = requireHumanRelationAccount(request.getUserId(), request.getAccountId());
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
        humanRelationRecordMapper.updateById(entity);

        return Optional.of(toResponse(humanRelationRecordMapper.selectById(id), account, fundingAccount));
    }

    @Transactional
    public boolean deleteRecord(Long id, Long userId) {
        HumanRelationRecordEntity entity = humanRelationRecordMapper.selectById(id);
        if (entity == null || !ACTIVE_STATUS.equals(entity.getStatus()) || !userId.equals(entity.getUserId())) {
            return false;
        }

        rollbackFundingAccountChange(entity);
        entity.setStatus(VOIDED_STATUS);
        humanRelationRecordMapper.updateById(entity);
        return true;
    }

    private List<AccountEntity> loadHumanRelationAccounts(Long userId) {
        AccountTypeEntity type = loadHumanRelationType();
        if (type == null) {
            return Collections.emptyList();
        }
        return accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(userId != null, AccountEntity::getUserId, userId)
            .eq(AccountEntity::getAccountTypeId, type.getId())
            .eq(AccountEntity::getStatus, ACTIVE_STATUS)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId));
    }

    private AccountTypeEntity loadHumanRelationType() {
        return accountTypeMapper.selectOne(new LambdaQueryWrapper<AccountTypeEntity>()
            .eq(AccountTypeEntity::getCode, HUMAN_RELATION_ACCOUNT_CODE)
            .eq(AccountTypeEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1"));
    }

    private List<HumanRelationRecordEntity> loadHumanRelationRecords(Long userId, Long accountId) {
        return humanRelationRecordMapper.selectList(new LambdaQueryWrapper<HumanRelationRecordEntity>()
            .eq(userId != null, HumanRelationRecordEntity::getUserId, userId)
            .eq(accountId != null, HumanRelationRecordEntity::getAccountId, accountId)
            .eq(HumanRelationRecordEntity::getStatus, ACTIVE_STATUS)
            .orderByDesc(HumanRelationRecordEntity::getOccurredAt)
            .orderByDesc(HumanRelationRecordEntity::getId));
    }

    private AccountEntity requireHumanRelationAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId()) || !ACTIVE_STATUS.equals(account.getStatus())) {
            throw new IllegalArgumentException("人情账户不存在");
        }

        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !HUMAN_RELATION_ACCOUNT_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("请选择有效的人情账户");
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
            throw new IllegalArgumentException("请选择送出或收到");
        }
        String normalizedDirection = direction.trim().toLowerCase();
        if (!HUMAN_RELATION_DIRECTIONS.contains(normalizedDirection)) {
            throw new IllegalArgumentException("人情方向无效");
        }
        return normalizedDirection;
    }

    private BigDecimal sumByDirection(List<HumanRelationRecordEntity> records, String direction) {
        return records.stream()
            .filter(record -> direction.equals(record.getDirection()))
            .map(HumanRelationRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private void applyFundingAccountChange(AccountEntity fundingAccount, String direction, BigDecimal amount) {
        if (fundingAccount == null) {
            return;
        }
        if (DIRECTION_OUTGOING.equals(direction)) {
            deductFundingAccount(fundingAccount, amount);
            return;
        }
        creditFundingAccount(fundingAccount, amount);
    }

    private void rollbackFundingAccountChange(HumanRelationRecordEntity entity) {
        if (entity.getFundingAccountId() == null || entity.getAmount() == null) {
            return;
        }
        AccountEntity fundingAccount = requireCashFundingAccount(entity.getUserId(), entity.getFundingAccountId());
        BigDecimal amount = entity.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (DIRECTION_OUTGOING.equals(entity.getDirection())) {
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

    private HumanRelationRecordResponse toResponse(
        HumanRelationRecordEntity entity,
        AccountEntity account,
        AccountEntity fundingAccount
    ) {
        HumanRelationRecordResponse response = new HumanRelationRecordResponse();
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

    private HumanRelationSummaryResponse emptySummary() {
        HumanRelationSummaryResponse response = new HumanRelationSummaryResponse();
        response.setNetAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setOutgoingTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setIncomingTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setAccountCount(0);
        response.setRecordCount(0);
        return response;
    }
}
