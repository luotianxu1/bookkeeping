package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountRequest;
import com.example.finance.dto.AccountResponse;
import com.example.finance.dto.AccountSortOrderRequest;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String DEFAULT_STATUS = "active";

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;

    public AccountService(AccountMapper accountMapper, AccountTypeMapper accountTypeMapper) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
    }

    public List<AccountResponse> list(Long userId, Long accountTypeId, String status) {
        LambdaQueryWrapper<AccountEntity> wrapper = new LambdaQueryWrapper<AccountEntity>()
            .eq(userId != null, AccountEntity::getUserId, userId)
            .eq(accountTypeId != null, AccountEntity::getAccountTypeId, accountTypeId)
            .eq(StringUtils.hasText(status), AccountEntity::getStatus, status)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId);

        List<AccountEntity> accounts = accountMapper.selectList(wrapper);
        return toResponses(accounts);
    }

    public Optional<AccountResponse> getById(Long id) {
        AccountEntity account = accountMapper.selectById(id);
        if (account == null) {
            return Optional.empty();
        }
        return Optional.of(toResponse(account, loadAccountType(account.getAccountTypeId())));
    }

    public AccountResponse create(AccountRequest request) {
        AccountTypeEntity accountType = requireAccountType(request.getAccountTypeId());
        validateNameUnique(request.getUserId(), request.getName(), null);

        AccountEntity entity = new AccountEntity();
        fillEntity(entity, request);
        accountMapper.insert(entity);

        return toResponse(accountMapper.selectById(entity.getId()), accountType);
    }

    public Optional<AccountResponse> update(Long id, AccountRequest request) {
        AccountEntity entity = accountMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        AccountTypeEntity accountType = requireAccountType(request.getAccountTypeId());
        validateNameUnique(request.getUserId(), request.getName(), id);
        fillEntity(entity, request);
        accountMapper.updateById(entity);

        return Optional.of(toResponse(accountMapper.selectById(id), accountType));
    }

    @Transactional
    public void updateSortOrders(AccountSortOrderRequest request) {
        for (AccountSortOrderRequest.AccountSortOrderItem item : request.getItems()) {
            AccountEntity account = accountMapper.selectById(item.getId());
            if (account == null || !request.getUserId().equals(account.getUserId())) {
                throw new IllegalArgumentException("账户不存在");
            }
            account.setSortOrder(item.getSortOrder());
            accountMapper.updateById(account);
        }
    }

    public boolean delete(Long id) {
        return accountMapper.deleteById(id) > 0;
    }

    private AccountTypeEntity requireAccountType(Long accountTypeId) {
        AccountTypeEntity accountType = accountTypeMapper.selectById(accountTypeId);
        if (accountType == null) {
            throw new IllegalArgumentException("账户类型不存在");
        }
        return accountType;
    }

    private AccountTypeEntity loadAccountType(Long accountTypeId) {
        return accountTypeMapper.selectById(accountTypeId);
    }

    private void validateNameUnique(Long userId, String name, Long ignoredId) {
        LambdaQueryWrapper<AccountEntity> wrapper = new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUserId, userId)
            .eq(AccountEntity::getName, name)
            .ne(ignoredId != null, AccountEntity::getId, ignoredId)
            .last("LIMIT 1");
        if (accountMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("账户名称已存在");
        }
    }

    private void fillEntity(AccountEntity entity, AccountRequest request) {
        entity.setUserId(request.getUserId());
        entity.setAccountTypeId(request.getAccountTypeId());
        entity.setName(request.getName());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setCurrentBalance(request.getCurrentBalance() != null ? request.getCurrentBalance() : BigDecimal.ZERO);
        entity.setIncludeInNetWorth(request.getIncludeInNetWorth());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setRemark(request.getRemark());
    }

    private List<AccountResponse> toResponses(List<AccountEntity> accounts) {
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> accountTypeIds = accounts.stream()
            .map(AccountEntity::getAccountTypeId)
            .collect(Collectors.toSet());
        Map<Long, AccountTypeEntity> accountTypes = accountTypeMapper.selectBatchIds(accountTypeIds).stream()
            .collect(Collectors.toMap(AccountTypeEntity::getId, Function.identity()));

        return accounts.stream()
            .map(account -> toResponse(account, accountTypes.get(account.getAccountTypeId())))
            .toList();
    }

    private AccountResponse toResponse(AccountEntity entity, AccountTypeEntity accountType) {
        AccountResponse response = new AccountResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setAccountTypeId(entity.getAccountTypeId());
        if (accountType != null) {
            response.setAccountTypeCode(accountType.getCode());
            response.setAccountTypeName(accountType.getName());
        }
        response.setName(entity.getName());
        response.setIcon(entity.getIcon());
        response.setColor(entity.getColor());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setCurrentBalance(entity.getCurrentBalance());
        response.setIncludeInNetWorth(entity.getIncludeInNetWorth());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
