package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.app.dto.internal.PhotographyIncomeRequest;
import com.example.app.entity.AccountEntity;
import com.example.app.entity.AccountTypeEntity;
import com.example.app.entity.CategoryEntity;
import com.example.app.entity.TransactionEntity;
import com.example.app.mapper.AccountMapper;
import com.example.app.mapper.AccountTypeMapper;
import com.example.app.mapper.CategoryMapper;
import com.example.app.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 摄影订单相关资金操作（原 InternalFinanceController 内联逻辑）。
 * 服务合并后同一 JVM 内供 InternalFinanceController 与 tool 侧 FinanceClient 共用。
 */
@Service
public class PhotographyAccountingService {

    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String CATEGORY_TYPE_INCOME = "income";
    private static final String PHOTOGRAPHY_CATEGORY_NAME = "摄影收入";
    private static final String TYPE_INCOME = "income";
    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_VOIDED = "voided";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final DateTimeFormatter TRANSACTION_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;

    public PhotographyAccountingService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        CategoryMapper categoryMapper,
        TransactionMapper transactionMapper
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
    }

    public record CashAccountView(Long id, Long userId, String name) {
    }

    /** 校验现金账户（归属+现金类型+可用），失败抛 IllegalArgumentException */
    public AccountEntity requireCashAccount(Long userId, Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("请选择现金账户");
        }
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("现金账户不存在");
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("请选择有效的现金账户");
        }
        if (!"active".equals(account.getStatus())) {
            throw new IllegalArgumentException("请选择可用的现金账户");
        }
        return account;
    }

    /** 批量获取现金账户名称（展示用） */
    public List<CashAccountView> loadCashAccounts(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return accountMapper.selectBatchIds(ids).stream()
            .filter(account -> userId.equals(account.getUserId()))
            .map(account -> new CashAccountView(account.getId(), account.getUserId(), account.getName()))
            .toList();
    }

    /** 创建摄影收入流水并原子加余额，返回流水 id */
    @Transactional
    public Long createPhotographyIncome(PhotographyIncomeRequest request) {
        if (request.userId() == null || request.accountId() == null
            || request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("入账参数无效");
        }
        AccountEntity account = requireCashAccount(request.userId(), request.accountId());
        CategoryEntity category = ensurePhotographyIncomeCategory(request.userId());
        TransactionEntity transaction = createIncomeTransaction(account, category, request);
        return transaction.getId();
    }

    public record VoidIncomeResult(Long accountId, BigDecimal refundedAmount) {
    }

    /** 作废关联收入流水并原子扣回余额；已作废的流水幂等返回 refundedAmount=0 */
    @Transactional
    public VoidIncomeResult voidPhotographyIncome(Long transactionId, Long userId) {
        TransactionEntity transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("关联流水不存在");
        }
        if (!userId.equals(transaction.getUserId())) {
            throw new IllegalArgumentException("无权处理该关联流水");
        }
        if (!STATUS_NORMAL.equals(transaction.getStatus())) {
            return new VoidIncomeResult(transaction.getAccountId(), BigDecimal.ZERO);
        }

        int updated = accountMapper.subtractBalanceIfSufficient(transaction.getAccountId(), transaction.getAmount());
        if (updated == 0) {
            throw new IllegalArgumentException("删除后账户余额不足");
        }
        transactionMapper.update(null, new LambdaUpdateWrapper<TransactionEntity>()
            .eq(TransactionEntity::getId, transactionId)
            .set(TransactionEntity::getStatus, STATUS_VOIDED));
        return new VoidIncomeResult(transaction.getAccountId(), transaction.getAmount());
    }

    private CategoryEntity ensurePhotographyIncomeCategory(Long userId) {
        List<CategoryEntity> categories = categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getType, CATEGORY_TYPE_INCOME)
            .eq(CategoryEntity::getName, PHOTOGRAPHY_CATEGORY_NAME)
            .eq(CategoryEntity::getStatus, "active")
            .and(query -> query
                .eq(CategoryEntity::getUserId, userId)
                .or()
                .isNull(CategoryEntity::getUserId))
            .orderByDesc(CategoryEntity::getUserId)
            .orderByAsc(CategoryEntity::getSortOrder)
            .last("LIMIT 1"));
        if (!categories.isEmpty()) {
            return categories.get(0);
        }

        CategoryEntity category = new CategoryEntity();
        category.setUserId(userId);
        category.setName(PHOTOGRAPHY_CATEGORY_NAME);
        category.setType(CATEGORY_TYPE_INCOME);
        category.setIcon("camera");
        category.setColor("#1D4ED8");
        category.setSystem(false);
        category.setSortOrder(30);
        category.setStatus("active");
        category.setRemark("摄影订单产生的收入");
        categoryMapper.insert(category);
        return category;
    }

    private TransactionEntity createIncomeTransaction(
        AccountEntity account,
        CategoryEntity category,
        PhotographyIncomeRequest request
    ) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setUserId(request.userId());
        transaction.setType(TYPE_INCOME);
        transaction.setAmount(request.amount());
        transaction.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        transaction.setAccountId(account.getId());
        transaction.setCategoryId(category.getId());
        transaction.setTitle(request.title());
        transaction.setRemark(request.remark());
        transaction.setOccurredAt(request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt());
        transaction.setStatus(STATUS_NORMAL);
        transactionMapper.insert(transaction);

        accountMapper.addBalance(account.getId(), request.amount());
        return transaction;
    }

    private String generateTransactionNo() {
        String timePart = LocalDateTime.now().format(TRANSACTION_NO_TIME_FORMAT);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "IN" + timePart + randomPart;
    }
}
