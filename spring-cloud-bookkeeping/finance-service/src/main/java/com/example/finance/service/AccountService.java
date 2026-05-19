package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountRequest;
import com.example.finance.dto.AccountResponse;
import com.example.finance.dto.AccountSortOrderRequest;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.InvestmentDividendRecordEntity;
import com.example.finance.entity.InvestmentPositionEntity;
import com.example.finance.entity.InvestmentTransactionEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.InvestmentDividendRecordMapper;
import com.example.finance.mapper.InvestmentPositionMapper;
import com.example.finance.mapper.InvestmentTransactionMapper;
import com.example.finance.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final String ACTIVE_POSITION_STATUS = "active";
    private static final String GOLD_ACCOUNT_TYPE_CODE = "gold";
    private static final String INVESTMENT_ACCOUNT_TYPE_CODE = "investment";
    private static final Set<String> POSITION_BALANCE_ACCOUNT_TYPES = Set.of("investment", "gold");

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final InvestmentPositionMapper investmentPositionMapper;
    private final InvestmentTransactionMapper investmentTransactionMapper;
    private final InvestmentDividendRecordMapper investmentDividendRecordMapper;
    private final TransactionMapper transactionMapper;
    private final GoldPriceService goldPriceService;

    public AccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        InvestmentPositionMapper investmentPositionMapper,
        InvestmentTransactionMapper investmentTransactionMapper,
        InvestmentDividendRecordMapper investmentDividendRecordMapper,
        TransactionMapper transactionMapper,
        GoldPriceService goldPriceService
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.investmentPositionMapper = investmentPositionMapper;
        this.investmentTransactionMapper = investmentTransactionMapper;
        this.investmentDividendRecordMapper = investmentDividendRecordMapper;
        this.transactionMapper = transactionMapper;
        this.goldPriceService = goldPriceService;
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

    @Transactional
    public boolean delete(Long id) {
        AccountEntity account = accountMapper.selectById(id);
        if (account == null) {
            return false;
        }

        transactionMapper.delete(new LambdaQueryWrapper<TransactionEntity>()
            .eq(TransactionEntity::getAccountId, id)
            .or()
            .eq(TransactionEntity::getFromAccountId, id)
            .or()
            .eq(TransactionEntity::getToAccountId, id));
        investmentTransactionMapper.delete(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getAccountId, id));
        investmentDividendRecordMapper.delete(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
            .eq(InvestmentDividendRecordEntity::getAccountId, id));
        investmentPositionMapper.delete(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getAccountId, id));
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
        response.setCurrentBalance(resolveCurrentBalance(entity, accountType));
        response.setIncludeInNetWorth(entity.getIncludeInNetWorth());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private BigDecimal resolveCurrentBalance(AccountEntity entity, AccountTypeEntity accountType) {
        if (accountType == null || entity == null || !POSITION_BALANCE_ACCOUNT_TYPES.contains(accountType.getCode())) {
            return entity == null || entity.getCurrentBalance() == null ? BigDecimal.ZERO : entity.getCurrentBalance();
        }
        List<InvestmentPositionEntity> positions = investmentPositionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(InvestmentPositionEntity::getAccountId, entity.getId())
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_POSITION_STATUS));

        if (GOLD_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            BigDecimal realtimePrice = resolveRealtimeGoldPrice(positions);
            BigDecimal marketValue = positions.stream()
                .map(InvestmentPositionEntity::getHoldingQuantity)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(realtimePrice);
            return marketValue.setScale(2, RoundingMode.HALF_UP);
        }

        if (INVESTMENT_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            BigDecimal marketValue = positions.stream()
                .map(InvestmentPositionEntity::getMarketValue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return marketValue.setScale(2, RoundingMode.HALF_UP);
        }

        return entity.getCurrentBalance() == null ? BigDecimal.ZERO : entity.getCurrentBalance();
    }

    private BigDecimal resolveRealtimeGoldPrice(List<InvestmentPositionEntity> positions) {
        try {
            BigDecimal price = goldPriceService.getGoldPrice("1d").getSpotGold().getPrice();
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                return price.setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {
            // Fall back to stored price when realtime gold quote is temporarily unavailable.
        }

        return positions.stream()
            .map(InvestmentPositionEntity::getCurrentPrice)
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .map(value -> value.setScale(2, RoundingMode.HALF_UP))
            .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
