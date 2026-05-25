package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.PhotographyOrderCollectFinalRequest;
import com.example.tool.dto.PhotographyOrderRequest;
import com.example.tool.dto.PhotographyOrderResponse;
import com.example.tool.entity.AccountEntity;
import com.example.tool.entity.AccountTypeEntity;
import com.example.tool.entity.CategoryEntity;
import com.example.tool.entity.PhotographyOrderEntity;
import com.example.tool.entity.TransactionEntity;
import com.example.tool.mapper.AccountMapper;
import com.example.tool.mapper.AccountTypeMapper;
import com.example.tool.mapper.CategoryMapper;
import com.example.tool.mapper.PhotographyOrderMapper;
import com.example.tool.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PhotographyOrderService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_SHOT = "shot";
    private static final String STATUS_ALL = "all";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String CATEGORY_TYPE_INCOME = "income";
    private static final String PHOTOGRAPHY_CATEGORY_NAME = "摄影收入";
    private static final String TRANSACTION_TYPE_INCOME = "income";
    private static final String TRANSACTION_STATUS_NORMAL = "normal";
    private static final String TRANSACTION_STATUS_VOIDED = "voided";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Set<String> ORDER_TYPES = Set.of(
        "first_birthday",
        "hundred_days",
        "engagement",
        "thanks_banquet",
        "wedding",
        "graduation"
    );

    private final PhotographyOrderMapper photographyOrderMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;

    public PhotographyOrderService(
        PhotographyOrderMapper photographyOrderMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        CategoryMapper categoryMapper,
        TransactionMapper transactionMapper
    ) {
        this.photographyOrderMapper = photographyOrderMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
    }

    public List<PhotographyOrderResponse> list(Long userId, String status, String keyword) {
        LambdaQueryWrapper<PhotographyOrderEntity> wrapper = new LambdaQueryWrapper<PhotographyOrderEntity>()
            .eq(userId != null, PhotographyOrderEntity::getUserId, userId)
            .eq(shouldFilterStatus(status), PhotographyOrderEntity::getStatus, normalizeStatus(status))
            .and(StringUtils.hasText(keyword), query -> query
                .like(PhotographyOrderEntity::getCustomerName, keyword.trim())
                .or()
                .like(PhotographyOrderEntity::getContactInfo, keyword.trim())
                .or()
                .like(PhotographyOrderEntity::getAddress, keyword.trim())
                .or()
                .like(PhotographyOrderEntity::getRemark, keyword.trim()))
            .orderByAsc(PhotographyOrderEntity::getShootAt)
            .orderByAsc(PhotographyOrderEntity::getSortOrder)
            .orderByDesc(PhotographyOrderEntity::getId);

        return toResponses(photographyOrderMapper.selectList(wrapper));
    }

    public Optional<PhotographyOrderResponse> getById(Long id) {
        PhotographyOrderEntity entity = photographyOrderMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        return toResponses(List.of(entity)).stream().findFirst();
    }

    @Transactional
    public PhotographyOrderResponse create(PhotographyOrderRequest request) {
        String orderType = normalizeOrderType(request.getOrderType());
        BigDecimal totalAmount = normalizeAmount(request.getTotalAmount(), "总金额");
        BigDecimal depositAmount = normalizeAmount(request.getDepositAmount(), "订金");
        BigDecimal finalAmount = normalizeAmount(request.getFinalAmount(), "尾款");
        validateAmountRelation(totalAmount, depositAmount, finalAmount);

        PhotographyOrderEntity entity = new PhotographyOrderEntity();
        entity.setOrderNo(generateOrderNo());
        entity.setUserId(request.getUserId());
        entity.setCustomerName(request.getCustomerName().trim());
        entity.setContactInfo(trimNullable(request.getContactInfo()));
        entity.setOrderType(orderType);
        entity.setShootAt(request.getShootAt());
        entity.setStatus(STATUS_PENDING);
        entity.setTotalAmount(totalAmount);
        entity.setDepositAmount(depositAmount);
        entity.setFinalAmount(finalAmount);
        entity.setAddress(trimNullable(request.getAddress()));
        entity.setRemark(trimNullable(request.getRemark()));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());

        if (depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            AccountEntity depositAccount = requireCashAccount(request.getUserId(), request.getDepositAccountId());
            CategoryEntity incomeCategory = requirePhotographyIncomeCategory(request.getUserId());
            TransactionEntity depositTransaction = createIncomeTransaction(
                request.getUserId(),
                depositAccount,
                incomeCategory,
                depositAmount,
                request.getShootAt(),
                buildTransactionTitle(entity.getCustomerName(), "订金"),
                buildTransactionRemark(orderType, entity.getAddress(), entity.getRemark())
            );
            entity.setDepositAccountId(depositAccount.getId());
            entity.setDepositTransactionId(depositTransaction.getId());
            entity.setDepositReceivedAt(depositTransaction.getOccurredAt());
        }

        photographyOrderMapper.insert(entity);
        return toResponses(List.of(photographyOrderMapper.selectById(entity.getId()))).get(0);
    }

    @Transactional
    public PhotographyOrderResponse collectFinal(Long id, PhotographyOrderCollectFinalRequest request) {
        PhotographyOrderEntity entity = requireOrder(id, request.getUserId());
        if (entity.getFinalTransactionId() != null || entity.getFinalReceivedAt() != null) {
            throw new IllegalArgumentException("该订单尾款已收取");
        }

        if (STATUS_SHOT.equals(entity.getStatus())) {
            throw new IllegalArgumentException("该订单已完成拍摄");
        }

        BigDecimal finalAmount = entity.getFinalAmount() == null
            ? BigDecimal.ZERO
            : entity.getFinalAmount().setScale(2, RoundingMode.HALF_UP);

        if (finalAmount.compareTo(BigDecimal.ZERO) > 0) {
            AccountEntity finalAccount = requireCashAccount(request.getUserId(), request.getFinalAccountId());
            CategoryEntity incomeCategory = requirePhotographyIncomeCategory(request.getUserId());
            LocalDateTime occurredAt = request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt();
            TransactionEntity finalTransaction = createIncomeTransaction(
                request.getUserId(),
                finalAccount,
                incomeCategory,
                finalAmount,
                occurredAt,
                buildTransactionTitle(entity.getCustomerName(), "尾款"),
                buildTransactionRemark(entity.getOrderType(), entity.getAddress(), entity.getRemark())
            );
            entity.setFinalAccountId(finalAccount.getId());
            entity.setFinalTransactionId(finalTransaction.getId());
            entity.setFinalReceivedAt(finalTransaction.getOccurredAt());
        } else {
            entity.setFinalReceivedAt(request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt());
        }

        entity.setStatus(STATUS_SHOT);
        photographyOrderMapper.updateById(entity);
        return toResponses(List.of(photographyOrderMapper.selectById(entity.getId()))).get(0);
    }

    @Transactional
    public boolean delete(Long id, Long userId) {
        PhotographyOrderEntity entity = photographyOrderMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }

        rollbackLinkedTransaction(entity.getFinalTransactionId(), userId);
        rollbackLinkedTransaction(entity.getDepositTransactionId(), userId);
        photographyOrderMapper.deleteById(id);
        return true;
    }

    private boolean shouldFilterStatus(String status) {
        return StringUtils.hasText(status) && !STATUS_ALL.equalsIgnoreCase(status.trim());
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        if (!STATUS_ALL.equals(normalized) && !STATUS_PENDING.equals(normalized) && !STATUS_SHOT.equals(normalized)) {
            throw new IllegalArgumentException("订单状态不正确");
        }
        return normalized;
    }

    private String normalizeOrderType(String orderType) {
        if (!StringUtils.hasText(orderType)) {
            throw new IllegalArgumentException("订单类型不能为空");
        }
        String normalized = orderType.trim();
        if (!ORDER_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("订单类型不支持");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于0");
        }
        return normalized;
    }

    private void validateAmountRelation(BigDecimal totalAmount, BigDecimal depositAmount, BigDecimal finalAmount) {
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("总金额必须大于0");
        }
        if (depositAmount.add(finalAmount).compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException("订金与尾款之和必须等于总金额");
        }
    }

    private PhotographyOrderEntity requireOrder(Long id, Long userId) {
        PhotographyOrderEntity entity = photographyOrderMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new IllegalArgumentException("摄影订单不存在");
        }
        return entity;
    }

    private AccountEntity requireCashAccount(Long userId, Long accountId) {
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

    private CategoryEntity requirePhotographyIncomeCategory(Long userId) {
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
        Long userId,
        AccountEntity account,
        CategoryEntity category,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String title,
        String remark
    ) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setUserId(userId);
        transaction.setType(TRANSACTION_TYPE_INCOME);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        transaction.setAccountId(account.getId());
        transaction.setCategoryId(category.getId());
        transaction.setTitle(title);
        transaction.setRemark(trimNullable(remark));
        transaction.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);
        transaction.setStatus(TRANSACTION_STATUS_NORMAL);
        transactionMapper.insert(transaction);

        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        account.setCurrentBalance(currentBalance.add(amount));
        accountMapper.updateById(account);
        return transaction;
    }

    private void rollbackLinkedTransaction(Long transactionId, Long userId) {
        if (transactionId == null) {
            return;
        }

        TransactionEntity transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("关联流水不存在");
        }
        if (!userId.equals(transaction.getUserId())) {
            throw new IllegalArgumentException("无权处理该关联流水");
        }
        if (!TRANSACTION_STATUS_NORMAL.equals(transaction.getStatus())) {
            return;
        }

        AccountEntity account = accountMapper.selectById(transaction.getAccountId());
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("关联现金账户不存在");
        }

        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        BigDecimal nextBalance = currentBalance.subtract(transaction.getAmount());
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("删除后账户余额不足");
        }

        account.setCurrentBalance(nextBalance);
        accountMapper.updateById(account);
        transaction.setStatus(TRANSACTION_STATUS_VOIDED);
        transactionMapper.updateById(transaction);
    }

    private String buildTransactionTitle(String customerName, String suffix) {
        return "摄影" + suffix + " - " + customerName;
    }

    private String buildTransactionRemark(String orderType, String address, String remark) {
        StringBuilder builder = new StringBuilder();
        builder.append("订单类型：").append(toOrderTypeLabel(orderType));
        if (StringUtils.hasText(address)) {
            builder.append("；地址：").append(address.trim());
        }
        if (StringUtils.hasText(remark)) {
            builder.append("；备注：").append(remark.trim());
        }
        return builder.toString();
    }

    private String toOrderTypeLabel(String orderType) {
        return switch (orderType) {
            case "first_birthday" -> "周岁";
            case "hundred_days" -> "百天";
            case "engagement" -> "订婚";
            case "thanks_banquet" -> "答谢宴";
            case "wedding" -> "婚礼";
            case "graduation" -> "毕业照";
            default -> orderType;
        };
    }

    private String generateOrderNo() {
        return "PHOTO" + LocalDateTime.now().format(NO_TIME_FORMATTER)
            + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
    }

    private String generateTransactionNo() {
        return "IN" + LocalDateTime.now().format(NO_TIME_FORMATTER)
            + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String trimNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<PhotographyOrderResponse> toResponses(List<PhotographyOrderEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        Map<Long, AccountEntity> accountMap = loadAccountMap(entities);
        return entities.stream()
            .sorted(Comparator
                .comparing(PhotographyOrderEntity::getShootAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PhotographyOrderEntity::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PhotographyOrderEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(entity -> toResponse(entity, accountMap))
            .toList();
    }

    private Map<Long, AccountEntity> loadAccountMap(List<PhotographyOrderEntity> entities) {
        Set<Long> accountIds = entities.stream()
            .flatMap(entity -> Stream.of(entity.getDepositAccountId(), entity.getFinalAccountId()))
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return accountMapper.selectBatchIds(accountIds).stream()
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
    }

    private PhotographyOrderResponse toResponse(PhotographyOrderEntity entity, Map<Long, AccountEntity> accountMap) {
        PhotographyOrderResponse response = new PhotographyOrderResponse();
        response.setId(entity.getId());
        response.setOrderNo(entity.getOrderNo());
        response.setUserId(entity.getUserId());
        response.setCustomerName(entity.getCustomerName());
        response.setContactInfo(entity.getContactInfo());
        response.setOrderType(entity.getOrderType());
        response.setStatus(entity.getStatus());
        response.setShootAt(entity.getShootAt());
        response.setTotalAmount(entity.getTotalAmount());
        response.setDepositAmount(entity.getDepositAmount());
        response.setFinalAmount(entity.getFinalAmount());
        response.setDepositAccountId(entity.getDepositAccountId());
        response.setDepositAccountName(resolveAccountName(accountMap, entity.getDepositAccountId()));
        response.setDepositTransactionId(entity.getDepositTransactionId());
        response.setDepositReceivedAt(entity.getDepositReceivedAt());
        response.setFinalAccountId(entity.getFinalAccountId());
        response.setFinalAccountName(resolveAccountName(accountMap, entity.getFinalAccountId()));
        response.setFinalTransactionId(entity.getFinalTransactionId());
        response.setFinalReceivedAt(entity.getFinalReceivedAt());
        response.setAddress(entity.getAddress());
        response.setRemark(entity.getRemark());
        response.setSortOrder(entity.getSortOrder());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String resolveAccountName(Map<Long, AccountEntity> accountMap, Long accountId) {
        if (accountId == null) {
            return null;
        }
        AccountEntity account = accountMap.get(accountId);
        return account == null ? null : account.getName();
    }
}
