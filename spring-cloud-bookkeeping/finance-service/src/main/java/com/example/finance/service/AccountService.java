package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountRequest;
import com.example.finance.dto.AccountResponse;
import com.example.finance.dto.AccountSortOrderRequest;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.DebtRecordEntity;
import com.example.finance.entity.InvestmentDividendRecordEntity;
import com.example.finance.entity.InvestmentPositionEntity;
import com.example.finance.entity.InvestmentTransactionEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.DebtRecordMapper;
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
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String DEBT_DIRECTION_PAYABLE = "payable";
    private static final Set<String> DEBT_ACCOUNT_TYPE_CODES = Set.of("debt", "loan_receivable", "loan_payable");
    private static final String DEBT_RECORD_STATUS_ACTIVE = "active";
    private static final Set<String> POSITION_BALANCE_ACCOUNT_TYPES = Set.of("investment", "gold");

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final DebtRecordMapper debtRecordMapper;
    private final InvestmentPositionMapper investmentPositionMapper;
    private final InvestmentTransactionMapper investmentTransactionMapper;
    private final InvestmentDividendRecordMapper investmentDividendRecordMapper;
    private final TransactionMapper transactionMapper;
    private final GoldPriceService goldPriceService;

    public AccountService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        DebtRecordMapper debtRecordMapper,
        InvestmentPositionMapper investmentPositionMapper,
        InvestmentTransactionMapper investmentTransactionMapper,
        InvestmentDividendRecordMapper investmentDividendRecordMapper,
        TransactionMapper transactionMapper,
        GoldPriceService goldPriceService
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.debtRecordMapper = debtRecordMapper;
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
        validateContactRequired(accountType, request.getContactId());
        validateDebtAccountContactUnique(accountType, request.getUserId(), request.getContactId(), null);
        validateNameUnique(request.getUserId(), request.getName(), null);

        AccountEntity entity = new AccountEntity();
        fillEntity(entity, request, accountType);
        accountMapper.insert(entity);

        return toResponse(accountMapper.selectById(entity.getId()), accountType);
    }

    public Optional<AccountResponse> update(Long id, AccountRequest request) {
        AccountEntity entity = accountMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        AccountTypeEntity accountType = requireAccountType(request.getAccountTypeId());
        validateContactRequired(accountType, request.getContactId());
        validateDebtAccountContactUnique(accountType, request.getUserId(), request.getContactId(), id);
        validateNameUnique(request.getUserId(), request.getName(), id);
        fillEntity(entity, request, accountType);
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

        AccountTypeEntity accountType = loadAccountType(account.getAccountTypeId());
        if (accountType != null && CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            Long referenceCount = debtRecordMapper.selectCount(new LambdaQueryWrapper<DebtRecordEntity>()
                .eq(DebtRecordEntity::getFundingAccountId, id)
                .eq(DebtRecordEntity::getStatus, DEBT_RECORD_STATUS_ACTIVE));
            if (referenceCount != null && referenceCount > 0) {
                throw new IllegalArgumentException("该现金账户已关联债务记录，暂时不能删除");
            }
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
        debtRecordMapper.delete(new LambdaQueryWrapper<DebtRecordEntity>()
            .eq(DebtRecordEntity::getAccountId, id));
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

    private void validateContactRequired(AccountTypeEntity accountType, Long contactId) {
        if (accountType != null && DEBT_ACCOUNT_TYPE_CODES.contains(accountType.getCode()) && contactId == null) {
            throw new IllegalArgumentException("债务账户必须关联联系人");
        }
    }

    private void validateDebtAccountContactUnique(AccountTypeEntity accountType, Long userId, Long contactId, Long ignoredId) {
        if (accountType == null || !DEBT_ACCOUNT_TYPE_CODES.contains(accountType.getCode()) || contactId == null) {
            return;
        }
        AccountEntity existingAccount = accountMapper.selectOne(new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUserId, userId)
            .eq(AccountEntity::getContactId, contactId)
            .eq(AccountEntity::getAccountTypeId, accountType.getId())
            .ne(ignoredId != null, AccountEntity::getId, ignoredId)
            .last("LIMIT 1"));
        if (existingAccount != null) {
            throw new IllegalArgumentException("该联系人已存在债务账户");
        }
    }

    private void fillEntity(AccountEntity entity, AccountRequest request, AccountTypeEntity accountType) {
        entity.setUserId(request.getUserId());
        entity.setAccountTypeId(request.getAccountTypeId());
        entity.setContactId(request.getContactId());
        entity.setName(request.getName());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setCurrentBalance(accountType != null && DEBT_ACCOUNT_TYPE_CODES.contains(accountType.getCode())
            ? BigDecimal.ZERO
            : request.getCurrentBalance() != null ? request.getCurrentBalance() : BigDecimal.ZERO);
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
        response.setContactId(entity.getContactId());
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
        if (accountType != null && entity != null && DEBT_ACCOUNT_TYPE_CODES.contains(accountType.getCode())) {
            return resolveDebtBalance(entity.getId());
        }
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

    private BigDecimal resolveDebtBalance(Long accountId) {
        List<DebtRecordEntity> records = debtRecordMapper.selectList(new LambdaQueryWrapper<DebtRecordEntity>()
            .eq(DebtRecordEntity::getAccountId, accountId)
            .eq(DebtRecordEntity::getStatus, DEBT_RECORD_STATUS_ACTIVE));
        BigDecimal payableTotal = records.stream()
            .filter(record -> DEBT_DIRECTION_PAYABLE.equals(record.getDirection()))
            .map(DebtRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivableTotal = records.stream()
            .filter(record -> !DEBT_DIRECTION_PAYABLE.equals(record.getDirection()))
            .map(DebtRecordEntity::getAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return receivableTotal.subtract(payableTotal).setScale(2, RoundingMode.HALF_UP);
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
