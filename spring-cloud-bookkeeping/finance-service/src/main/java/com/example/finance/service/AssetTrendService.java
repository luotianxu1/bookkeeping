package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.AccountResponse;
import com.example.finance.dto.AssetTrendAllocationResponse;
import com.example.finance.dto.AssetTrendContributorResponse;
import com.example.finance.dto.AssetTrendPointResponse;
import com.example.finance.dto.AssetTrendResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AssetDailySnapshotEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.DebtRecordEntity;
import com.example.finance.entity.HumanRelationRecordEntity;
import com.example.finance.entity.InvestmentPositionEntity;
import com.example.finance.entity.InvestmentPriceQuoteEntity;
import com.example.finance.entity.InvestmentProductEntity;
import com.example.finance.entity.InvestmentTransactionEntity;
import com.example.finance.entity.LiabilityRecordEntity;
import com.example.finance.entity.TransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.DebtRecordMapper;
import com.example.finance.mapper.HumanRelationRecordMapper;
import com.example.finance.mapper.InvestmentPositionMapper;
import com.example.finance.mapper.InvestmentPriceQuoteMapper;
import com.example.finance.mapper.InvestmentProductMapper;
import com.example.finance.mapper.InvestmentTransactionMapper;
import com.example.finance.mapper.LiabilityRecordMapper;
import com.example.finance.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssetTrendService {

    private record ProfitSummary(
        BigDecimal amount,
        BigDecimal rate
    ) {
    }

    private record TrendRangeMeta(
        String rangeKey,
        String rangeLabel,
        LocalDate startDate,
        LocalDate endDate,
        boolean monthlyBuckets,
        boolean yearlyBuckets
    ) {
    }

    private record TrendAccount(
        Long id,
        String name,
        String accountTypeCode,
        String accountTypeName,
        String accountCategory,
        String balanceDirection,
        BigDecimal currentBalance,
        LocalDate createdDate,
        LocalDateTime updatedAt
    ) {
    }

    private record TrendContext(
        Map<Long, List<TransactionEntity>> transactionsByAccountId,
        Map<Long, List<InvestmentPositionEntity>> positionsByAccountId,
        Map<Long, List<InvestmentTransactionEntity>> investmentTransactionsByPositionId,
        Map<Long, List<InvestmentTransactionEntity>> investmentFundingTransactionsByAccountId,
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId,
        Map<Long, List<DebtRecordEntity>> debtRecordsByAccountId,
        Map<Long, List<DebtRecordEntity>> debtFundingRecordsByAccountId,
        Map<Long, List<LiabilityRecordEntity>> liabilityRecordsByAccountId,
        Map<Long, List<HumanRelationRecordEntity>> humanRelationRecordsByAccountId,
        Map<Long, List<HumanRelationRecordEntity>> humanRelationFundingRecordsByAccountId,
        LocalDate earliestActivityDate,
        LocalDateTime lastSyncedAt
    ) {
    }

    private static final String DEFAULT_ACCOUNT_STATUS = "active";
    private static final String NORMAL_STATUS = "normal";
    private static final String VOIDED_STATUS = "voided";
    private static final String POSITION_STATUS_ACTIVE = "active";
    private static final String POSITION_STATUS_CLOSED = "closed";
    private static final String TRANSACTION_TYPE_INCOME = "income";
    private static final String TRANSACTION_TYPE_EXPENSE = "expense";
    private static final String TRANSACTION_TYPE_TRANSFER = "transfer";
    private static final String DEBT_DIRECTION_PAYABLE = "payable";
    private static final String DEBT_RECORD_TYPE_REPAYMENT = "repayment";
    private static final String HUMAN_RELATION_DIRECTION_OUTGOING = "outgoing";
    private static final String LIABILITY_REPAYMENT_STATUS_PENDING = "pending";
    private static final String LIABILITY_REPAYMENT_STATUS_PAID = "paid";
    private static final String SETTLEMENT_STATUS_PENDING = "pending";
    private static final Set<String> POSITION_ACCOUNT_TYPE_CODES = Set.of("investment", "gold");
    private static final Set<String> DEBT_ACCOUNT_TYPE_CODES = Set.of("debt");
    private static final String LIABILITY_ACCOUNT_TYPE_CODE = "liability";
    private static final Set<String> LIABILITY_ACCOUNT_CATEGORIES = Set.of("liability");

    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final TransactionMapper transactionMapper;
    private final InvestmentPositionMapper investmentPositionMapper;
    private final InvestmentTransactionMapper investmentTransactionMapper;
    private final InvestmentPriceQuoteMapper investmentPriceQuoteMapper;
    private final InvestmentProductMapper investmentProductMapper;
    private final DebtRecordMapper debtRecordMapper;
    private final LiabilityRecordMapper liabilityRecordMapper;
    private final HumanRelationRecordMapper humanRelationRecordMapper;
    private final AccountService accountService;
    private final GoldPriceService goldPriceService;
    private final AssetSnapshotService assetSnapshotService;

    public AssetTrendService(
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        TransactionMapper transactionMapper,
        InvestmentPositionMapper investmentPositionMapper,
        InvestmentTransactionMapper investmentTransactionMapper,
        InvestmentPriceQuoteMapper investmentPriceQuoteMapper,
        InvestmentProductMapper investmentProductMapper,
        DebtRecordMapper debtRecordMapper,
        LiabilityRecordMapper liabilityRecordMapper,
        HumanRelationRecordMapper humanRelationRecordMapper,
        AccountService accountService,
        GoldPriceService goldPriceService,
        AssetSnapshotService assetSnapshotService
    ) {
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.transactionMapper = transactionMapper;
        this.investmentPositionMapper = investmentPositionMapper;
        this.investmentTransactionMapper = investmentTransactionMapper;
        this.investmentPriceQuoteMapper = investmentPriceQuoteMapper;
        this.investmentProductMapper = investmentProductMapper;
        this.debtRecordMapper = debtRecordMapper;
        this.liabilityRecordMapper = liabilityRecordMapper;
        this.humanRelationRecordMapper = humanRelationRecordMapper;
        this.accountService = accountService;
        this.goldPriceService = goldPriceService;
        this.assetSnapshotService = assetSnapshotService;
    }

    public AssetTrendResponse trend(Long userId, Long accountId, String range) {
        List<TrendAccount> accounts = loadTrendAccounts(userId, accountId);
        TrendContext context = buildTrendContext(userId, accounts);
        TrendRangeMeta rangeMeta = resolveTrendRange(range, accounts, context);
        List<LocalDate> bucketDates = buildTrendBucketDates(rangeMeta);
        List<AssetTrendPointResponse> trendPoints = buildTrendPoints(userId, accountId, accounts, context, rangeMeta, bucketDates);
        TrendRangeMeta allRangeMeta = resolveTrendRange("all", accounts, context);
        ProfitSummary cumulativeSummary = buildCumulativeSummary(
            accounts,
            context,
            allRangeMeta.startDate(),
            allRangeMeta.endDate()
        );

        BigDecimal totalAssets = sumCurrentAssets(accounts);
        BigDecimal periodChangeAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal periodChangeRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (trendPoints.size() >= 2) {
            BigDecimal startValue = defaultZero(trendPoints.get(0).getValue()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal endValue = defaultZero(trendPoints.get(trendPoints.size() - 1).getValue()).setScale(2, RoundingMode.HALF_UP);
            periodChangeAmount = endValue.subtract(startValue).setScale(2, RoundingMode.HALF_UP);
            periodChangeRate = rate(periodChangeAmount, startValue);
        }

        AssetTrendResponse response = new AssetTrendResponse();
        response.setUserId(userId);
        response.setAccountId(accountId);
        response.setRange(rangeMeta.rangeKey());
        response.setRangeLabel(rangeMeta.rangeLabel());
        response.setStartDate(rangeMeta.startDate());
        response.setEndDate(rangeMeta.endDate());
        response.setTotalAssets(totalAssets);
        response.setCumulativeProfit(cumulativeSummary.amount());
        response.setCumulativeProfitRate(cumulativeSummary.rate());
        response.setPeriodChangeAmount(periodChangeAmount);
        response.setPeriodChangeRate(periodChangeRate);
        response.setLastSyncedAt(context.lastSyncedAt());
        response.setTrendPoints(trendPoints);
        response.setAllocations(buildAllocations(accounts));
        response.setContributors(buildContributors(accounts, context, rangeMeta));
        return response;
    }

    private List<TrendAccount> loadTrendAccounts(Long userId, Long accountId) {
        List<AccountEntity> accountEntities = accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(AccountEntity::getUserId, userId)
            .eq(AccountEntity::getStatus, DEFAULT_ACCOUNT_STATUS)
            .eq(AccountEntity::getIncludeInNetWorth, true)
            .eq(accountId != null, AccountEntity::getId, accountId)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId));
        if (accountEntities.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> accountTypeIds = accountEntities.stream()
            .map(AccountEntity::getAccountTypeId)
            .collect(Collectors.toSet());
        Map<Long, AccountTypeEntity> accountTypes = accountTypeMapper.selectByIds(accountTypeIds).stream()
            .collect(Collectors.toMap(AccountTypeEntity::getId, Function.identity()));
        Map<Long, AccountResponse> accountResponses = accountService.list(userId, null, DEFAULT_ACCOUNT_STATUS).stream()
            .filter(item -> Boolean.TRUE.equals(item.getIncludeInNetWorth()))
            .filter(item -> accountId == null || accountId.equals(item.getId()))
            .collect(Collectors.toMap(AccountResponse::getId, Function.identity()));

        return accountEntities.stream()
            .map(entity -> {
                AccountTypeEntity accountType = accountTypes.get(entity.getAccountTypeId());
                AccountResponse response = accountResponses.get(entity.getId());
                return new TrendAccount(
                    entity.getId(),
                    entity.getName(),
                    response == null ? accountType == null ? null : accountType.getCode() : response.getAccountTypeCode(),
                    response == null ? accountType == null ? null : accountType.getName() : response.getAccountTypeName(),
                    accountType == null ? null : accountType.getCategory(),
                    accountType == null ? null : accountType.getBalanceDirection(),
                    normalizeAccountBalance(
                        response == null ? accountType == null ? null : accountType.getCode() : response.getAccountTypeCode(),
                        accountType == null ? null : accountType.getBalanceDirection(),
                        response == null ? defaultZero(entity.getCurrentBalance()).setScale(2, RoundingMode.HALF_UP)
                            : defaultZero(response.getCurrentBalance()).setScale(2, RoundingMode.HALF_UP)
                    ),
                    entity.getCreatedAt() == null ? null : entity.getCreatedAt().toLocalDate(),
                    latestTime(entity.getUpdatedAt(), response == null ? null : response.getUpdatedAt())
                );
            })
            .toList();
    }

    private TrendContext buildTrendContext(Long userId, List<TrendAccount> accounts) {
        if (accounts.isEmpty()) {
            return new TrendContext(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                null,
                null
            );
        }

        Set<Long> accountIds = accounts.stream().map(TrendAccount::id).collect(Collectors.toSet());
        Set<Long> positionAccountIds = accounts.stream()
            .filter(item -> POSITION_ACCOUNT_TYPE_CODES.contains(item.accountTypeCode()))
            .map(TrendAccount::id)
            .collect(Collectors.toSet());

        Map<Long, List<TransactionEntity>> transactionsByAccountId = new HashMap<>();
        LocalDate earliestActivityDate = null;
        LocalDateTime lastSyncedAt = accounts.stream()
            .map(TrendAccount::updatedAt)
            .filter(item -> item != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        for (TransactionEntity transaction : transactionMapper.selectList(new LambdaQueryWrapper<TransactionEntity>()
            .eq(TransactionEntity::getUserId, userId)
            .eq(TransactionEntity::getStatus, NORMAL_STATUS)
            .orderByAsc(TransactionEntity::getOccurredAt)
            .orderByAsc(TransactionEntity::getId))) {
            LocalDate occurredDate = transaction.getOccurredAt() == null ? null : transaction.getOccurredAt().toLocalDate();
            boolean related = false;
            if (transaction.getAccountId() != null && accountIds.contains(transaction.getAccountId())) {
                transactionsByAccountId.computeIfAbsent(transaction.getAccountId(), key -> new ArrayList<>()).add(transaction);
                related = true;
            }
            if (transaction.getFromAccountId() != null && accountIds.contains(transaction.getFromAccountId())) {
                transactionsByAccountId.computeIfAbsent(transaction.getFromAccountId(), key -> new ArrayList<>()).add(transaction);
                related = true;
            }
            if (transaction.getToAccountId() != null && accountIds.contains(transaction.getToAccountId())) {
                transactionsByAccountId.computeIfAbsent(transaction.getToAccountId(), key -> new ArrayList<>()).add(transaction);
                related = true;
            }
            if (related) {
                earliestActivityDate = earliestDate(earliestActivityDate, occurredDate);
                lastSyncedAt = latestTime(lastSyncedAt, transaction.getUpdatedAt());
            }
        }

        List<InvestmentPositionEntity> positions = positionAccountIds.isEmpty()
            ? Collections.emptyList()
            : investmentPositionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .in(InvestmentPositionEntity::getAccountId, positionAccountIds)
                .in(InvestmentPositionEntity::getStatus, POSITION_STATUS_ACTIVE, POSITION_STATUS_CLOSED));
        Map<Long, List<InvestmentPositionEntity>> positionsByAccountId = positions.stream()
            .collect(Collectors.groupingBy(InvestmentPositionEntity::getAccountId));
        Set<Long> productIds = positions.stream().map(InvestmentPositionEntity::getProductId).collect(Collectors.toSet());
        Set<Long> positionIds = positions.stream().map(InvestmentPositionEntity::getId).collect(Collectors.toSet());
        for (InvestmentPositionEntity position : positions) {
            earliestActivityDate = earliestDate(earliestActivityDate, position.getCreatedAt() == null ? null : position.getCreatedAt().toLocalDate());
            lastSyncedAt = latestTime(lastSyncedAt, position.getLastSyncedAt());
            lastSyncedAt = latestTime(lastSyncedAt, position.getUpdatedAt());
        }

        Map<Long, List<InvestmentTransactionEntity>> investmentTransactionsByPositionId = new HashMap<>();
        Map<Long, List<InvestmentTransactionEntity>> investmentFundingTransactionsByAccountId = new HashMap<>();
        for (InvestmentTransactionEntity transaction : investmentTransactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getUserId, userId)
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByAsc(InvestmentTransactionEntity::getTradeAt)
            .orderByAsc(InvestmentTransactionEntity::getId))) {
            if (transaction.getPositionId() != null && positionIds.contains(transaction.getPositionId())) {
                investmentTransactionsByPositionId.computeIfAbsent(transaction.getPositionId(), key -> new ArrayList<>()).add(transaction);
                earliestActivityDate = earliestDate(earliestActivityDate, investmentTransactionStartDate(transaction));
                lastSyncedAt = latestTime(lastSyncedAt, transaction.getSettlementConfirmedAt());
                lastSyncedAt = latestTime(lastSyncedAt, transaction.getUpdatedAt());
            }
            if (transaction.getFundingAccountId() != null && accountIds.contains(transaction.getFundingAccountId())) {
                investmentFundingTransactionsByAccountId.computeIfAbsent(transaction.getFundingAccountId(), key -> new ArrayList<>()).add(transaction);
                earliestActivityDate = earliestDate(earliestActivityDate, investmentTransactionStartDate(transaction));
                lastSyncedAt = latestTime(lastSyncedAt, transaction.getSettlementConfirmedAt());
                lastSyncedAt = latestTime(lastSyncedAt, transaction.getUpdatedAt());
            }
        }

        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId = loadPriceHistoryByProduct(productIds);

        Map<Long, List<DebtRecordEntity>> debtRecordsByAccountId = new HashMap<>();
        Map<Long, List<DebtRecordEntity>> debtFundingRecordsByAccountId = new HashMap<>();
        for (DebtRecordEntity record : debtRecordMapper.selectList(new LambdaQueryWrapper<DebtRecordEntity>()
            .eq(DebtRecordEntity::getUserId, userId)
            .eq(DebtRecordEntity::getStatus, DEFAULT_ACCOUNT_STATUS)
            .orderByAsc(DebtRecordEntity::getOccurredAt)
            .orderByAsc(DebtRecordEntity::getId))) {
            if (record.getAccountId() != null && accountIds.contains(record.getAccountId())) {
                debtRecordsByAccountId.computeIfAbsent(record.getAccountId(), key -> new ArrayList<>()).add(record);
                earliestActivityDate = earliestDate(earliestActivityDate, record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate());
                lastSyncedAt = latestTime(lastSyncedAt, record.getUpdatedAt());
            }
            if (record.getFundingAccountId() != null && accountIds.contains(record.getFundingAccountId())) {
                debtFundingRecordsByAccountId.computeIfAbsent(record.getFundingAccountId(), key -> new ArrayList<>()).add(record);
                earliestActivityDate = earliestDate(earliestActivityDate, record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate());
                lastSyncedAt = latestTime(lastSyncedAt, record.getUpdatedAt());
            }
        }

        Map<Long, List<LiabilityRecordEntity>> liabilityRecordsByAccountId = new HashMap<>();
        for (LiabilityRecordEntity record : liabilityRecordMapper.selectList(new LambdaQueryWrapper<LiabilityRecordEntity>()
            .eq(LiabilityRecordEntity::getUserId, userId)
            .eq(LiabilityRecordEntity::getStatus, DEFAULT_ACCOUNT_STATUS)
            .orderByAsc(LiabilityRecordEntity::getOccurredAt)
            .orderByAsc(LiabilityRecordEntity::getId))) {
            if (record.getAccountId() != null && accountIds.contains(record.getAccountId())) {
                liabilityRecordsByAccountId.computeIfAbsent(record.getAccountId(), key -> new ArrayList<>()).add(record);
                earliestActivityDate = earliestDate(earliestActivityDate, record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate());
                lastSyncedAt = latestTime(lastSyncedAt, record.getUpdatedAt());
            }
        }

        Map<Long, List<HumanRelationRecordEntity>> humanRelationRecordsByAccountId = new HashMap<>();
        Map<Long, List<HumanRelationRecordEntity>> humanRelationFundingRecordsByAccountId = new HashMap<>();
        for (HumanRelationRecordEntity record : humanRelationRecordMapper.selectList(new LambdaQueryWrapper<HumanRelationRecordEntity>()
            .eq(HumanRelationRecordEntity::getUserId, userId)
            .eq(HumanRelationRecordEntity::getStatus, DEFAULT_ACCOUNT_STATUS)
            .orderByAsc(HumanRelationRecordEntity::getOccurredAt)
            .orderByAsc(HumanRelationRecordEntity::getId))) {
            if (record.getAccountId() != null && accountIds.contains(record.getAccountId())) {
                humanRelationRecordsByAccountId.computeIfAbsent(record.getAccountId(), key -> new ArrayList<>()).add(record);
                earliestActivityDate = earliestDate(earliestActivityDate, record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate());
                lastSyncedAt = latestTime(lastSyncedAt, record.getUpdatedAt());
            }
            if (record.getFundingAccountId() != null && accountIds.contains(record.getFundingAccountId())) {
                humanRelationFundingRecordsByAccountId.computeIfAbsent(record.getFundingAccountId(), key -> new ArrayList<>()).add(record);
                earliestActivityDate = earliestDate(earliestActivityDate, record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate());
                lastSyncedAt = latestTime(lastSyncedAt, record.getUpdatedAt());
            }
        }

        return new TrendContext(
            transactionsByAccountId,
            positionsByAccountId,
            investmentTransactionsByPositionId,
            investmentFundingTransactionsByAccountId,
            priceHistoryByProductId,
            debtRecordsByAccountId,
            debtFundingRecordsByAccountId,
            liabilityRecordsByAccountId,
            humanRelationRecordsByAccountId,
            humanRelationFundingRecordsByAccountId,
            earliestActivityDate,
            lastSyncedAt
        );
    }

    private TrendRangeMeta resolveTrendRange(String range, List<TrendAccount> accounts, TrendContext context) {
        String normalizedRange = switch (range == null ? "" : range.trim().toLowerCase(Locale.ROOT)) {
            case "7d" -> "7d";
            case "30d" -> "30d";
            case "all" -> "all";
            default -> "ytd";
        };
        LocalDate endDate = LocalDate.now();
        LocalDate earliestAccountDate = accounts.stream()
            .map(TrendAccount::createdDate)
            .filter(item -> item != null)
            .min(LocalDate::compareTo)
            .orElse(endDate);
        LocalDate earliestDate = earliestDate(earliestAccountDate, context.earliestActivityDate());
        LocalDate startDate;
        boolean monthlyBuckets;
        boolean yearlyBuckets;
        String rangeLabel;
        switch (normalizedRange) {
            case "7d" -> {
                startDate = endDate.minusDays(6);
                monthlyBuckets = false;
                yearlyBuckets = false;
                rangeLabel = "近7日";
            }
            case "30d" -> {
                startDate = endDate.minusDays(29);
                monthlyBuckets = false;
                yearlyBuckets = false;
                rangeLabel = "近30日";
            }
            case "all" -> {
                startDate = earliestDate == null ? endDate : earliestDate;
                monthlyBuckets = false;
                yearlyBuckets = true;
                rangeLabel = "全部";
            }
            default -> {
                startDate = endDate.withDayOfYear(1);
                monthlyBuckets = true;
                yearlyBuckets = false;
                rangeLabel = "年内";
            }
        }
        if (startDate.isAfter(endDate)) {
            startDate = endDate;
        }
        return new TrendRangeMeta(normalizedRange, rangeLabel, startDate, endDate, monthlyBuckets, yearlyBuckets);
    }

    private List<LocalDate> buildTrendBucketDates(TrendRangeMeta rangeMeta) {
        if (!rangeMeta.monthlyBuckets() && !rangeMeta.yearlyBuckets()) {
            List<LocalDate> dates = new ArrayList<>();
            LocalDate cursor = rangeMeta.startDate();
            while (!cursor.isAfter(rangeMeta.endDate())) {
                dates.add(cursor);
                cursor = cursor.plusDays(1);
            }
            return dates;
        }

        if (rangeMeta.yearlyBuckets()) {
            List<LocalDate> dates = new ArrayList<>();
            LocalDate yearCursor = rangeMeta.startDate().withDayOfYear(1);
            LocalDate endYear = rangeMeta.endDate().withDayOfYear(1);
            while (!yearCursor.isAfter(endYear)) {
                LocalDate bucketDate = yearCursor.equals(endYear)
                    ? rangeMeta.endDate()
                    : yearCursor.with(TemporalAdjusters.lastDayOfYear());
                if (bucketDate.isBefore(rangeMeta.startDate())) {
                    bucketDate = rangeMeta.startDate();
                }
                dates.add(bucketDate);
                yearCursor = yearCursor.plusYears(1);
            }
            return dates;
        }

        List<LocalDate> dates = new ArrayList<>();
        LocalDate monthCursor = rangeMeta.startDate().withDayOfMonth(1);
        LocalDate endMonth = rangeMeta.endDate().withDayOfMonth(1);
        while (!monthCursor.isAfter(endMonth)) {
            LocalDate bucketDate = monthCursor.equals(endMonth)
                ? rangeMeta.endDate()
                : monthCursor.with(TemporalAdjusters.lastDayOfMonth());
            if (bucketDate.isBefore(rangeMeta.startDate())) {
                bucketDate = rangeMeta.startDate();
            }
            dates.add(bucketDate);
            monthCursor = monthCursor.plusMonths(1);
        }
        return dates;
    }

    private List<AssetTrendPointResponse> buildTrendPoints(
        Long userId,
        Long accountId,
        List<TrendAccount> accounts,
        TrendContext context,
        TrendRangeMeta rangeMeta,
        List<LocalDate> bucketDates
    ) {
        Map<LocalDate, BigDecimal> snapshotTotals = assetSnapshotService.getTotalAssetSnapshots(
            userId,
            accountId,
            rangeMeta.startDate(),
            rangeMeta.endDate()
        );
        List<AssetTrendPointResponse> points = new ArrayList<>();
        for (LocalDate bucketDate : bucketDates) {
            AssetTrendPointResponse point = new AssetTrendPointResponse();
            point.setKey(bucketDate.toString());
            point.setLabel(buildTrendPointLabel(bucketDate, rangeMeta));
            point.setValue(resolveTrendPointValue(userId, accountId, accounts, context, snapshotTotals, bucketDate));
            points.add(point);
        }
        if (points.isEmpty()) {
            AssetTrendPointResponse point = new AssetTrendPointResponse();
            point.setKey(rangeMeta.endDate().toString());
            point.setLabel(buildTrendPointLabel(rangeMeta.endDate(), rangeMeta));
            point.setValue(resolveTrendPointValue(userId, accountId, accounts, context, snapshotTotals, rangeMeta.endDate()));
            points.add(point);
        }
        return points;
    }

    private BigDecimal resolveTrendPointValue(
        Long userId,
        Long accountId,
        List<TrendAccount> accounts,
        TrendContext context,
        Map<LocalDate, BigDecimal> snapshotTotals,
        LocalDate bucketDate
    ) {
        if (bucketDate != null && bucketDate.isBefore(LocalDate.now())) {
            BigDecimal snapshotValue = snapshotTotals.get(bucketDate);
            if (snapshotValue != null) {
                return snapshotValue.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal fallbackValue = sumAssetsAtDate(accounts, context, bucketDate);
            assetSnapshotService.saveSnapshot(userId, accountId, bucketDate, fallbackValue);
            return fallbackValue;
        }
        return sumAssetsAtDate(accounts, context, bucketDate);
    }

    private ProfitSummary buildCumulativeSummary(
        List<TrendAccount> accounts,
        TrendContext context,
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (accounts.isEmpty() || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return new ProfitSummary(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null
            );
        }

        BigDecimal firstPositiveValue = null;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            BigDecimal value = sumAssetsAtDate(accounts, context, cursor);
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                firstPositiveValue = value.setScale(2, RoundingMode.HALF_UP);
                break;
            }
            cursor = cursor.plusDays(1);
        }

        if (firstPositiveValue == null) {
            return new ProfitSummary(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null
            );
        }

        BigDecimal latestValue = sumAssetsAtDate(accounts, context, endDate);
        BigDecimal cumulativeProfit = latestValue.subtract(firstPositiveValue).setScale(2, RoundingMode.HALF_UP);
        return new ProfitSummary(
            cumulativeProfit,
            firstPositiveValue.compareTo(BigDecimal.ZERO) > 0 ? rate(cumulativeProfit, firstPositiveValue) : null
        );
    }

    private String buildTrendPointLabel(LocalDate bucketDate, TrendRangeMeta rangeMeta) {
        if (!rangeMeta.monthlyBuckets() && !rangeMeta.yearlyBuckets()) {
            return bucketDate.getMonthValue() + "/" + bucketDate.getDayOfMonth();
        }
        if (rangeMeta.yearlyBuckets()) {
            return bucketDate.getYear() + "年";
        }
        return bucketDate.getMonthValue() + "月";
    }

    private BigDecimal sumCurrentAssets(List<TrendAccount> accounts) {
        return accounts.stream()
            .map(TrendAccount::currentBalance)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumAssetsAtDate(List<TrendAccount> accounts, TrendContext context, LocalDate targetDate) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (TrendAccount account : accounts) {
            total = total.add(resolveAccountBalanceAtDate(account, context, targetDate)).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private BigDecimal resolveAccountBalanceAtDate(TrendAccount account, TrendContext context, LocalDate targetDate) {
        if (account.createdDate() != null && account.createdDate().isAfter(targetDate)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // Gold accounts use realtime pricing in the account list. Align today's trend point
        // with that same balance source so the latest chart point matches the summary total.
        if ("gold".equals(account.accountTypeCode()) && LocalDate.now().equals(targetDate)) {
            return normalizeAccountBalance(account.accountTypeCode(), account.balanceDirection(), account.currentBalance());
        }
        if (POSITION_ACCOUNT_TYPE_CODES.contains(account.accountTypeCode())) {
            return normalizeAccountBalance(account.accountTypeCode(), account.balanceDirection(), resolvePositionAccountBalanceAtDate(account.id(), context, targetDate));
        }
        if (DEBT_ACCOUNT_TYPE_CODES.contains(account.accountTypeCode())) {
            return resolveDebtAccountBalanceAtDate(account.id(), context, targetDate);
        }
        if (LIABILITY_ACCOUNT_TYPE_CODE.equals(account.accountTypeCode())) {
            return normalizeAccountBalance(account.accountTypeCode(), account.balanceDirection(), resolveLiabilityBalanceAtDate(account.id(), context, targetDate));
        }
        if ("human_relation".equals(account.accountTypeCode())) {
            return resolveHumanRelationBalanceAtDate(account.id(), context, targetDate);
        }
        return normalizeAccountBalance(account.accountTypeCode(), account.balanceDirection(), resolveLedgerAccountBalanceAtDate(account, context, targetDate));
    }

    private BigDecimal resolveLedgerAccountBalanceAtDate(TrendAccount account, TrendContext context, LocalDate targetDate) {
        BigDecimal balance = defaultZero(account.currentBalance()).setScale(2, RoundingMode.HALF_UP);

        for (TransactionEntity transaction : context.transactionsByAccountId().getOrDefault(account.id(), List.of())) {
            LocalDate eventDate = transaction.getOccurredAt() == null ? null : transaction.getOccurredAt().toLocalDate();
            if (eventDate != null && eventDate.isAfter(targetDate)) {
                balance = balance.subtract(resolveTransactionDeltaForAccount(transaction, account.id())).setScale(2, RoundingMode.HALF_UP);
            }
        }
        for (InvestmentTransactionEntity transaction : context.investmentFundingTransactionsByAccountId().getOrDefault(account.id(), List.of())) {
            LocalDate eventDate = resolveInvestmentFundingEffectiveDate(transaction);
            if (eventDate != null && eventDate.isAfter(targetDate)) {
                balance = balance.subtract(resolveInvestmentFundingDelta(transaction, account.id())).setScale(2, RoundingMode.HALF_UP);
            }
        }
        for (DebtRecordEntity record : context.debtFundingRecordsByAccountId().getOrDefault(account.id(), List.of())) {
            LocalDate eventDate = record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate();
            if (eventDate != null && eventDate.isAfter(targetDate)) {
                balance = balance.subtract(resolveDebtFundingDelta(record, account.id())).setScale(2, RoundingMode.HALF_UP);
            }
        }
        for (HumanRelationRecordEntity record : context.humanRelationFundingRecordsByAccountId().getOrDefault(account.id(), List.of())) {
            LocalDate eventDate = record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate();
            if (eventDate != null && eventDate.isAfter(targetDate)) {
                balance = balance.subtract(resolveHumanRelationFundingDelta(record, account.id())).setScale(2, RoundingMode.HALF_UP);
            }
        }

        return balance.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePositionAccountBalanceAtDate(Long accountId, TrendContext context, LocalDate targetDate) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (InvestmentPositionEntity position : context.positionsByAccountId().getOrDefault(accountId, List.of())) {
            BigDecimal quantity = resolvePositionQuantityAtDate(
                position,
                targetDate,
                context.investmentTransactionsByPositionId().get(position.getId())
            );
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal price = resolvePositionPriceAtDate(
                position,
                targetDate,
                context.priceHistoryByProductId().get(position.getProductId())
            );
            total = total.add(quantity.multiply(price).setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private BigDecimal resolveDebtAccountBalanceAtDate(Long accountId, TrendContext context, LocalDate targetDate) {
        BigDecimal payable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal receivable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (DebtRecordEntity record : context.debtRecordsByAccountId().getOrDefault(accountId, List.of())) {
            LocalDate occurredDate = record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate();
            if (occurredDate == null || occurredDate.isAfter(targetDate)) {
                continue;
            }
            BigDecimal delta = resolveDebtBalanceDelta(record);
            if (delta.compareTo(BigDecimal.ZERO) < 0) {
                payable = payable.add(delta.abs()).setScale(2, RoundingMode.HALF_UP);
            } else {
                receivable = receivable.add(delta).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return receivable.subtract(payable).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveLiabilityBalanceAtDate(Long accountId, TrendContext context, LocalDate targetDate) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (LiabilityRecordEntity record : context.liabilityRecordsByAccountId().getOrDefault(accountId, List.of())) {
            LocalDate occurredDate = record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate();
            if (occurredDate == null || occurredDate.isAfter(targetDate)) {
                continue;
            }
            String repaymentStatus = record.getRepaymentStatus();
            if (LIABILITY_REPAYMENT_STATUS_PAID.equals(repaymentStatus)) {
                LocalDate paidDate = record.getPaidAt() == null ? null : record.getPaidAt().toLocalDate();
                if (paidDate == null || !paidDate.isAfter(targetDate)) {
                    continue;
                }
            } else if (StringUtils.hasText(repaymentStatus) && !LIABILITY_REPAYMENT_STATUS_PENDING.equals(repaymentStatus)) {
                continue;
            }
            total = total.add(defaultZero(record.getAmount())).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private BigDecimal resolveHumanRelationBalanceAtDate(Long accountId, TrendContext context, LocalDate targetDate) {
        BigDecimal outgoing = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal incoming = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (HumanRelationRecordEntity record : context.humanRelationRecordsByAccountId().getOrDefault(accountId, List.of())) {
            LocalDate occurredDate = record.getOccurredAt() == null ? null : record.getOccurredAt().toLocalDate();
            if (occurredDate == null || occurredDate.isAfter(targetDate)) {
                continue;
            }
            if (HUMAN_RELATION_DIRECTION_OUTGOING.equals(record.getDirection())) {
                outgoing = outgoing.add(defaultZero(record.getAmount())).setScale(2, RoundingMode.HALF_UP);
            } else {
                incoming = incoming.add(defaultZero(record.getAmount())).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return outgoing.subtract(incoming).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeAccountBalance(String accountTypeCode, String balanceDirection, BigDecimal balance) {
        BigDecimal normalized = defaultZero(balance).setScale(2, RoundingMode.HALF_UP);
        if (DEBT_ACCOUNT_TYPE_CODES.contains(accountTypeCode) || "human_relation".equals(accountTypeCode)) {
            return normalized;
        }
        if ("credit".equals(balanceDirection)) {
            return normalized.negate().setScale(2, RoundingMode.HALF_UP);
        }
        return normalized;
    }

    private List<AssetTrendAllocationResponse> buildAllocations(List<TrendAccount> accounts) {
        List<TrendAccount> allocationAccounts = accounts.stream()
            .filter(item -> item.currentBalance().compareTo(BigDecimal.ZERO) > 0)
            .filter(item -> !LIABILITY_ACCOUNT_CATEGORIES.contains(item.accountCategory()))
            .toList();
        if (allocationAccounts.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal totalBalance = allocationAccounts.stream()
            .map(TrendAccount::currentBalance)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        if (totalBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }

        Map<String, BigDecimal> balanceByType = new LinkedHashMap<>();
        Map<String, String> labelByType = new HashMap<>();
        for (TrendAccount account : allocationAccounts) {
            String accountTypeCode = StringUtils.hasText(account.accountTypeCode()) ? account.accountTypeCode() : "other";
            balanceByType.merge(accountTypeCode, account.currentBalance(), BigDecimal::add);
            labelByType.putIfAbsent(accountTypeCode, StringUtils.hasText(account.accountTypeName()) ? account.accountTypeName() : "其他");
        }

        return balanceByType.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .map(entry -> {
                AssetTrendAllocationResponse item = new AssetTrendAllocationResponse();
                item.setAccountTypeCode(entry.getKey());
                item.setLabel(labelByType.getOrDefault(entry.getKey(), "其他"));
                item.setBalance(entry.getValue().setScale(2, RoundingMode.HALF_UP));
                item.setPercent(rate(entry.getValue(), totalBalance));
                return item;
            })
            .toList();
    }

    private List<AssetTrendContributorResponse> buildContributors(
        List<TrendAccount> accounts,
        TrendContext context,
        TrendRangeMeta rangeMeta
    ) {
        List<AssetTrendContributorResponse> contributors = new ArrayList<>();
        for (TrendAccount account : accounts) {
            BigDecimal startValue = resolveAccountBalanceAtDate(account, context, rangeMeta.startDate());
            BigDecimal endValue = resolveAccountBalanceAtDate(account, context, rangeMeta.endDate());
            BigDecimal contributionAmount = endValue.subtract(startValue).setScale(2, RoundingMode.HALF_UP);
            if (contributionAmount.compareTo(BigDecimal.ZERO) == 0
                && startValue.compareTo(BigDecimal.ZERO) == 0
                && endValue.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal base = startValue.abs().compareTo(BigDecimal.ZERO) > 0 ? startValue.abs() : endValue.abs();
            AssetTrendContributorResponse item = new AssetTrendContributorResponse();
            item.setAccountId(account.id());
            item.setAccountName(account.name());
            item.setAccountTypeCode(account.accountTypeCode());
            item.setAccountTypeLabel(StringUtils.hasText(account.accountTypeName()) ? account.accountTypeName() : "其他");
            item.setContributionAmount(contributionAmount);
            item.setContributionRate(rate(contributionAmount, base));
            contributors.add(item);
        }

        return contributors.stream()
            .sorted(Comparator.comparing(
                (AssetTrendContributorResponse item) -> defaultZero(item.getContributionAmount()).abs()
            ).reversed())
            .limit(3)
            .toList();
    }

    private BigDecimal resolvePositionQuantityAtDate(
        InvestmentPositionEntity position,
        LocalDate targetDate,
        List<InvestmentTransactionEntity> transactions
    ) {
        if (position.getCreatedAt() != null && position.getCreatedAt().toLocalDate().isAfter(targetDate)) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        if (isPendingFundSubscription(position)
            && (position.getSubscriptionConfirmedAt() == null || position.getSubscriptionConfirmedAt().toLocalDate().isAfter(targetDate))) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }

        BigDecimal quantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        for (InvestmentTransactionEntity transaction : Optional.ofNullable(transactions).orElse(List.of())) {
            LocalDate effectiveDate = resolvePositionEffectiveDate(transaction);
            if (effectiveDate != null && effectiveDate.isAfter(targetDate)) {
                quantity = quantity.subtract(resolveSignedTransactionQuantity(transaction)).setScale(6, RoundingMode.HALF_UP);
            }
        }
        return quantity.compareTo(BigDecimal.ZERO) > 0
            ? quantity
            : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePositionPriceAtDate(
        InvestmentPositionEntity position,
        LocalDate targetDate,
        NavigableMap<LocalDate, BigDecimal> priceHistory
    ) {
        if (priceHistory != null && !priceHistory.isEmpty()) {
            Map.Entry<LocalDate, BigDecimal> floorEntry = priceHistory.floorEntry(targetDate);
            if (floorEntry != null && floorEntry.getValue() != null && floorEntry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                return floorEntry.getValue().setScale(6, RoundingMode.HALF_UP);
            }
            Map.Entry<LocalDate, BigDecimal> firstEntry = priceHistory.firstEntry();
            if (firstEntry != null && firstEntry.getValue() != null && firstEntry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                return firstEntry.getValue().setScale(6, RoundingMode.HALF_UP);
            }
        }

        if (position.getCurrentPrice() != null && position.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
            return position.getCurrentPrice().setScale(6, RoundingMode.HALF_UP);
        }
        return defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
    }

    private LocalDate resolvePositionEffectiveDate(InvestmentTransactionEntity transaction) {
        if (transaction == null || VOIDED_STATUS.equals(transaction.getStatus())) {
            return null;
        }
        if (SETTLEMENT_STATUS_PENDING.equals(transaction.getSettlementStatus()) && transaction.getSettlementConfirmedAt() == null) {
            return null;
        }
        if (transaction.getSettlementConfirmedAt() != null) {
            return transaction.getSettlementConfirmedAt().toLocalDate();
        }
        return transaction.getTradeAt() == null ? null : transaction.getTradeAt().toLocalDate();
    }

    private LocalDate resolveInvestmentFundingEffectiveDate(InvestmentTransactionEntity transaction) {
        if (transaction == null || transaction.getFundingAccountId() == null || VOIDED_STATUS.equals(transaction.getStatus())) {
            return null;
        }
        if (isPositiveTradeType(transaction.getTradeType())) {
            return transaction.getTradeAt() == null ? null : transaction.getTradeAt().toLocalDate();
        }
        if (isNegativeTradeType(transaction.getTradeType())) {
            if (SETTLEMENT_STATUS_PENDING.equals(transaction.getSettlementStatus()) && transaction.getSettlementConfirmedAt() == null) {
                return null;
            }
            if (transaction.getSettlementConfirmedAt() != null) {
                return transaction.getSettlementConfirmedAt().toLocalDate();
            }
            return transaction.getTradeAt() == null ? null : transaction.getTradeAt().toLocalDate();
        }
        return null;
    }

    private BigDecimal resolveSignedTransactionQuantity(InvestmentTransactionEntity transaction) {
        BigDecimal quantity = defaultZero(transaction.getQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (isPositiveTradeType(transaction.getTradeType())) {
            return quantity;
        }
        if (isNegativeTradeType(transaction.getTradeType())) {
            return quantity.negate().setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    }

    private boolean isPositiveTradeType(String tradeType) {
        return "buy".equals(tradeType)
            || "add".equals(tradeType)
            || "dividend_reinvest".equals(tradeType)
            || "split_adjust".equals(tradeType);
    }

    private boolean isNegativeTradeType(String tradeType) {
        return "sell".equals(tradeType) || "reduce".equals(tradeType);
    }

    private boolean isPendingFundSubscription(InvestmentPositionEntity position) {
        return position != null
            && "pending".equals(position.getSubscriptionStatus())
            && position.getSubscriptionConfirmedAt() == null;
    }

    private Map<Long, NavigableMap<LocalDate, BigDecimal>> loadPriceHistoryByProduct(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, NavigableMap<LocalDate, BigDecimal>> history = new HashMap<>();
        Map<Long, InvestmentProductEntity> products = investmentProductMapper.selectByIds(productIds).stream()
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        for (InvestmentPriceQuoteEntity quote : investmentPriceQuoteMapper.selectList(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .in(InvestmentPriceQuoteEntity::getProductId, productIds)
            .orderByAsc(InvestmentPriceQuoteEntity::getProductId)
            .orderByAsc(InvestmentPriceQuoteEntity::getQuoteDate)
            .orderByAsc(InvestmentPriceQuoteEntity::getQuoteTime)
            .orderByAsc(InvestmentPriceQuoteEntity::getId))) {
            LocalDate quoteDate = quote.getQuoteDate();
            BigDecimal price = resolveQuotePrice(quote);
            if (quoteDate == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            history.computeIfAbsent(quote.getProductId(), key -> new TreeMap<>())
                .put(quoteDate, price.setScale(6, RoundingMode.HALF_UP));
        }
        for (Long productId : productIds) {
            InvestmentProductEntity product = products.get(productId);
            if (product == null || !"gold".equals(product.getProductType())) {
                continue;
            }
            history.computeIfAbsent(productId, key -> loadGoldPriceHistory());
        }
        return history;
    }

    private NavigableMap<LocalDate, BigDecimal> loadGoldPriceHistory() {
        NavigableMap<LocalDate, BigDecimal> history = new TreeMap<>();
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d");
            goldPriceService.getGoldPrice("7d").getChartPoints().forEach((point) -> {
                if (point == null || point.getPrice() == null || point.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }
                String label = point.getLabel();
                if (!StringUtils.hasText(label)) {
                    return;
                }
                String monthDayText = label.split(" ")[0].trim();
                MonthDay monthDay = MonthDay.parse(monthDayText, formatter.withLocale(Locale.CHINA));
                LocalDate date = monthDay.atYear(LocalDate.now().getYear());
                history.put(date, point.getPrice().setScale(6, RoundingMode.HALF_UP));
            });
        } catch (Exception ignored) {
            return history;
        }
        return history;
    }

    private BigDecimal resolveQuotePrice(InvestmentPriceQuoteEntity quote) {
        if (quote == null) {
            return null;
        }
        if (quote.getClosePrice() != null && quote.getClosePrice().compareTo(BigDecimal.ZERO) > 0) {
            return quote.getClosePrice();
        }
        if (quote.getLatestPrice() != null && quote.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            return quote.getLatestPrice();
        }
        if (quote.getOpenPrice() != null && quote.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
            return quote.getOpenPrice();
        }
        return null;
    }

    private BigDecimal resolveTransactionDeltaForAccount(TransactionEntity transaction, Long accountId) {
        BigDecimal amount = defaultZero(transaction.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (TRANSACTION_TYPE_INCOME.equals(transaction.getType()) && accountId.equals(transaction.getAccountId())) {
            return amount;
        }
        if (TRANSACTION_TYPE_EXPENSE.equals(transaction.getType()) && accountId.equals(transaction.getAccountId())) {
            return amount.negate().setScale(2, RoundingMode.HALF_UP);
        }
        if (TRANSACTION_TYPE_TRANSFER.equals(transaction.getType())) {
            if (accountId.equals(transaction.getFromAccountId())) {
                return amount.negate().setScale(2, RoundingMode.HALF_UP);
            }
            if (accountId.equals(transaction.getToAccountId())) {
                return amount;
            }
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveInvestmentFundingDelta(InvestmentTransactionEntity transaction, Long accountId) {
        if (!accountId.equals(transaction.getFundingAccountId())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal amount = defaultZero(transaction.getAmount())
            .add(defaultZero(transaction.getFeeAmount()))
            .add(defaultZero(transaction.getTaxAmount()))
            .setScale(2, RoundingMode.HALF_UP);
        if (isPositiveTradeType(transaction.getTradeType())) {
            return amount.negate().setScale(2, RoundingMode.HALF_UP);
        }
        if (isNegativeTradeType(transaction.getTradeType())) {
            return defaultZero(transaction.getAmount())
                .subtract(defaultZero(transaction.getFeeAmount()))
                .subtract(defaultZero(transaction.getTaxAmount()))
                .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDebtFundingDelta(DebtRecordEntity record, Long accountId) {
        if (!accountId.equals(record.getFundingAccountId())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal amount = defaultZero(record.getAmount()).setScale(2, RoundingMode.HALF_UP);
        boolean isRepayment = DEBT_RECORD_TYPE_REPAYMENT.equalsIgnoreCase(record.getRecordType());
        if (DEBT_DIRECTION_PAYABLE.equals(record.getDirection())) {
            return isRepayment ? amount.negate().setScale(2, RoundingMode.HALF_UP) : amount;
        }
        return isRepayment ? amount : amount.negate().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDebtBalanceDelta(DebtRecordEntity record) {
        BigDecimal amount = defaultZero(record.getAmount()).setScale(2, RoundingMode.HALF_UP);
        boolean isRepayment = DEBT_RECORD_TYPE_REPAYMENT.equalsIgnoreCase(record.getRecordType());
        if (DEBT_DIRECTION_PAYABLE.equals(record.getDirection())) {
            return isRepayment ? amount : amount.negate().setScale(2, RoundingMode.HALF_UP);
        }
        return isRepayment ? amount.negate().setScale(2, RoundingMode.HALF_UP) : amount;
    }

    private BigDecimal resolveHumanRelationFundingDelta(HumanRelationRecordEntity record, Long accountId) {
        if (!accountId.equals(record.getFundingAccountId())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal amount = defaultZero(record.getAmount()).setScale(2, RoundingMode.HALF_UP);
        return HUMAN_RELATION_DIRECTION_OUTGOING.equals(record.getDirection())
            ? amount.negate().setScale(2, RoundingMode.HALF_UP)
            : amount;
    }

    private LocalDate investmentTransactionStartDate(InvestmentTransactionEntity transaction) {
        LocalDate fundingDate = resolveInvestmentFundingEffectiveDate(transaction);
        LocalDate positionDate = resolvePositionEffectiveDate(transaction);
        LocalDate tradeDate = transaction.getTradeAt() == null ? null : transaction.getTradeAt().toLocalDate();
        return earliestDate(earliestDate(fundingDate, positionDate), tradeDate);
    }

    private LocalDate earliestDate(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private LocalDateTime latestTime(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return numerator
            .multiply(BigDecimal.valueOf(100))
            .divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
