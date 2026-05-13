package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountTypeRequest;
import com.example.finance.dto.AccountTypeResponse;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.mapper.AccountTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class AccountTypeService {

    private static final String DEFAULT_STATUS = "active";

    private final AccountTypeMapper accountTypeMapper;

    public AccountTypeService(AccountTypeMapper accountTypeMapper) {
        this.accountTypeMapper = accountTypeMapper;
    }

    public List<AccountTypeResponse> list(String category, String status) {
        LambdaQueryWrapper<AccountTypeEntity> wrapper = new LambdaQueryWrapper<AccountTypeEntity>()
            .eq(StringUtils.hasText(category), AccountTypeEntity::getCategory, category)
            .eq(StringUtils.hasText(status), AccountTypeEntity::getStatus, status)
            .orderByAsc(AccountTypeEntity::getSortOrder)
            .orderByAsc(AccountTypeEntity::getId);

        return accountTypeMapper.selectList(wrapper).stream()
            .map(this::toResponse)
            .toList();
    }

    public Optional<AccountTypeResponse> getById(Long id) {
        return Optional.ofNullable(accountTypeMapper.selectById(id)).map(this::toResponse);
    }

    public AccountTypeResponse create(AccountTypeRequest request) {
        validateCodeUnique(request.getCode(), null);
        AccountTypeEntity entity = new AccountTypeEntity();
        fillEntity(entity, request);
        accountTypeMapper.insert(entity);
        return toResponse(accountTypeMapper.selectById(entity.getId()));
    }

    public Optional<AccountTypeResponse> update(Long id, AccountTypeRequest request) {
        AccountTypeEntity entity = accountTypeMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        validateCodeUnique(request.getCode(), id);
        fillEntity(entity, request);
        accountTypeMapper.updateById(entity);
        return Optional.of(toResponse(accountTypeMapper.selectById(id)));
    }

    public boolean delete(Long id) {
        return accountTypeMapper.deleteById(id) > 0;
    }

    private void validateCodeUnique(String code, Long ignoredId) {
        LambdaQueryWrapper<AccountTypeEntity> wrapper = new LambdaQueryWrapper<AccountTypeEntity>()
            .eq(AccountTypeEntity::getCode, code)
            .ne(ignoredId != null, AccountTypeEntity::getId, ignoredId)
            .last("LIMIT 1");
        if (accountTypeMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("账户类型编码已存在");
        }
    }

    private void fillEntity(AccountTypeEntity entity, AccountTypeRequest request) {
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setCategory(request.getCategory());
        entity.setBalanceDirection(request.getBalanceDirection());
        entity.setIncludeInNetWorthDefault(request.getIncludeInNetWorthDefault());
        entity.setAllowOverdraft(request.getAllowOverdraft());
        entity.setSystem(request.getSystem() != null ? request.getSystem() : Boolean.FALSE);
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setRemark(request.getRemark());
    }

    private AccountTypeResponse toResponse(AccountTypeEntity entity) {
        AccountTypeResponse response = new AccountTypeResponse();
        response.setId(entity.getId());
        response.setCode(entity.getCode());
        response.setName(entity.getName());
        response.setCategory(entity.getCategory());
        response.setBalanceDirection(entity.getBalanceDirection());
        response.setIncludeInNetWorthDefault(entity.getIncludeInNetWorthDefault());
        response.setAllowOverdraft(entity.getAllowOverdraft());
        response.setSystem(entity.getSystem());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
