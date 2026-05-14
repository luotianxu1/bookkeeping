package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.TransactionRequest;
import com.example.finance.dto.TransactionResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.CategoryEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.CategoryMapper;
import com.example.finance.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String DEFAULT_STATUS = "normal";
    private static final DateTimeFormatter TRANSACTION_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final CategoryMapper categoryMapper;

    public TransactionService(
        TransactionMapper transactionMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        CategoryMapper categoryMapper
    ) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.categoryMapper = categoryMapper;
    }

    public List<TransactionResponse> list(Long userId, String type) {
        LambdaQueryWrapper<TransactionEntity> wrapper = new LambdaQueryWrapper<TransactionEntity>()
            .eq(userId != null, TransactionEntity::getUserId, userId)
            .eq(StringUtils.hasText(type), TransactionEntity::getType, type)
            .eq(TransactionEntity::getStatus, DEFAULT_STATUS)
            .orderByDesc(TransactionEntity::getOccurredAt)
            .orderByDesc(TransactionEntity::getId);

        List<TransactionEntity> transactions = transactionMapper.selectList(wrapper);
        return toResponses(transactions);
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        String type = requireIncomeOrExpense(request.getType());
        AccountEntity account = requireCashAccount(request.getUserId(), request.getAccountId());
        CategoryEntity category = requireCategory(request.getUserId(), request.getCategoryId(), type);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        TransactionEntity entity = new TransactionEntity();
        entity.setTransactionNo(generateTransactionNo(type));
        entity.setUserId(request.getUserId());
        entity.setType(type);
        entity.setAmount(amount);
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setAccountId(account.getId());
        entity.setCategoryId(category.getId());
        entity.setTitle(buildTitle(request, category));
        entity.setRemark(request.getRemark());
        entity.setOccurredAt(request.getOccurredAt());
        entity.setStatus(DEFAULT_STATUS);
        transactionMapper.insert(entity);

        updateAccountBalance(account, type, amount);

        return toResponse(transactionMapper.selectById(entity.getId()), account, category);
    }

    private String requireIncomeOrExpense(String type) {
        if (!TYPE_EXPENSE.equals(type) && !TYPE_INCOME.equals(type)) {
            throw new IllegalArgumentException("当前只支持支出和收入");
        }
        return type;
    }

    private AccountEntity requireCashAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("账户不存在");
        }

        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("请选择现金账户");
        }
        return account;
    }

    private CategoryEntity requireCategory(Long userId, Long categoryId, String type) {
        CategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("分类不存在");
        }
        if (category.getUserId() != null && !userId.equals(category.getUserId())) {
            throw new IllegalArgumentException("分类不存在");
        }
        if (!type.equals(category.getType())) {
            throw new IllegalArgumentException("分类类型与流水类型不一致");
        }
        return category;
    }

    private String buildTitle(TransactionRequest request, CategoryEntity category) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle();
        }
        if (StringUtils.hasText(request.getRemark())) {
            return request.getRemark().length() > 120 ? request.getRemark().substring(0, 120) : request.getRemark();
        }
        return category.getName();
    }

    private void updateAccountBalance(AccountEntity account, String type, BigDecimal amount) {
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        BigDecimal nextBalance = TYPE_INCOME.equals(type)
            ? currentBalance.add(amount)
            : currentBalance.subtract(amount);
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("账户余额不足");
        }
        account.setCurrentBalance(nextBalance);
        accountMapper.updateById(account);
    }

    private String generateTransactionNo(String type) {
        String prefix = TYPE_INCOME.equals(type) ? "IN" : "EX";
        String timePart = LocalDateTime.now().format(TRANSACTION_NO_TIME_FORMAT);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return prefix + timePart + randomPart;
    }

    private List<TransactionResponse> toResponses(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }

        Set<Long> accountIds = transactions.stream()
            .map(TransactionEntity::getAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Set<Long> categoryIds = transactions.stream()
            .map(TransactionEntity::getCategoryId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<Long, AccountEntity> accounts = accountIds.isEmpty()
            ? Map.of()
            : accountMapper.selectBatchIds(accountIds).stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Map<Long, CategoryEntity> categories = categoryIds.isEmpty()
            ? Map.of()
            : categoryMapper.selectBatchIds(categoryIds).stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));

        return transactions.stream()
            .map(transaction -> toResponse(
                transaction,
                accounts.get(transaction.getAccountId()),
                categories.get(transaction.getCategoryId())
            ))
            .toList();
    }

    private TransactionResponse toResponse(TransactionEntity entity, AccountEntity account, CategoryEntity category) {
        TransactionResponse response = new TransactionResponse();
        response.setId(entity.getId());
        response.setTransactionNo(entity.getTransactionNo());
        response.setUserId(entity.getUserId());
        response.setType(entity.getType());
        response.setAmount(entity.getAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setAccountId(entity.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setCategoryId(entity.getCategoryId());
        response.setCategoryName(category == null ? null : category.getName());
        response.setCategoryIcon(category == null ? null : category.getIcon());
        response.setTitle(entity.getTitle());
        response.setRemark(entity.getRemark());
        response.setOccurredAt(entity.getOccurredAt());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
