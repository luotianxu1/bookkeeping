package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.TransactionAnalysisCategoryBreakdownResponse;
import com.example.finance.dto.TransactionAnalysisPeriodSummaryResponse;
import com.example.finance.dto.TransactionAnalysisResponse;
import com.example.finance.dto.TransactionAnalysisSummaryResponse;
import com.example.finance.dto.TransactionAnalysisTrendPointResponse;
import com.example.finance.dto.TransactionPageResponse;
import com.example.finance.dto.TransactionRequest;
import com.example.finance.dto.TransactionResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.CategoryEntity;
import com.example.finance.entity.DebtRecordEntity;
import com.example.finance.entity.HumanRelationRecordEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.CategoryMapper;
import com.example.finance.mapper.DebtRecordMapper;
import com.example.finance.mapper.HumanRelationRecordMapper;
import com.example.finance.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final String SOURCE_TRANSACTION = "transaction";
    private static final String SOURCE_DEBT_RECORD = "debt_record";
    private static final String SOURCE_HUMAN_RELATION_RECORD = "human_relation_record";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String DEBT_DIRECTION_PAYABLE = "payable";
    private static final String DEBT_DIRECTION_RECEIVABLE = "receivable";
    private static final String DEBT_RECORD_TYPE_REPAYMENT = "repayment";
    private static final String HUMAN_RELATION_DIRECTION_OUTGOING = "outgoing";
    private static final String HUMAN_RELATION_DIRECTION_INCOMING = "incoming";
    private static final String DEBT_RECORD_ACTIVE_STATUS = "active";
    private static final String HUMAN_RELATION_RECORD_ACTIVE_STATUS = "active";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String DEFAULT_STATUS = "normal";
    private static final String VOIDED_STATUS = "voided";
    private static final String ACTIVE_ACCOUNT_STATUS = "active";
    private static final String ACTIVE_CATEGORY_STATUS = "active";
    private static final String PERIOD_MONTH = "month";
    private static final String PERIOD_YEAR = "year";
    private static final String CATEGORY_COLOR_INCOME = "#F43F5E";
    private static final String CATEGORY_COLOR_EXPENSE = "#10B981";
    private static final String CATEGORY_COLOR_DEBT = "#F59E0B";
    private static final String CATEGORY_COLOR_HUMAN_RELATION = "#8B5CF6";
    private static final DateTimeFormatter TRANSACTION_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final CategoryMapper categoryMapper;
    private final DebtRecordMapper debtRecordMapper;
    private final HumanRelationRecordMapper humanRelationRecordMapper;

    public TransactionService(
        TransactionMapper transactionMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        CategoryMapper categoryMapper,
        DebtRecordMapper debtRecordMapper,
        HumanRelationRecordMapper humanRelationRecordMapper
    ) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.categoryMapper = categoryMapper;
        this.debtRecordMapper = debtRecordMapper;
        this.humanRelationRecordMapper = humanRelationRecordMapper;
    }

    public List<TransactionResponse> list(Long userId, String type, Long accountId) {
        LambdaQueryWrapper<TransactionEntity> wrapper = new LambdaQueryWrapper<TransactionEntity>()
            .eq(userId != null, TransactionEntity::getUserId, userId)
            .eq(StringUtils.hasText(type), TransactionEntity::getType, type)
            .eq(accountId != null, TransactionEntity::getAccountId, accountId)
            .eq(TransactionEntity::getStatus, DEFAULT_STATUS)
            .orderByDesc(TransactionEntity::getOccurredAt)
            .orderByDesc(TransactionEntity::getId);

        List<TransactionEntity> transactions = transactionMapper.selectList(wrapper);
        List<DebtRecordEntity> debtRecords = loadDebtRecords(userId, type, accountId);
        List<HumanRelationRecordEntity> humanRelationRecords = loadHumanRelationRecords(userId, type, accountId);
        return mergeResponses(transactions, debtRecords, humanRelationRecords);
    }

    public TransactionPageResponse page(
        List<Long> userIds,
        String type,
        Long accountId,
        LocalDate startDate,
        LocalDate endDate,
        String sortOrder,
        boolean cashOnly,
        Integer page,
        Integer pageSize
    ) {
        int normalizedPageSize = normalizePageSize(pageSize);
        int normalizedPage = page == null || page < 1 ? 1 : page;
        List<TransactionResponse> transactions = loadMergedTransactions(userIds, type, accountId);
        if (cashOnly) {
            Set<Long> cashAccountIds = loadCashAccountIds(userIds);
            transactions = transactions.stream()
                .filter(transaction -> transaction.getAccountId() != null && cashAccountIds.contains(transaction.getAccountId()))
                .toList();
        }
        transactions = filterTransactionsByDateRange(transactions, startDate, endDate);
        transactions = sortTransactions(transactions, sortOrder);

        int total = transactions.size();
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / normalizedPageSize);
        int resolvedPage = Math.min(normalizedPage, totalPages);
        int fromIndex = Math.min((resolvedPage - 1) * normalizedPageSize, total);
        int toIndex = Math.min(fromIndex + normalizedPageSize, total);

        TransactionPageResponse response = new TransactionPageResponse();
        response.setItems(transactions.subList(fromIndex, toIndex));
        response.setTotal(total);
        response.setPage(resolvedPage);
        response.setPageSize(normalizedPageSize);
        response.setTotalPages(totalPages);
        response.setIncomeTotal(sumAmounts(transactions, TYPE_INCOME));
        response.setExpenseTotal(sumAmounts(transactions, TYPE_EXPENSE));
        response.setBalanceTotal(response.getIncomeTotal().subtract(response.getExpenseTotal()));
        return response;
    }

    public TransactionAnalysisResponse getAnalysis(Long userId, String period, String month, Integer year) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        String normalizedPeriod = normalizePeriod(period);
        Set<Long> cashAccountIds = loadCashAccountIds(userId);
        List<TransactionResponse> cashTransactions = list(userId, null, null).stream()
            .filter(transaction -> transaction.getAccountId() != null && cashAccountIds.contains(transaction.getAccountId()))
            .toList();

        TransactionAnalysisResponse response = new TransactionAnalysisResponse();
        response.setUserId(userId);
        response.setPeriod(normalizedPeriod);

        if (PERIOD_YEAR.equals(normalizedPeriod)) {
            int selectedYear = year == null ? LocalDate.now().getYear() : year;
            response.setYear(selectedYear);

            List<TransactionResponse> selectedTransactions = filterTransactionsByYear(cashTransactions, selectedYear);
            response.setSummary(buildSummary(selectedTransactions));
            response.setIncomeBreakdown(buildBreakdown(selectedTransactions, TYPE_INCOME));
            response.setExpenseBreakdown(buildBreakdown(selectedTransactions, TYPE_EXPENSE));
            response.setTrendPoints(buildYearTrendPoints(selectedYear, selectedTransactions));
            response.setPeriodSummaries(buildYearPeriodSummaries(selectedYear, selectedTransactions));
            return response;
        }

        YearMonth selectedMonth = parseMonth(month);
        response.setMonth(selectedMonth.toString());
        response.setYear(selectedMonth.getYear());

        List<TransactionResponse> selectedTransactions = filterTransactionsByMonth(cashTransactions, selectedMonth);
        response.setSummary(buildSummary(selectedTransactions));
        response.setIncomeBreakdown(buildBreakdown(selectedTransactions, TYPE_INCOME));
        response.setExpenseBreakdown(buildBreakdown(selectedTransactions, TYPE_EXPENSE));
        response.setTrendPoints(buildMonthTrendPoints(selectedMonth, selectedTransactions));
        response.setPeriodSummaries(buildMonthPeriodSummaries(selectedMonth, selectedTransactions));
        return response;
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

        return toTransactionResponse(transactionMapper.selectById(entity.getId()), account, category);
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        TransactionEntity transaction = requireEditableTransaction(id, request.getUserId());
        AccountEntity originalAccount = requireOwnedAccount(request.getUserId(), transaction.getAccountId());
        String type = requireIncomeOrExpense(request.getType());
        AccountEntity account = requireCashAccount(request.getUserId(), request.getAccountId());
        CategoryEntity category = requireCategory(request.getUserId(), request.getCategoryId(), type);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        rollbackAccountBalance(originalAccount, transaction.getType(), transaction.getAmount());
        AccountEntity targetAccount = originalAccount.getId().equals(account.getId()) ? originalAccount : account;
        updateAccountBalance(targetAccount, type, amount);

        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        transaction.setAccountId(targetAccount.getId());
        transaction.setCategoryId(category.getId());
        transaction.setTitle(buildTitle(request, category));
        transaction.setRemark(request.getRemark());
        transaction.setOccurredAt(request.getOccurredAt());
        transactionMapper.updateById(transaction);

        return toTransactionResponse(transactionMapper.selectById(transaction.getId()), targetAccount, category);
    }

    @Transactional
    public boolean delete(Long id, Long userId) {
        TransactionEntity transaction = transactionMapper.selectById(id);
        if (transaction == null || !DEFAULT_STATUS.equals(transaction.getStatus()) || !userId.equals(transaction.getUserId())) {
            return false;
        }

        AccountEntity account = accountMapper.selectById(transaction.getAccountId());
        if (account == null || !userId.equals(account.getUserId())) {
            return false;
        }

        rollbackAccountBalance(account, transaction.getType(), transaction.getAmount());
        transaction.setStatus(VOIDED_STATUS);
        transactionMapper.updateById(transaction);
        return true;
    }

    private String requireIncomeOrExpense(String type) {
        if (!TYPE_EXPENSE.equals(type) && !TYPE_INCOME.equals(type)) {
            throw new IllegalArgumentException("当前只支持支出和收入");
        }
        return type;
    }

    private String normalizePeriod(String period) {
        if (!StringUtils.hasText(period)) {
            return PERIOD_MONTH;
        }
        if (!PERIOD_MONTH.equals(period) && !PERIOD_YEAR.equals(period)) {
            throw new IllegalArgumentException("分析周期仅支持 month 或 year");
        }
        return period;
    }

    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }

        try {
            return YearMonth.parse(month);
        } catch (Exception exception) {
            throw new IllegalArgumentException("月份格式应为 yyyy-MM");
        }
    }

    private AccountEntity requireCashAccount(Long userId, Long accountId) {
        AccountEntity account = requireOwnedAccount(userId, accountId);

        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("请选择现金账户");
        }
        return account;
    }

    private AccountEntity requireOwnedAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("账户不存在");
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
        if (!ACTIVE_CATEGORY_STATUS.equals(category.getStatus())) {
            throw new IllegalArgumentException("分类不可用");
        }
        if (categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getParentId, category.getId())
            .eq(CategoryEntity::getStatus, ACTIVE_CATEGORY_STATUS)) > 0) {
            throw new IllegalArgumentException("请选择二级分类");
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

    private TransactionEntity requireEditableTransaction(Long id, Long userId) {
        TransactionEntity transaction = transactionMapper.selectById(id);
        if (transaction == null || !DEFAULT_STATUS.equals(transaction.getStatus()) || !userId.equals(transaction.getUserId())) {
            throw new IllegalArgumentException("收支记录不存在");
        }
        return transaction;
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

    private void rollbackAccountBalance(AccountEntity account, String type, BigDecimal amount) {
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        BigDecimal nextBalance = TYPE_INCOME.equals(type)
            ? currentBalance.subtract(amount)
            : currentBalance.add(amount);
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("删除后账户余额不足");
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

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private List<TransactionResponse> loadMergedTransactions(Collection<Long> userIds, String type, Long accountId) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<TransactionEntity> wrapper = new LambdaQueryWrapper<TransactionEntity>()
            .in(TransactionEntity::getUserId, userIds)
            .eq(StringUtils.hasText(type), TransactionEntity::getType, type)
            .eq(accountId != null, TransactionEntity::getAccountId, accountId)
            .eq(TransactionEntity::getStatus, DEFAULT_STATUS)
            .orderByDesc(TransactionEntity::getOccurredAt)
            .orderByDesc(TransactionEntity::getId);

        List<TransactionEntity> transactions = transactionMapper.selectList(wrapper);
        List<DebtRecordEntity> debtRecords = loadDebtRecords(userIds, type, accountId);
        List<HumanRelationRecordEntity> humanRelationRecords = loadHumanRelationRecords(userIds, type, accountId);
        return mergeResponses(transactions, debtRecords, humanRelationRecords);
    }

    private List<TransactionResponse> mergeResponses(
        List<TransactionEntity> transactions,
        List<DebtRecordEntity> debtRecords,
        List<HumanRelationRecordEntity> humanRelationRecords
    ) {
        if (transactions.isEmpty() && debtRecords.isEmpty() && humanRelationRecords.isEmpty()) {
            return List.of();
        }

        Set<Long> accountIds = transactions.stream()
            .map(TransactionEntity::getAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        accountIds.addAll(debtRecords.stream()
            .map(DebtRecordEntity::getAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet()));
        accountIds.addAll(debtRecords.stream()
            .map(DebtRecordEntity::getFundingAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet()));
        accountIds.addAll(humanRelationRecords.stream()
            .map(HumanRelationRecordEntity::getAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet()));
        accountIds.addAll(humanRelationRecords.stream()
            .map(HumanRelationRecordEntity::getFundingAccountId)
            .filter(id -> id != null)
            .collect(Collectors.toSet()));
        Set<Long> categoryIds = transactions.stream()
            .map(TransactionEntity::getCategoryId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<Long, AccountEntity> accounts = accountIds.isEmpty()
            ? Collections.emptyMap()
            : accountMapper.selectByIds(accountIds).stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Map<Long, CategoryEntity> categories = categoryIds.isEmpty()
            ? Collections.emptyMap()
            : categoryMapper.selectByIds(categoryIds).stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));

        List<TransactionResponse> expenseIncomeResponses = transactions.stream()
            .map(transaction -> toTransactionResponse(
                transaction,
                transaction.getAccountId() == null ? null : accounts.get(transaction.getAccountId()),
                transaction.getCategoryId() == null ? null : categories.get(transaction.getCategoryId())
            ))
            .toList();
        List<TransactionResponse> debtResponses = debtRecords.stream()
            .map(record -> toDebtRecordResponse(
                record,
                accounts.get(record.getAccountId()),
                accounts.get(record.getFundingAccountId())
            ))
            .toList();
        List<TransactionResponse> humanRelationResponses = humanRelationRecords.stream()
            .map(record -> toHumanRelationRecordResponse(
                record,
                accounts.get(record.getAccountId()),
                accounts.get(record.getFundingAccountId())
            ))
            .toList();

        return java.util.stream.Stream.of(
                expenseIncomeResponses.stream(),
                debtResponses.stream(),
                humanRelationResponses.stream()
            )
            .flatMap(Function.identity())
            .sorted(Comparator
                .comparing(TransactionResponse::getOccurredAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .reversed()
                .thenComparing(
                    Comparator.comparing(TransactionResponse::getSourceId, Comparator.nullsLast(Long::compareTo)).reversed()
                ))
            .toList();
    }

    private List<DebtRecordEntity> loadDebtRecords(Long userId, String type, Long accountId) {
        if (StringUtils.hasText(type) && !TYPE_EXPENSE.equals(type) && !TYPE_INCOME.equals(type)) {
            return List.of();
        }

        LambdaQueryWrapper<DebtRecordEntity> wrapper = new LambdaQueryWrapper<DebtRecordEntity>()
            .eq(userId != null, DebtRecordEntity::getUserId, userId)
            .eq(accountId != null, DebtRecordEntity::getFundingAccountId, accountId)
            .eq(DebtRecordEntity::getStatus, DEBT_RECORD_ACTIVE_STATUS)
            .orderByDesc(DebtRecordEntity::getOccurredAt)
            .orderByDesc(DebtRecordEntity::getId);

        if (TYPE_INCOME.equals(type)) {
            wrapper.eq(DebtRecordEntity::getDirection, DEBT_DIRECTION_RECEIVABLE);
        } else if (TYPE_EXPENSE.equals(type)) {
            wrapper.eq(DebtRecordEntity::getDirection, DEBT_DIRECTION_PAYABLE);
        }

        return debtRecordMapper.selectList(wrapper);
    }

    private List<DebtRecordEntity> loadDebtRecords(Collection<Long> userIds, String type, Long accountId) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (StringUtils.hasText(type) && !TYPE_EXPENSE.equals(type) && !TYPE_INCOME.equals(type)) {
            return List.of();
        }

        LambdaQueryWrapper<DebtRecordEntity> wrapper = new LambdaQueryWrapper<DebtRecordEntity>()
            .in(DebtRecordEntity::getUserId, userIds)
            .eq(accountId != null, DebtRecordEntity::getFundingAccountId, accountId)
            .eq(DebtRecordEntity::getStatus, DEBT_RECORD_ACTIVE_STATUS)
            .orderByDesc(DebtRecordEntity::getOccurredAt)
            .orderByDesc(DebtRecordEntity::getId);

        if (TYPE_INCOME.equals(type)) {
            wrapper.eq(DebtRecordEntity::getDirection, DEBT_DIRECTION_RECEIVABLE);
        } else if (TYPE_EXPENSE.equals(type)) {
            wrapper.eq(DebtRecordEntity::getDirection, DEBT_DIRECTION_PAYABLE);
        }

        return debtRecordMapper.selectList(wrapper);
    }

    private List<HumanRelationRecordEntity> loadHumanRelationRecords(Long userId, String type, Long accountId) {
        if (StringUtils.hasText(type) && !TYPE_EXPENSE.equals(type) && !TYPE_INCOME.equals(type)) {
            return List.of();
        }

        LambdaQueryWrapper<HumanRelationRecordEntity> wrapper = new LambdaQueryWrapper<HumanRelationRecordEntity>()
            .eq(userId != null, HumanRelationRecordEntity::getUserId, userId)
            .eq(accountId != null, HumanRelationRecordEntity::getFundingAccountId, accountId)
            .eq(HumanRelationRecordEntity::getStatus, HUMAN_RELATION_RECORD_ACTIVE_STATUS)
            .orderByDesc(HumanRelationRecordEntity::getOccurredAt)
            .orderByDesc(HumanRelationRecordEntity::getId);

        if (TYPE_INCOME.equals(type)) {
            wrapper.eq(HumanRelationRecordEntity::getDirection, HUMAN_RELATION_DIRECTION_INCOMING);
        } else if (TYPE_EXPENSE.equals(type)) {
            wrapper.eq(HumanRelationRecordEntity::getDirection, HUMAN_RELATION_DIRECTION_OUTGOING);
        }

        return humanRelationRecordMapper.selectList(wrapper);
    }

    private List<HumanRelationRecordEntity> loadHumanRelationRecords(Collection<Long> userIds, String type, Long accountId) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (StringUtils.hasText(type) && !TYPE_EXPENSE.equals(type) && !TYPE_INCOME.equals(type)) {
            return List.of();
        }

        LambdaQueryWrapper<HumanRelationRecordEntity> wrapper = new LambdaQueryWrapper<HumanRelationRecordEntity>()
            .in(HumanRelationRecordEntity::getUserId, userIds)
            .eq(accountId != null, HumanRelationRecordEntity::getFundingAccountId, accountId)
            .eq(HumanRelationRecordEntity::getStatus, HUMAN_RELATION_RECORD_ACTIVE_STATUS)
            .orderByDesc(HumanRelationRecordEntity::getOccurredAt)
            .orderByDesc(HumanRelationRecordEntity::getId);

        if (TYPE_INCOME.equals(type)) {
            wrapper.eq(HumanRelationRecordEntity::getDirection, HUMAN_RELATION_DIRECTION_INCOMING);
        } else if (TYPE_EXPENSE.equals(type)) {
            wrapper.eq(HumanRelationRecordEntity::getDirection, HUMAN_RELATION_DIRECTION_OUTGOING);
        }

        return humanRelationRecordMapper.selectList(wrapper);
    }

    private TransactionResponse toTransactionResponse(TransactionEntity entity, AccountEntity account, CategoryEntity category) {
        TransactionResponse response = new TransactionResponse();
        response.setId(entity.getId());
        response.setSourceId(entity.getId());
        response.setSourceType(SOURCE_TRANSACTION);
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
        response.setCategoryColor(category == null ? null : category.getColor());
        response.setTitle(entity.getTitle());
        response.setRemark(entity.getRemark());
        response.setOccurredAt(entity.getOccurredAt());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private TransactionResponse toDebtRecordResponse(
        DebtRecordEntity entity,
        AccountEntity debtAccount,
        AccountEntity fundingAccount
    ) {
        TransactionResponse response = new TransactionResponse();
        response.setId(entity.getId() == null ? null : -entity.getId());
        response.setSourceId(entity.getId());
        response.setSourceType(SOURCE_DEBT_RECORD);
        response.setTransactionNo("DEBT-" + entity.getId());
        response.setUserId(entity.getUserId());
        boolean isRepayment = DEBT_RECORD_TYPE_REPAYMENT.equalsIgnoreCase(entity.getRecordType());
        response.setType(resolveDebtTransactionType(entity.getDirection(), isRepayment));
        response.setAmount(entity.getAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setAccountId(fundingAccount == null ? entity.getAccountId() : fundingAccount.getId());
        response.setAccountName(fundingAccount == null ? (debtAccount == null ? null : debtAccount.getName()) : fundingAccount.getName());
        response.setCategoryId(null);
        response.setCategoryName(buildDebtCategoryName(entity.getDirection(), isRepayment));
        response.setCategoryIcon("债");
        response.setCategoryColor(CATEGORY_COLOR_DEBT);
        response.setTitle(buildDebtRecordTitle(debtAccount, entity));
        response.setRemark(entity.getRemark());
        response.setOccurredAt(entity.getOccurredAt());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String buildDebtRecordTitle(AccountEntity account, DebtRecordEntity entity) {
        String accountName = account == null ? "债务账户" : account.getName();
        boolean isRepayment = DEBT_RECORD_TYPE_REPAYMENT.equalsIgnoreCase(entity.getRecordType());
        String directionLabel = buildDebtActionLabel(entity.getDirection(), isRepayment);
        return accountName + " · " + directionLabel;
    }

    private String resolveDebtTransactionType(String direction, boolean isRepayment) {
        if (DEBT_DIRECTION_RECEIVABLE.equals(direction)) {
            return isRepayment ? TYPE_INCOME : TYPE_EXPENSE;
        }
        return isRepayment ? TYPE_EXPENSE : TYPE_INCOME;
    }

    private String buildDebtCategoryName(String direction, boolean isRepayment) {
        if (DEBT_DIRECTION_RECEIVABLE.equals(direction)) {
            return isRepayment ? "债务收款" : "债务借出";
        }
        return isRepayment ? "债务还款" : "债务借入";
    }

    private String buildDebtActionLabel(String direction, boolean isRepayment) {
        if (DEBT_DIRECTION_RECEIVABLE.equals(direction)) {
            return isRepayment ? "收款" : "借出";
        }
        return isRepayment ? "还款" : "借入";
    }

    private TransactionResponse toHumanRelationRecordResponse(
        HumanRelationRecordEntity entity,
        AccountEntity humanRelationAccount,
        AccountEntity fundingAccount
    ) {
        TransactionResponse response = new TransactionResponse();
        response.setId(entity.getId() == null ? null : -1000000L - entity.getId());
        response.setSourceId(entity.getId());
        response.setSourceType(SOURCE_HUMAN_RELATION_RECORD);
        response.setTransactionNo("HUMAN-" + entity.getId());
        response.setUserId(entity.getUserId());
        response.setType(HUMAN_RELATION_DIRECTION_INCOMING.equals(entity.getDirection()) ? TYPE_INCOME : TYPE_EXPENSE);
        response.setAmount(entity.getAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setAccountId(fundingAccount == null ? entity.getAccountId() : fundingAccount.getId());
        response.setAccountName(fundingAccount == null
            ? (humanRelationAccount == null ? null : humanRelationAccount.getName())
            : fundingAccount.getName());
        response.setCategoryId(null);
        response.setCategoryName(HUMAN_RELATION_DIRECTION_INCOMING.equals(entity.getDirection()) ? "人情收到" : "人情送出");
        response.setCategoryIcon("礼");
        response.setCategoryColor(CATEGORY_COLOR_HUMAN_RELATION);
        response.setTitle(buildHumanRelationRecordTitle(humanRelationAccount, entity));
        response.setRemark(entity.getRemark());
        response.setOccurredAt(entity.getOccurredAt());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String buildHumanRelationRecordTitle(AccountEntity account, HumanRelationRecordEntity entity) {
        String accountName = account == null ? "人情账户" : account.getName();
        String directionLabel = HUMAN_RELATION_DIRECTION_INCOMING.equals(entity.getDirection()) ? "收到" : "送出";
        return accountName + " · " + directionLabel;
    }

    private Set<Long> loadCashAccountIds(Long userId) {
        AccountTypeEntity cashAccountType = accountTypeMapper.selectOne(new LambdaQueryWrapper<AccountTypeEntity>()
            .eq(AccountTypeEntity::getCode, CASH_ACCOUNT_TYPE_CODE)
            .last("LIMIT 1"));
        if (cashAccountType == null) {
            return Set.of();
        }

        return accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
                .eq(AccountEntity::getUserId, userId)
                .eq(AccountEntity::getAccountTypeId, cashAccountType.getId())
                .eq(AccountEntity::getStatus, ACTIVE_ACCOUNT_STATUS))
            .stream()
            .map(AccountEntity::getId)
            .collect(Collectors.toSet());
    }

    private Set<Long> loadCashAccountIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }

        AccountTypeEntity cashAccountType = accountTypeMapper.selectOne(new LambdaQueryWrapper<AccountTypeEntity>()
            .eq(AccountTypeEntity::getCode, CASH_ACCOUNT_TYPE_CODE)
            .last("LIMIT 1"));
        if (cashAccountType == null) {
            return Set.of();
        }

        return accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
                .in(AccountEntity::getUserId, userIds)
                .eq(AccountEntity::getAccountTypeId, cashAccountType.getId())
                .eq(AccountEntity::getStatus, ACTIVE_ACCOUNT_STATUS))
            .stream()
            .map(AccountEntity::getId)
            .collect(Collectors.toSet());
    }

    private List<TransactionResponse> filterTransactionsByMonth(List<TransactionResponse> transactions, YearMonth month) {
        LocalDateTime startTime = month.atDay(1).atStartOfDay();
        LocalDateTime endTime = month.plusMonths(1).atDay(1).atStartOfDay();
        return transactions.stream()
            .filter(transaction -> transaction.getOccurredAt() != null)
            .filter(transaction -> !transaction.getOccurredAt().isBefore(startTime) && transaction.getOccurredAt().isBefore(endTime))
            .sorted(transactionComparator())
            .toList();
    }

    private List<TransactionResponse> filterTransactionsByYear(List<TransactionResponse> transactions, int year) {
        LocalDateTime startTime = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime endTime = LocalDate.of(year + 1, 1, 1).atStartOfDay();
        return transactions.stream()
            .filter(transaction -> transaction.getOccurredAt() != null)
            .filter(transaction -> !transaction.getOccurredAt().isBefore(startTime) && transaction.getOccurredAt().isBefore(endTime))
            .sorted(transactionComparator())
            .toList();
    }

    private List<TransactionResponse> filterTransactionsByDateRange(
        List<TransactionResponse> transactions,
        LocalDate startDate,
        LocalDate endDate
    ) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        if (startTime == null && endTime == null) {
            return transactions;
        }

        return transactions.stream()
            .filter(transaction -> transaction.getOccurredAt() != null)
            .filter(transaction -> startTime == null || !transaction.getOccurredAt().isBefore(startTime))
            .filter(transaction -> endTime == null || transaction.getOccurredAt().isBefore(endTime))
            .toList();
    }

    private List<TransactionResponse> sortTransactions(List<TransactionResponse> transactions, String sortOrder) {
        Comparator<TransactionResponse> comparator = transactionComparator();
        if ("asc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        return transactions.stream().sorted(comparator).toList();
    }

    private TransactionAnalysisSummaryResponse buildSummary(List<TransactionResponse> transactions) {
        BigDecimal income = sumAmounts(transactions, TYPE_INCOME);
        BigDecimal expense = sumAmounts(transactions, TYPE_EXPENSE);

        TransactionAnalysisSummaryResponse response = new TransactionAnalysisSummaryResponse();
        response.setIncome(income);
        response.setExpense(expense);
        response.setSurplus(income.subtract(expense).setScale(2, RoundingMode.HALF_UP));
        response.setIncomeCount((int) transactions.stream().filter(transaction -> TYPE_INCOME.equals(transaction.getType())).count());
        response.setExpenseCount((int) transactions.stream().filter(transaction -> TYPE_EXPENSE.equals(transaction.getType())).count());
        response.setTransactionCount(transactions.size());
        return response;
    }

    private List<TransactionAnalysisCategoryBreakdownResponse> buildBreakdown(List<TransactionResponse> transactions, String type) {
        List<TransactionResponse> filteredTransactions = transactions.stream()
            .filter(transaction -> type.equals(transaction.getType()))
            .toList();
        BigDecimal totalAmount = filteredTransactions.stream()
            .map(this::safeAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        Map<String, BreakdownAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (TransactionResponse transaction : filteredTransactions) {
            String key = transaction.getCategoryId() == null
                ? String.valueOf(transaction.getSourceType()) + ":" + String.valueOf(transaction.getCategoryName())
                : "category:" + transaction.getCategoryId();
            BreakdownAccumulator accumulator = accumulatorMap.computeIfAbsent(key, ignored -> new BreakdownAccumulator(
                transaction.getCategoryId(),
                defaultCategoryName(transaction),
                StringUtils.hasText(transaction.getCategoryIcon()) ? transaction.getCategoryIcon() : "账",
                resolveCategoryColor(transaction)
            ));
            accumulator.amount = accumulator.amount.add(safeAmount(transaction));
            accumulator.transactionCount += 1;
        }

        return accumulatorMap.values().stream()
            .sorted(Comparator.comparing((BreakdownAccumulator item) -> item.amount).reversed())
            .map(item -> {
                TransactionAnalysisCategoryBreakdownResponse response = new TransactionAnalysisCategoryBreakdownResponse();
                response.setCategoryId(item.categoryId);
                response.setCategoryName(item.categoryName);
                response.setCategoryIcon(item.categoryIcon);
                response.setCategoryColor(item.categoryColor);
                response.setAmount(item.amount.setScale(2, RoundingMode.HALF_UP));
                response.setPercent(item.amount.multiply(BigDecimal.valueOf(100))
                    .divide(totalAmount, 2, RoundingMode.HALF_UP));
                response.setTransactionCount(item.transactionCount);
                return response;
            })
            .toList();
    }

    private List<TransactionAnalysisTrendPointResponse> buildMonthTrendPoints(YearMonth month, List<TransactionResponse> transactions) {
        Map<LocalDate, List<TransactionResponse>> groupedTransactions = transactions.stream()
            .collect(Collectors.groupingBy(transaction -> transaction.getOccurredAt().toLocalDate()));
        return java.util.stream.IntStream.rangeClosed(1, month.lengthOfMonth())
            .mapToObj(day -> {
                LocalDate currentDate = month.atDay(day);
                List<TransactionResponse> currentTransactions = groupedTransactions.getOrDefault(currentDate, List.of());
                TransactionAnalysisTrendPointResponse response = new TransactionAnalysisTrendPointResponse();
                response.setKey(currentDate.toString());
                response.setLabel(String.valueOf(day));
                response.setIncome(sumAmounts(currentTransactions, TYPE_INCOME));
                response.setExpense(sumAmounts(currentTransactions, TYPE_EXPENSE));
                response.setSurplus(response.getIncome().subtract(response.getExpense()).setScale(2, RoundingMode.HALF_UP));
                return response;
            })
            .toList();
    }

    private List<TransactionAnalysisTrendPointResponse> buildYearTrendPoints(int year, List<TransactionResponse> transactions) {
        Map<YearMonth, List<TransactionResponse>> groupedTransactions = transactions.stream()
            .collect(Collectors.groupingBy(transaction -> YearMonth.from(transaction.getOccurredAt())));
        return java.util.stream.IntStream.rangeClosed(1, 12)
            .mapToObj(month -> {
                YearMonth currentMonth = YearMonth.of(year, month);
                List<TransactionResponse> currentTransactions = groupedTransactions.getOrDefault(currentMonth, List.of());
                TransactionAnalysisTrendPointResponse response = new TransactionAnalysisTrendPointResponse();
                response.setKey(currentMonth.toString());
                response.setLabel(month + "月");
                response.setIncome(sumAmounts(currentTransactions, TYPE_INCOME));
                response.setExpense(sumAmounts(currentTransactions, TYPE_EXPENSE));
                response.setSurplus(response.getIncome().subtract(response.getExpense()).setScale(2, RoundingMode.HALF_UP));
                return response;
            })
            .toList();
    }

    private List<TransactionAnalysisPeriodSummaryResponse> buildMonthPeriodSummaries(YearMonth month, List<TransactionResponse> transactions) {
        Map<LocalDate, List<TransactionResponse>> groupedTransactions = transactions.stream()
            .collect(Collectors.groupingBy(transaction -> transaction.getOccurredAt().toLocalDate()));
        return java.util.stream.IntStream.rangeClosed(1, month.lengthOfMonth())
            .mapToObj(day -> buildPeriodSummary(
                month.atDay(day).toString(),
                String.valueOf(day),
                groupedTransactions.getOrDefault(month.atDay(day), List.of())
            ))
            .toList();
    }

    private List<TransactionAnalysisPeriodSummaryResponse> buildYearPeriodSummaries(int year, List<TransactionResponse> transactions) {
        Map<YearMonth, List<TransactionResponse>> groupedTransactions = transactions.stream()
            .collect(Collectors.groupingBy(transaction -> YearMonth.from(transaction.getOccurredAt())));
        return java.util.stream.IntStream.rangeClosed(1, 12)
            .mapToObj(month -> {
                YearMonth currentMonth = YearMonth.of(year, month);
                return buildPeriodSummary(currentMonth.toString(), month + "月", groupedTransactions.getOrDefault(currentMonth, List.of()));
            })
            .toList();
    }

    private TransactionAnalysisPeriodSummaryResponse buildPeriodSummary(
        String key,
        String label,
        List<TransactionResponse> transactions
    ) {
        List<TransactionResponse> sortedTransactions = transactions.stream()
            .sorted(transactionComparator())
            .toList();
        BigDecimal income = sumAmounts(sortedTransactions, TYPE_INCOME);
        BigDecimal expense = sumAmounts(sortedTransactions, TYPE_EXPENSE);

        TransactionAnalysisPeriodSummaryResponse response = new TransactionAnalysisPeriodSummaryResponse();
        response.setKey(key);
        response.setLabel(label);
        response.setIncome(income);
        response.setExpense(expense);
        response.setSurplus(income.subtract(expense).setScale(2, RoundingMode.HALF_UP));
        response.setTransactionCount(sortedTransactions.size());
        response.setTransactions(sortedTransactions);
        return response;
    }

    private BigDecimal sumAmounts(List<TransactionResponse> transactions, String type) {
        return transactions.stream()
            .filter(transaction -> type.equals(transaction.getType()))
            .map(this::safeAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeAmount(TransactionResponse transaction) {
        return transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount();
    }

    private String defaultCategoryName(TransactionResponse transaction) {
        if (StringUtils.hasText(transaction.getCategoryName())) {
            return transaction.getCategoryName();
        }
        return TYPE_INCOME.equals(transaction.getType()) ? "收入" : "支出";
    }

    private String resolveCategoryColor(TransactionResponse transaction) {
        if (StringUtils.hasText(transaction.getCategoryColor())) {
            return transaction.getCategoryColor();
        }
        if (SOURCE_DEBT_RECORD.equals(transaction.getSourceType())) {
            return CATEGORY_COLOR_DEBT;
        }
        if (SOURCE_HUMAN_RELATION_RECORD.equals(transaction.getSourceType())) {
            return CATEGORY_COLOR_HUMAN_RELATION;
        }
        return TYPE_INCOME.equals(transaction.getType()) ? CATEGORY_COLOR_INCOME : CATEGORY_COLOR_EXPENSE;
    }

    private Comparator<TransactionResponse> transactionComparator() {
        return Comparator
            .comparing(TransactionResponse::getOccurredAt, Comparator.nullsLast(LocalDateTime::compareTo))
            .reversed()
            .thenComparing(
                Comparator.comparing(TransactionResponse::getSourceId, Comparator.nullsLast(Long::compareTo)).reversed()
            );
    }

    private static class BreakdownAccumulator {

        private final Long categoryId;
        private final String categoryName;
        private final String categoryIcon;
        private final String categoryColor;
        private BigDecimal amount = BigDecimal.ZERO;
        private int transactionCount = 0;

        private BreakdownAccumulator(Long categoryId, String categoryName, String categoryIcon, String categoryColor) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.categoryIcon = categoryIcon;
            this.categoryColor = categoryColor;
        }
    }
}
