package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.RenewalSubscriptionRequest;
import com.example.finance.dto.RenewalSubscriptionResponse;
import com.example.finance.dto.RenewalSubscriptionSummaryResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.CategoryEntity;
import com.example.finance.entity.RenewalSubscriptionEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.CategoryMapper;
import com.example.finance.mapper.RenewalSubscriptionMapper;
import com.example.finance.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RenewalSubscriptionService {

    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PAUSED = "paused";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String BILLING_CYCLE_MONTHLY = "monthly";
    private static final String BILLING_CYCLE_QUARTERLY = "quarterly";
    private static final String BILLING_CYCLE_YEARLY = "yearly";
    private static final String CHARGE_STATUS_IDLE = "idle";
    private static final String CHARGE_STATUS_SUCCESS = "success";
    private static final String CHARGE_STATUS_FAILED = "failed";
    private static final String EXPENSE_TYPE = "expense";
    private static final String TRANSACTION_STATUS = "normal";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String ACTIVE_CATEGORY_STATUS = "active";
    private static final String FIXED_EXPENSE_CATEGORY_NAME = "固定支出";
    private static final String FIXED_EXPENSE_TRANSACTION_TITLE_SUFFIX = "支出";
    private static final int MAX_CHARGE_MESSAGE_LENGTH = 255;
    private static final ZoneId SHANGHAI_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TRANSACTION_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RenewalSubscriptionMapper renewalSubscriptionMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;

    public RenewalSubscriptionService(
        RenewalSubscriptionMapper renewalSubscriptionMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        CategoryMapper categoryMapper,
        TransactionMapper transactionMapper
    ) {
        this.renewalSubscriptionMapper = renewalSubscriptionMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
    }

    public List<RenewalSubscriptionResponse> list(Long userId, String status) {
        LambdaQueryWrapper<RenewalSubscriptionEntity> wrapper = new LambdaQueryWrapper<RenewalSubscriptionEntity>()
            .eq(RenewalSubscriptionEntity::getUserId, userId)
            .orderByAsc(RenewalSubscriptionEntity::getStatus)
            .orderByAsc(RenewalSubscriptionEntity::getNextBillingDate)
            .orderByDesc(RenewalSubscriptionEntity::getUpdatedAt)
            .orderByDesc(RenewalSubscriptionEntity::getId);

        if (StringUtils.hasText(status)) {
            wrapper.eq(RenewalSubscriptionEntity::getStatus, normalizeListStatus(status));
        } else {
            wrapper.in(RenewalSubscriptionEntity::getStatus, List.of(STATUS_ACTIVE, STATUS_PAUSED));
        }

        List<RenewalSubscriptionEntity> entities = renewalSubscriptionMapper.selectList(wrapper);
        return toResponses(entities);
    }

    public RenewalSubscriptionSummaryResponse summary(Long userId) {
        List<RenewalSubscriptionEntity> entities = renewalSubscriptionMapper.selectList(new LambdaQueryWrapper<RenewalSubscriptionEntity>()
            .eq(RenewalSubscriptionEntity::getUserId, userId)
            .in(RenewalSubscriptionEntity::getStatus, List.of(STATUS_ACTIVE, STATUS_PAUSED)));
        if (entities.isEmpty()) {
            RenewalSubscriptionSummaryResponse response = new RenewalSubscriptionSummaryResponse();
            response.setActiveCount(0);
            response.setPausedCount(0);
            response.setDueThisMonthCount(0);
            response.setMonthlyAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            response.setDueThisMonthAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return response;
        }

        YearMonth currentMonth = YearMonth.now(SHANGHAI_ZONE_ID);
        BigDecimal monthlyAmount = BigDecimal.ZERO;
        BigDecimal dueThisMonthAmount = BigDecimal.ZERO;
        int activeCount = 0;
        int pausedCount = 0;
        int dueThisMonthCount = 0;

        for (RenewalSubscriptionEntity entity : entities) {
            BigDecimal amount = normalizeAmount(entity.getAmount());
            if (STATUS_ACTIVE.equals(entity.getStatus())) {
                activeCount++;
                monthlyAmount = monthlyAmount.add(amount);
                if (entity.getNextBillingDate() != null && YearMonth.from(entity.getNextBillingDate()).equals(currentMonth)) {
                    dueThisMonthCount++;
                    dueThisMonthAmount = dueThisMonthAmount.add(amount);
                }
            } else if (STATUS_PAUSED.equals(entity.getStatus())) {
                pausedCount++;
            }
        }

        RenewalSubscriptionSummaryResponse response = new RenewalSubscriptionSummaryResponse();
        response.setActiveCount(activeCount);
        response.setPausedCount(pausedCount);
        response.setDueThisMonthCount(dueThisMonthCount);
        response.setMonthlyAmount(monthlyAmount.setScale(2, RoundingMode.HALF_UP));
        response.setDueThisMonthAmount(dueThisMonthAmount.setScale(2, RoundingMode.HALF_UP));
        return response;
    }

    @Transactional
    public RenewalSubscriptionResponse create(RenewalSubscriptionRequest request) {
        AccountEntity fundingAccount = requireCashAccount(request.getUserId(), request.getFundingAccountId());
        CategoryEntity category = requireExpenseLeafCategory(request.getUserId(), request.getCategoryId());

        RenewalSubscriptionEntity entity = new RenewalSubscriptionEntity();
        fillEntity(entity, request, fundingAccount, category, null);
        renewalSubscriptionMapper.insert(entity);
        return toResponse(renewalSubscriptionMapper.selectById(entity.getId()), fundingAccount, category);
    }

    @Transactional
    public Optional<RenewalSubscriptionResponse> update(Long id, RenewalSubscriptionRequest request) {
        RenewalSubscriptionEntity entity = renewalSubscriptionMapper.selectById(id);
        if (entity == null || STATUS_CANCELLED.equals(entity.getStatus()) || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }

        AccountEntity fundingAccount = requireCashAccount(request.getUserId(), request.getFundingAccountId());
        CategoryEntity category = requireExpenseLeafCategory(request.getUserId(), request.getCategoryId());
        fillEntity(entity, request, fundingAccount, category, entity);
        renewalSubscriptionMapper.updateById(entity);
        return Optional.of(toResponse(renewalSubscriptionMapper.selectById(id), fundingAccount, category));
    }

    @Transactional
    public Optional<RenewalSubscriptionResponse> pause(Long id, Long userId) {
        RenewalSubscriptionEntity entity = renewalSubscriptionMapper.selectById(id);
        if (entity == null || STATUS_CANCELLED.equals(entity.getStatus()) || !userId.equals(entity.getUserId())) {
            return Optional.empty();
        }
        entity.setStatus(STATUS_PAUSED);
        renewalSubscriptionMapper.updateById(entity);
        return Optional.of(toResponse(entity, loadFundingAccount(entity.getFundingAccountId()), loadCategory(entity.getCategoryId())));
    }

    @Transactional
    public Optional<RenewalSubscriptionResponse> resume(Long id, Long userId) {
        RenewalSubscriptionEntity entity = renewalSubscriptionMapper.selectById(id);
        if (entity == null || STATUS_CANCELLED.equals(entity.getStatus()) || !userId.equals(entity.getUserId())) {
            return Optional.empty();
        }
        entity.setStatus(STATUS_ACTIVE);
        if (entity.getNextBillingDate() == null) {
            entity.setNextBillingDate(resolveNextBillingDate(null, entity.getBillingDay(), entity.getBillingCycle(), LocalDate.now(SHANGHAI_ZONE_ID)));
        }
        renewalSubscriptionMapper.updateById(entity);
        return Optional.of(toResponse(entity, loadFundingAccount(entity.getFundingAccountId()), loadCategory(entity.getCategoryId())));
    }

    @Transactional
    public boolean delete(Long id, Long userId) {
        RenewalSubscriptionEntity entity = renewalSubscriptionMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        return renewalSubscriptionMapper.deleteById(id) > 0;
    }

    public List<Long> listDueSubscriptionIds(LocalDate today) {
        return renewalSubscriptionMapper.selectList(new LambdaQueryWrapper<RenewalSubscriptionEntity>()
                .eq(RenewalSubscriptionEntity::getStatus, STATUS_ACTIVE)
                .le(RenewalSubscriptionEntity::getNextBillingDate, today)
                .orderByAsc(RenewalSubscriptionEntity::getNextBillingDate)
                .orderByAsc(RenewalSubscriptionEntity::getId))
            .stream()
            .map(RenewalSubscriptionEntity::getId)
            .toList();
    }

    @Transactional
    public void processDueSubscription(Long subscriptionId) {
        RenewalSubscriptionEntity entity = renewalSubscriptionMapper.selectById(subscriptionId);
        if (entity == null || !STATUS_ACTIVE.equals(entity.getStatus()) || entity.getNextBillingDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now(SHANGHAI_ZONE_ID);
        if (entity.getNextBillingDate().isAfter(today)) {
            return;
        }

        boolean hasWritten = false;
        try {
            // 校验阶段：只做读取与校验，不产生任何写入，保证失败时无需回滚
            AccountEntity fundingAccount = requireCashAccount(entity.getUserId(), entity.getFundingAccountId());
            CategoryEntity category = requireChargeCategory(entity);
            BigDecimal amount = normalizeAmount(entity.getAmount());
            BigDecimal nextBalance = resolveNextFundingAccountBalance(fundingAccount, amount);

            // 写入阶段：此后不再抛出 IllegalArgumentException，避免流水与余额不一致
            TransactionEntity transaction = new TransactionEntity();
            transaction.setTransactionNo(generateTransactionNo());
            transaction.setUserId(entity.getUserId());
            transaction.setType(EXPENSE_TYPE);
            transaction.setAmount(amount);
            transaction.setCurrencyCode(StringUtils.hasText(entity.getCurrencyCode()) ? entity.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
            transaction.setAccountId(fundingAccount.getId());
            transaction.setCategoryId(category.getId());
            transaction.setTitle(buildTransactionTitle(entity));
            transaction.setRemark(entity.getRemark());
            transaction.setOccurredAt(entity.getNextBillingDate().atTime(8, 0));
            transaction.setStatus(TRANSACTION_STATUS);
            hasWritten = true;
            transactionMapper.insert(transaction);

            applyFundingAccountBalance(fundingAccount, nextBalance);

            entity.setLastChargedAt(LocalDateTime.now(SHANGHAI_ZONE_ID));
            entity.setLastTransactionId(transaction.getId());
            entity.setLastChargeStatus(CHARGE_STATUS_SUCCESS);
            entity.setLastChargeMessage("自动扣款成功");
            entity.setNextBillingDate(resolveNextCycleBillingDate(entity.getNextBillingDate(), entity.getBillingDay(), entity.getBillingCycle()));
        } catch (IllegalArgumentException exception) {
            if (hasWritten) {
                // 已经写入流水后才失败，只能整体回滚，否则会留下未真正扣款的流水
                throw exception;
            }
            entity.setLastChargeStatus(CHARGE_STATUS_FAILED);
            entity.setLastChargeMessage(trimMessage(exception.getMessage()));
        }

        renewalSubscriptionMapper.updateById(entity);
    }

    private void fillEntity(
        RenewalSubscriptionEntity entity,
        RenewalSubscriptionRequest request,
        AccountEntity fundingAccount,
        CategoryEntity category,
        RenewalSubscriptionEntity existing
    ) {
        entity.setUserId(request.getUserId());
        entity.setName(request.getName().trim());
        entity.setProviderName(StringUtils.hasText(request.getProviderName()) ? request.getProviderName().trim() : null);
        entity.setAmount(normalizeAmount(request.getAmount()));
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode().trim() : DEFAULT_CURRENCY_CODE);
        entity.setFundingAccountId(fundingAccount.getId());
        entity.setCategoryId(category.getId());
        entity.setBillingDay(request.getBillingDay());
        entity.setBillingCycle(normalizeBillingCycle(request.getBillingCycle(), existing == null ? null : existing.getBillingCycle()));
        entity.setNextBillingDate(resolveRequestedNextBillingDate(request, existing));
        entity.setStatus(normalizeSaveStatus(request.getStatus(), existing == null ? null : existing.getStatus()));
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        if (existing == null) {
            entity.setLastChargeStatus(CHARGE_STATUS_IDLE);
            entity.setLastChargeMessage(null);
            entity.setLastChargedAt(null);
            entity.setLastTransactionId(null);
        }
    }

    private List<RenewalSubscriptionResponse> toResponses(List<RenewalSubscriptionEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        Set<Long> fundingAccountIds = entities.stream()
            .map(RenewalSubscriptionEntity::getFundingAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Map<Long, AccountEntity> fundingAccounts = fundingAccountIds.isEmpty()
            ? Collections.emptyMap()
            : accountMapper.selectByIds(fundingAccountIds).stream()
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));

        Set<Long> categoryIds = entities.stream()
            .map(RenewalSubscriptionEntity::getCategoryId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Map<Long, CategoryEntity> categories = categoryIds.isEmpty()
            ? Collections.emptyMap()
            : categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));

        return entities.stream()
            .map(entity -> toResponse(
                entity,
                entity.getFundingAccountId() == null ? null : fundingAccounts.get(entity.getFundingAccountId()),
                entity.getCategoryId() == null ? null : categories.get(entity.getCategoryId())
            ))
            .sorted(Comparator
                .comparing(RenewalSubscriptionResponse::getStatus, Comparator.nullsLast(String::compareTo))
                .thenComparing(RenewalSubscriptionResponse::getNextBillingDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(RenewalSubscriptionResponse::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    private RenewalSubscriptionResponse toResponse(
        RenewalSubscriptionEntity entity,
        AccountEntity fundingAccount,
        CategoryEntity category
    ) {
        RenewalSubscriptionResponse response = new RenewalSubscriptionResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setName(entity.getName());
        response.setProviderName(entity.getProviderName());
        response.setAmount(entity.getAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setFundingAccountId(entity.getFundingAccountId());
        response.setFundingAccountName(fundingAccount == null ? null : fundingAccount.getName());
        response.setCategoryId(entity.getCategoryId());
        response.setCategoryName(category == null ? null : category.getName());
        response.setCategoryIcon(category == null ? null : category.getIcon());
        response.setCategoryColor(category == null ? null : category.getColor());
        response.setBillingDay(entity.getBillingDay());
        response.setBillingCycle(entity.getBillingCycle());
        response.setNextBillingDate(entity.getNextBillingDate());
        response.setLastChargedAt(entity.getLastChargedAt());
        response.setLastTransactionId(entity.getLastTransactionId());
        response.setLastChargeStatus(entity.getLastChargeStatus());
        response.setLastChargeMessage(entity.getLastChargeMessage());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private AccountEntity requireCashAccount(Long userId, Long fundingAccountId) {
        if (fundingAccountId == null) {
            throw new IllegalArgumentException("请选择扣款账户");
        }
        AccountEntity account = accountMapper.selectById(fundingAccountId);
        if (account == null || !userId.equals(account.getUserId()) || !STATUS_ACTIVE.equals(account.getStatus())) {
            throw new IllegalArgumentException("扣款账户不存在");
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("扣款账户必须为现金账户");
        }
        return account;
    }

    private AccountEntity loadFundingAccount(Long fundingAccountId) {
        return fundingAccountId == null ? null : accountMapper.selectById(fundingAccountId);
    }

    private CategoryEntity loadCategory(Long categoryId) {
        return categoryId == null ? null : categoryMapper.selectById(categoryId);
    }

    private CategoryEntity requireChargeCategory(RenewalSubscriptionEntity entity) {
        if (entity.getCategoryId() == null) {
            return requireFixedExpenseCategory();
        }
        return requireExpenseLeafCategory(entity.getUserId(), entity.getCategoryId());
    }

    private CategoryEntity requireExpenseLeafCategory(Long userId, Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("请选择扣款分类");
        }
        CategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("扣款分类不存在");
        }
        if (category.getUserId() != null && !userId.equals(category.getUserId())) {
            throw new IllegalArgumentException("扣款分类不存在");
        }
        if (!EXPENSE_TYPE.equals(category.getType())) {
            throw new IllegalArgumentException("扣款分类必须为支出分类");
        }
        if (!ACTIVE_CATEGORY_STATUS.equals(category.getStatus())) {
            throw new IllegalArgumentException("扣款分类不可用");
        }
        if (categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getParentId, category.getId())
            .eq(CategoryEntity::getStatus, ACTIVE_CATEGORY_STATUS)) > 0) {
            throw new IllegalArgumentException("请选择可直接记账的扣款分类");
        }
        return category;
    }

    private CategoryEntity requireFixedExpenseCategory() {
        CategoryEntity category = categoryMapper.selectOne(new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getName, FIXED_EXPENSE_CATEGORY_NAME)
            .eq(CategoryEntity::getType, EXPENSE_TYPE)
            .eq(CategoryEntity::getStatus, ACTIVE_CATEGORY_STATUS)
            .isNull(CategoryEntity::getUserId)
            .last("LIMIT 1"));
        if (category == null) {
            throw new IllegalArgumentException("未找到“固定支出”支出分类，请先执行最新数据库脚本");
        }
        return category;
    }

    private BigDecimal resolveNextFundingAccountBalance(AccountEntity fundingAccount, BigDecimal amount) {
        BigDecimal currentBalance = fundingAccount.getCurrentBalance() == null ? BigDecimal.ZERO : fundingAccount.getCurrentBalance();
        BigDecimal nextBalance = currentBalance.subtract(amount);
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("扣款账户余额不足");
        }
        return nextBalance.setScale(2, RoundingMode.HALF_UP);
    }

    private void applyFundingAccountBalance(AccountEntity fundingAccount, BigDecimal nextBalance) {
        fundingAccount.setCurrentBalance(nextBalance);
        accountMapper.updateById(fundingAccount);
    }

    private String normalizeListStatus(String status) {
        if (STATUS_ACTIVE.equals(status) || STATUS_PAUSED.equals(status) || STATUS_CANCELLED.equals(status)) {
            return status;
        }
        throw new IllegalArgumentException("固定支出状态仅支持 active、paused 或 cancelled");
    }

    private String normalizeSaveStatus(String status, String currentStatus) {
        if (!StringUtils.hasText(status)) {
            return StringUtils.hasText(currentStatus) ? currentStatus : STATUS_ACTIVE;
        }
        if (!STATUS_ACTIVE.equals(status) && !STATUS_PAUSED.equals(status)) {
            throw new IllegalArgumentException("固定支出状态仅支持 active 或 paused");
        }
        return status;
    }

    private LocalDate resolveRequestedNextBillingDate(RenewalSubscriptionRequest request, RenewalSubscriptionEntity existing) {
        String billingCycle = normalizeBillingCycle(request.getBillingCycle(), existing == null ? null : existing.getBillingCycle());
        if (request.getNextBillingDate() != null) {
            return alignBillingDay(request.getNextBillingDate(), request.getBillingDay());
        }
        if (existing != null && existing.getNextBillingDate() != null) {
            return alignBillingDay(existing.getNextBillingDate(), request.getBillingDay());
        }
        return resolveNextBillingDate(null, request.getBillingDay(), billingCycle, LocalDate.now(SHANGHAI_ZONE_ID));
    }

    private LocalDate resolveNextBillingDate(LocalDate seedDate, Integer billingDay, String billingCycle, LocalDate today) {
        LocalDate baseDate = seedDate == null ? today : seedDate;
        LocalDate candidate = alignBillingDay(baseDate, billingDay);
        if (seedDate == null && candidate.isBefore(today)) {
            return alignBillingDay(shiftDateByCycle(today, billingCycle), billingDay);
        }
        return candidate;
    }

    private LocalDate resolveNextCycleBillingDate(LocalDate currentDueDate, Integer billingDay, String billingCycle) {
        LocalDate baseDate = currentDueDate == null ? LocalDate.now(SHANGHAI_ZONE_ID) : currentDueDate;
        return alignBillingDay(shiftDateByCycle(baseDate, billingCycle), billingDay);
    }

    private LocalDate alignBillingDay(LocalDate date, Integer billingDay) {
        int targetDay = Math.min(Math.max(billingDay == null ? 1 : billingDay, 1), date.lengthOfMonth());
        return date.withDayOfMonth(targetDay);
    }

    private LocalDate shiftDateByCycle(LocalDate baseDate, String billingCycle) {
        String cycle = normalizeBillingCycle(billingCycle, null);
        if (BILLING_CYCLE_QUARTERLY.equals(cycle)) {
            return baseDate.plusMonths(3);
        }
        if (BILLING_CYCLE_YEARLY.equals(cycle)) {
            return baseDate.plusYears(1);
        }
        return baseDate.plusMonths(1);
    }

    private String normalizeBillingCycle(String billingCycle, String currentBillingCycle) {
        if (!StringUtils.hasText(billingCycle)) {
            return StringUtils.hasText(currentBillingCycle) ? currentBillingCycle : BILLING_CYCLE_MONTHLY;
        }
        if (!BILLING_CYCLE_MONTHLY.equals(billingCycle)
            && !BILLING_CYCLE_QUARTERLY.equals(billingCycle)
            && !BILLING_CYCLE_YEARLY.equals(billingCycle)) {
            throw new IllegalArgumentException("支出周期仅支持 monthly、quarterly 或 yearly");
        }
        return billingCycle;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("固定支出金额必须大于0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildTransactionTitle(RenewalSubscriptionEntity entity) {
        String name = StringUtils.hasText(entity.getName()) ? entity.getName().trim() : "固定支出";
        return name.length() > 100 ? name.substring(0, 100) : name + FIXED_EXPENSE_TRANSACTION_TITLE_SUFFIX;
    }

    private String generateTransactionNo() {
        String timePart = LocalDateTime.now(SHANGHAI_ZONE_ID).format(TRANSACTION_NO_TIME_FORMAT);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "EX" + timePart + randomPart;
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "自动扣款失败";
        }
        return message.length() > MAX_CHARGE_MESSAGE_LENGTH
            ? message.substring(0, MAX_CHARGE_MESSAGE_LENGTH)
            : message;
    }
}
