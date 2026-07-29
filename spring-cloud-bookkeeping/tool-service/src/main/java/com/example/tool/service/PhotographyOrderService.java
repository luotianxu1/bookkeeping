package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.tool.dto.PhotographyOrderCollectFinalRequest;
import com.example.tool.dto.PhotographyOrderOverviewBucketResponse;
import com.example.tool.dto.PhotographyOrderOverviewResponse;
import com.example.tool.dto.PhotographyOrderOverviewSummaryResponse;
import com.example.tool.dto.PhotographyOrderOverviewTrendPointResponse;
import com.example.tool.dto.PhotographyOrderOverviewTypeStatResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String STATUS_ALL = "all";
    private static final String OVERVIEW_VIEW_CALENDAR = "calendar";
    private static final String OVERVIEW_VIEW_MONTH = "month";
    private static final String OVERVIEW_VIEW_YEAR = "year";
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

    public PhotographyOrderOverviewResponse overview(Long userId, String view, String anchor, String selectedDate) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }

        List<PhotographyOrderEntity> entities = photographyOrderMapper.selectList(new LambdaQueryWrapper<PhotographyOrderEntity>()
            .eq(PhotographyOrderEntity::getUserId, userId)
            .orderByAsc(PhotographyOrderEntity::getShootAt)
            .orderByAsc(PhotographyOrderEntity::getSortOrder)
            .orderByDesc(PhotographyOrderEntity::getId));

        String normalizedView = normalizeOverviewView(view);
        return switch (normalizedView) {
            case OVERVIEW_VIEW_YEAR -> buildYearOverview(entities, anchor);
            case OVERVIEW_VIEW_MONTH -> buildMonthOverview(entities, anchor);
            default -> buildCalendarOverview(entities, anchor, selectedDate);
        };
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
        entity.setCustomerName(buildOrderDisplayName(orderType));
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
            LocalDateTime depositReceivedAt = LocalDateTime.now();
            TransactionEntity depositTransaction = createIncomeTransaction(
                request.getUserId(),
                depositAccount,
                incomeCategory,
                depositAmount,
                depositReceivedAt,
                buildTransactionTitle(orderType, "订金"),
                buildTransactionRemark(orderType, entity.getAddress(), entity.getRemark())
            );
            entity.setDepositAccountId(depositAccount.getId());
            entity.setDepositTransactionId(depositTransaction.getId());
            entity.setDepositReceivedAt(depositReceivedAt);
        }

        photographyOrderMapper.insert(entity);
        return toResponses(List.of(photographyOrderMapper.selectById(entity.getId()))).get(0);
    }

    @Transactional
    public PhotographyOrderResponse collectFinal(Long id, PhotographyOrderCollectFinalRequest request) {
        PhotographyOrderEntity entity = requireOrder(id, request.getUserId());
        if (STATUS_CANCELLED.equals(entity.getStatus())) {
            throw new IllegalArgumentException("该订单已取消拍摄");
        }
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
                buildTransactionTitle(entity.getOrderType(), "尾款"),
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
    public PhotographyOrderResponse cancel(Long id, Long userId) {
        PhotographyOrderEntity entity = requireOrder(id, userId);
        if (STATUS_CANCELLED.equals(entity.getStatus())) {
            throw new IllegalArgumentException("该订单已取消拍摄");
        }

        rollbackLinkedTransaction(entity.getFinalTransactionId(), userId);
        photographyOrderMapper.update(null, new UpdateWrapper<PhotographyOrderEntity>()
            .eq("id", entity.getId())
            .set("status", STATUS_CANCELLED)
            .set("final_account_id", null)
            .set("final_transaction_id", null)
            .set("final_received_at", null));
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

    private String normalizeOverviewView(String view) {
        if (!StringUtils.hasText(view)) {
            return OVERVIEW_VIEW_CALENDAR;
        }
        String normalized = view.trim().toLowerCase(Locale.ROOT);
        if (!OVERVIEW_VIEW_CALENDAR.equals(normalized)
            && !OVERVIEW_VIEW_MONTH.equals(normalized)
            && !OVERVIEW_VIEW_YEAR.equals(normalized)) {
            throw new IllegalArgumentException("总览视图不支持");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        if (!STATUS_ALL.equals(normalized)
            && !STATUS_PENDING.equals(normalized)
            && !STATUS_SHOT.equals(normalized)
            && !STATUS_CANCELLED.equals(normalized)) {
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

    private String buildTransactionTitle(String orderType, String suffix) {
        return "摄影" + suffix + " - " + buildOrderDisplayName(orderType);
    }

    private String buildOrderDisplayName(String orderType) {
        return toOrderTypeLabel(orderType) + "订单";
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

    private PhotographyOrderOverviewResponse buildCalendarOverview(
        List<PhotographyOrderEntity> entities,
        String anchor,
        String selectedDate
    ) {
        YearMonth month = parseYearMonth(anchor, YearMonth.now());
        LocalDate targetDate = parseLocalDate(selectedDate, null);
        if (targetDate != null && !YearMonth.from(targetDate).equals(month)) {
            targetDate = null;
        }
        final LocalDate selectedTargetDate = targetDate;

        List<PhotographyOrderEntity> monthOrders = entities.stream()
            .filter(entity -> isInMonth(entity, month))
            .toList();
        List<PhotographyOrderEntity> periodOrders = selectedTargetDate == null
            ? List.of()
            : monthOrders.stream()
                .filter(entity -> isInDate(entity, selectedTargetDate))
                .toList();

        PhotographyOrderOverviewResponse response = new PhotographyOrderOverviewResponse();
        response.setView(OVERVIEW_VIEW_CALENDAR);
        response.setAnchor(month.toString());
        response.setSelectedValue(selectedTargetDate == null ? null : selectedTargetDate.toString());
        response.setTitle(month.getMonthValue() + "月订单总览");
        response.setSubtitle("点击有订单的日期查看当天订单");
        response.setSummary(buildSummary(monthOrders));
        response.setTrendPoints(buildDailyTrendPoints(monthOrders, month));
        response.setTypeStats(buildTypeStats(monthOrders));
        response.setBuckets(buildCalendarBuckets(monthOrders, month, selectedTargetDate));
        response.setOrders(toResponses(periodOrders));
        return response;
    }

    private PhotographyOrderOverviewResponse buildMonthOverview(List<PhotographyOrderEntity> entities, String anchor) {
        int year = parseYear(anchor, LocalDate.now().getYear());
        List<PhotographyOrderEntity> periodOrders = entities.stream()
            .filter(entity -> entity.getShootAt() != null && entity.getShootAt().getYear() == year)
            .toList();

        PhotographyOrderOverviewResponse response = new PhotographyOrderOverviewResponse();
        response.setView(OVERVIEW_VIEW_MONTH);
        response.setAnchor(String.valueOf(year));
        response.setSelectedValue(String.valueOf(year));
        response.setTitle(year + "年月度总览");
        response.setSubtitle("查看全年 12 个月的档期分布与收入变化");
        response.setSummary(buildSummary(periodOrders));
        response.setTrendPoints(buildYearTrendPoints(periodOrders, year));
        response.setTypeStats(buildTypeStats(periodOrders));
        response.setBuckets(buildYearBuckets(periodOrders, year));
        response.setOrders(toResponses(periodOrders));
        return response;
    }

    private PhotographyOrderOverviewResponse buildYearOverview(List<PhotographyOrderEntity> entities, String anchor) {
        int endYear = parseYear(anchor, LocalDate.now().getYear());
        int startYear = endYear - 4;
        List<PhotographyOrderEntity> periodOrders = entities.stream()
            .filter(entity -> entity.getShootAt() != null
                && entity.getShootAt().getYear() >= startYear
                && entity.getShootAt().getYear() <= endYear)
            .toList();

        PhotographyOrderOverviewResponse response = new PhotographyOrderOverviewResponse();
        response.setView(OVERVIEW_VIEW_YEAR);
        response.setAnchor(String.valueOf(endYear));
        response.setSelectedValue(startYear + "-" + endYear);
        response.setTitle("近5年订单总览");
        response.setSubtitle(startYear + " - " + endYear + " 的收入趋势与类型分布");
        response.setSummary(buildSummary(periodOrders));
        response.setTrendPoints(buildFiveYearTrendPoints(periodOrders, startYear, endYear));
        response.setTypeStats(buildTypeStats(periodOrders));
        response.setBuckets(buildFiveYearBuckets(periodOrders, startYear, endYear));
        response.setOrders(toResponses(periodOrders));
        return response;
    }

    private PhotographyOrderOverviewSummaryResponse buildSummary(List<PhotographyOrderEntity> entities) {
        PhotographyOrderOverviewSummaryResponse response = new PhotographyOrderOverviewSummaryResponse();
        List<PhotographyOrderEntity> activeEntities = activeOrders(entities);
        BigDecimal totalContractAmount = sumAmount(activeEntities, PhotographyOrderEntity::getTotalAmount);
        BigDecimal totalDepositAmount = sumAmount(entities, PhotographyOrderEntity::getDepositAmount);
        BigDecimal totalFinalAmount = sumAmount(activeEntities, PhotographyOrderEntity::getFinalAmount);
        BigDecimal depositIncome = sumReceivedDeposit(entities);
        BigDecimal finalIncome = sumReceivedFinal(activeEntities);
        BigDecimal totalReceivedAmount = depositIncome.add(finalIncome).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pendingFinalAmount = sumPendingFinal(activeEntities);

        response.setTotalOrders(entities.size());
        response.setShotOrders((int) entities.stream().filter(entity -> STATUS_SHOT.equals(entity.getStatus())).count());
        response.setPendingOrders((int) entities.stream().filter(entity -> STATUS_PENDING.equals(entity.getStatus())).count());
        response.setTotalContractAmount(totalContractAmount);
        response.setTotalReceivedAmount(totalReceivedAmount);
        response.setTotalDepositAmount(totalDepositAmount);
        response.setTotalFinalAmount(totalFinalAmount);
        response.setDepositIncome(depositIncome);
        response.setFinalIncome(finalIncome);
        response.setPendingFinalAmount(pendingFinalAmount);
        response.setAverageContractAmount(activeEntities.isEmpty()
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : totalContractAmount.divide(BigDecimal.valueOf(activeEntities.size()), 2, RoundingMode.HALF_UP));
        return response;
    }

    private List<PhotographyOrderOverviewTrendPointResponse> buildDailyTrendPoints(
        List<PhotographyOrderEntity> entities,
        YearMonth month
    ) {
        return month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
            .map(date -> buildTrendPoint(
                date.toString(),
                String.valueOf(date.getDayOfMonth()),
                entities.stream().filter(entity -> isInDate(entity, date)).toList()
            ))
            .toList();
    }

    private List<PhotographyOrderOverviewTrendPointResponse> buildYearTrendPoints(List<PhotographyOrderEntity> entities, int year) {
        return Stream.iterate(YearMonth.of(year, 1), current -> current.plusMonths(1))
            .limit(12)
            .map(month -> buildTrendPoint(
                month.toString(),
                month.getMonthValue() + "月",
                entities.stream().filter(entity -> isInMonth(entity, month)).toList()
            ))
            .toList();
    }

    private List<PhotographyOrderOverviewTrendPointResponse> buildFiveYearTrendPoints(
        List<PhotographyOrderEntity> entities,
        int startYear,
        int endYear
    ) {
        return Stream.iterate(startYear, year -> year + 1)
            .limit(endYear - startYear + 1L)
            .map(year -> buildTrendPoint(
                String.valueOf(year),
                year + "年",
                entities.stream()
                    .filter(entity -> entity.getShootAt() != null && entity.getShootAt().getYear() == year)
                    .toList()
            ))
            .toList();
    }

    private PhotographyOrderOverviewTrendPointResponse buildTrendPoint(
        String key,
        String label,
        List<PhotographyOrderEntity> entities
    ) {
        PhotographyOrderOverviewTrendPointResponse response = new PhotographyOrderOverviewTrendPointResponse();
        List<PhotographyOrderEntity> activeEntities = activeOrders(entities);
        response.setKey(key);
        response.setLabel(label);
        response.setOrderCount(entities.size());
        response.setShotCount((int) entities.stream().filter(entity -> STATUS_SHOT.equals(entity.getStatus())).count());
        response.setPendingCount((int) entities.stream().filter(entity -> STATUS_PENDING.equals(entity.getStatus())).count());
        response.setTotalIncome(sumReceivedAmount(entities));
        response.setContractAmount(sumAmount(activeEntities, PhotographyOrderEntity::getTotalAmount));
        return response;
    }

    private List<PhotographyOrderOverviewTypeStatResponse> buildTypeStats(List<PhotographyOrderEntity> entities) {
        return ORDER_TYPES.stream()
            .map(orderType -> {
                List<PhotographyOrderEntity> typeOrders = entities.stream()
                    .filter(entity -> orderType.equals(entity.getOrderType()))
                    .toList();
                if (typeOrders.isEmpty()) {
                    return null;
                }
                PhotographyOrderOverviewTypeStatResponse response = new PhotographyOrderOverviewTypeStatResponse();
                response.setType(orderType);
                response.setLabel(toOrderTypeLabel(orderType));
                response.setOrderCount(typeOrders.size());
                response.setTotalIncome(sumReceivedAmount(typeOrders));
                response.setContractAmount(sumAmount(activeOrders(typeOrders), PhotographyOrderEntity::getTotalAmount));
                return response;
            })
            .filter(item -> item != null)
            .sorted(Comparator.comparing(PhotographyOrderOverviewTypeStatResponse::getOrderCount).reversed())
            .toList();
    }

    private List<PhotographyOrderOverviewBucketResponse> buildCalendarBuckets(
        List<PhotographyOrderEntity> entities,
        YearMonth month,
        LocalDate selectedDate
    ) {
        LocalDate start = month.atDay(1).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
        return start.datesUntil(end.plusDays(1))
            .map(date -> buildBucket(
                date.toString(),
                String.valueOf(date.getDayOfMonth()),
                toWeekdayShortLabel(date),
                entities.stream().filter(entity -> isInDate(entity, date)).toList(),
                date.equals(selectedDate),
                YearMonth.from(date).equals(month)
            ))
            .toList();
    }

    private List<PhotographyOrderOverviewBucketResponse> buildMonthBuckets(
        List<PhotographyOrderEntity> entities,
        YearMonth month
    ) {
        return month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
            .map(date -> buildBucket(
                date.toString(),
                String.valueOf(date.getDayOfMonth()),
                toWeekdayShortLabel(date),
                entities.stream().filter(entity -> isInDate(entity, date)).toList(),
                false,
                true
            ))
            .toList();
    }

    private List<PhotographyOrderOverviewBucketResponse> buildYearBuckets(List<PhotographyOrderEntity> entities, int year) {
        return Stream.iterate(YearMonth.of(year, 1), current -> current.plusMonths(1))
            .limit(12)
            .map(month -> buildBucket(
                month.toString(),
                month.getMonthValue() + "月",
                month.getMonth().getDisplayName(TextStyle.SHORT, Locale.CHINA),
                entities.stream().filter(entity -> isInMonth(entity, month)).toList(),
                false,
                true
            ))
            .toList();
    }

    private List<PhotographyOrderOverviewBucketResponse> buildFiveYearBuckets(
        List<PhotographyOrderEntity> entities,
        int startYear,
        int endYear
    ) {
        return Stream.iterate(startYear, year -> year + 1)
            .limit(endYear - startYear + 1L)
            .map(year -> buildBucket(
                String.valueOf(year),
                year + "年",
                null,
                entities.stream()
                    .filter(entity -> entity.getShootAt() != null && entity.getShootAt().getYear() == year)
                    .toList(),
                false,
                true
            ))
            .toList();
    }

    private PhotographyOrderOverviewBucketResponse buildBucket(
        String key,
        String label,
        String subLabel,
        List<PhotographyOrderEntity> entities,
        boolean selected,
        boolean currentScope
    ) {
        PhotographyOrderOverviewBucketResponse response = new PhotographyOrderOverviewBucketResponse();
        List<PhotographyOrderEntity> activeEntities = activeOrders(entities);
        response.setKey(key);
        response.setLabel(label);
        response.setSubLabel(subLabel);
        response.setOrderCount(entities.size());
        response.setShotCount((int) entities.stream().filter(entity -> STATUS_SHOT.equals(entity.getStatus())).count());
        response.setPendingCount((int) entities.stream().filter(entity -> STATUS_PENDING.equals(entity.getStatus())).count());
        response.setTotalIncome(sumReceivedAmount(entities));
        response.setContractAmount(sumAmount(activeEntities, PhotographyOrderEntity::getTotalAmount));
        response.setSelected(selected);
        response.setCurrentScope(currentScope);
        return response;
    }

    private List<PhotographyOrderEntity> activeOrders(List<PhotographyOrderEntity> entities) {
        return entities.stream()
            .filter(entity -> !STATUS_CANCELLED.equals(entity.getStatus()))
            .toList();
    }

    private BigDecimal sumAmount(List<PhotographyOrderEntity> entities, Function<PhotographyOrderEntity, BigDecimal> getter) {
        return entities.stream()
            .map(getter)
            .filter(item -> item != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumReceivedDeposit(List<PhotographyOrderEntity> entities) {
        return entities.stream()
            .filter(entity -> entity.getDepositReceivedAt() != null)
            .map(entity -> entity.getDepositAmount() == null ? BigDecimal.ZERO : entity.getDepositAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumReceivedFinal(List<PhotographyOrderEntity> entities) {
        return entities.stream()
            .filter(entity -> entity.getFinalReceivedAt() != null)
            .map(entity -> entity.getFinalAmount() == null ? BigDecimal.ZERO : entity.getFinalAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPendingFinal(List<PhotographyOrderEntity> entities) {
        return entities.stream()
            .filter(entity -> entity.getFinalReceivedAt() == null)
            .map(entity -> entity.getFinalAmount() == null ? BigDecimal.ZERO : entity.getFinalAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumReceivedAmount(List<PhotographyOrderEntity> entities) {
        return sumReceivedDeposit(entities).add(sumReceivedFinal(entities)).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isInMonth(PhotographyOrderEntity entity, YearMonth month) {
        return entity.getShootAt() != null && YearMonth.from(entity.getShootAt()).equals(month);
    }

    private boolean isInDate(PhotographyOrderEntity entity, LocalDate date) {
        return entity.getShootAt() != null && entity.getShootAt().toLocalDate().equals(date);
    }

    private YearMonth parseYearMonth(String anchor, YearMonth fallback) {
        try {
            return StringUtils.hasText(anchor) ? YearMonth.parse(anchor.trim()) : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int parseYear(String anchor, int fallback) {
        try {
            return StringUtils.hasText(anchor) ? Integer.parseInt(anchor.trim()) : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private LocalDate parseLocalDate(String value, LocalDate fallback) {
        try {
            return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String toWeekdayShortLabel(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.CHINA);
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

        return accountMapper.selectByIds(accountIds).stream()
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
    }

    private PhotographyOrderResponse toResponse(PhotographyOrderEntity entity, Map<Long, AccountEntity> accountMap) {
        PhotographyOrderResponse response = new PhotographyOrderResponse();
        response.setId(entity.getId());
        response.setOrderNo(entity.getOrderNo());
        response.setUserId(entity.getUserId());
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
