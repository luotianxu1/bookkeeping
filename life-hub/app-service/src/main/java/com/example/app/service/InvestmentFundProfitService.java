package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.dto.FundProfitCalendarCellResponse;
import com.example.app.dto.FundProfitContributionResponse;
import com.example.app.dto.FundProfitDetailResponse;
import com.example.app.dto.FundProfitForecastAccountResponse;
import com.example.app.dto.FundProfitForecastHoldingResponse;
import com.example.app.dto.FundProfitForecastResponse;
import com.example.app.dto.FundProfitPageAccountResponse;
import com.example.app.dto.FundProfitPageResponse;
import com.example.app.dto.FundProfitPageSummaryMetricResponse;
import com.example.app.dto.FundProfitPageSummaryResponse;
import com.example.app.dto.FundProfitSelectionResponse;
import com.example.app.dto.FundProfitTrendPointResponse;
import com.example.app.entity.AccountEntity;
import com.example.app.entity.AccountTypeEntity;
import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentProductEntity;
import com.example.app.entity.InvestmentTransactionEntity;
import com.example.app.mapper.AccountMapper;
import com.example.app.mapper.AccountTypeMapper;
import com.example.app.mapper.InvestmentPositionMapper;
import com.example.app.mapper.InvestmentProductMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.app.service.InvestmentCalcUtils.*;

/**
 * 基金收益域服务：基金收益预测（/fund-profit-forecast）与基金收益页（/fund-profit）的查询与聚合。
 */
@Service
public class InvestmentFundProfitService {

    private record FundProfitPageContext(
        List<AccountEntity> accounts,
        Map<Long, AccountEntity> accountMap,
        List<InvestmentPositionEntity> positions,
        Map<Long, InvestmentProductEntity> products,
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId,
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId,
        LocalDate earliestDate,
        LocalDate latestDate,
        LocalDateTime lastSyncedAt
    ) {
    }

    private record FundProfitPeriodMeta(
        String view,
        String key,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate comparisonDate
    ) {
    }

    private record PositionPeriodProfit(
        BigDecimal startValue,
        BigDecimal endValue,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal profit,
        BigDecimal profitRate,
        BigDecimal holdingAmount,
        BigDecimal holdingQuantity,
        BigDecimal endPrice
    ) {
    }

    private record FundProfitAggregate(
        BigDecimal startValue,
        BigDecimal endValue,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal profit,
        BigDecimal profitRate,
        int positiveCount,
        int negativeCount
    ) {
    }

    private record FundProfitForecastMetrics(
        BigDecimal holdingAmount,
        BigDecimal estimateProfit,
        BigDecimal estimateProfitRate,
        BigDecimal totalProfit,
        BigDecimal totalProfitRate,
        Integer fundCount,
        LocalDateTime estimatedAt
    ) {
    }

    private static final String ACTIVE_STATUS = "active";
    private static final String INVESTMENT_ACCOUNT_TYPE_CODE = "investment";
    private static final String FUND_PRODUCT_TYPE = "fund";
    private static final LocalTime FUND_PROFIT_SYNC_READY_TIME = LocalTime.of(21, 30);
    private static final DateTimeFormatter FUND_ESTIMATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FUND_ESTIMATE_TIME_WITH_SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InvestmentPositionMapper positionMapper;
    private final InvestmentProductMapper productMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final ObjectMapper objectMapper;
    private final MarketCalendarService marketCalendarService;
    private final InvestmentQuoteSyncService quoteSyncService;
    private final InvestmentPriceHistoryService priceHistoryService;

    public InvestmentFundProfitService(
        InvestmentPositionMapper positionMapper,
        InvestmentProductMapper productMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        ObjectMapper objectMapper,
        MarketCalendarService marketCalendarService,
        InvestmentQuoteSyncService quoteSyncService,
        InvestmentPriceHistoryService priceHistoryService
    ) {
        this.positionMapper = positionMapper;
        this.productMapper = productMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.objectMapper = objectMapper;
        this.marketCalendarService = marketCalendarService;
        this.quoteSyncService = quoteSyncService;
        this.priceHistoryService = priceHistoryService;
    }

    public FundProfitForecastResponse fundProfitForecast(Long userId, Long accountId) {
        List<AccountEntity> investmentAccounts = listActiveAccountsByTypeCode(userId, accountId, INVESTMENT_ACCOUNT_TYPE_CODE);
        Map<Long, AccountEntity> accountMap = investmentAccounts.stream()
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Set<Long> accountIds = accountMap.keySet();

        List<InvestmentPositionEntity> positions = accountIds.isEmpty()
            ? Collections.emptyList()
            : positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(InvestmentPositionEntity::getUserId, userId)
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS)
                .in(InvestmentPositionEntity::getAccountId, accountIds));

        Map<Long, InvestmentProductEntity> products = positions.isEmpty()
            ? Collections.emptyMap()
            : productMapper.selectByIds(positions.stream()
                .map(InvestmentPositionEntity::getProductId)
                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));

        Map<String, JsonNode> estimateInfoBySymbol = positions.stream()
            .map(position -> products.get(position.getProductId()))
            .filter(product -> product != null
                && FUND_PRODUCT_TYPE.equals(product.getProductType())
                && StringUtils.hasText(product.getSymbol()))
            .map(InvestmentProductEntity::getSymbol)
            .distinct()
            .parallel()
            .collect(Collectors.toConcurrentMap(Function.identity(), this::fetchFundEstimateInfo));
        List<FundProfitForecastHoldingResponse> holdings = positions.stream()
            .filter(position -> {
                InvestmentProductEntity product = products.get(position.getProductId());
                return product != null
                    && FUND_PRODUCT_TYPE.equals(product.getProductType())
                    && StringUtils.hasText(product.getSymbol());
            })
            .sorted(Comparator.comparing(InvestmentPositionEntity::getMarketValue, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
            .map(position -> buildFundProfitForecastHolding(
                position,
                accountMap.get(position.getAccountId()),
                products.get(position.getProductId()),
                estimateInfoBySymbol
            ))
            .toList();

        Map<Long, List<FundProfitForecastHoldingResponse>> holdingsByAccountId = holdings.stream()
            .collect(Collectors.groupingBy(FundProfitForecastHoldingResponse::getAccountId));

        List<FundProfitForecastAccountResponse> accounts = investmentAccounts.stream()
            .map(account -> buildFundProfitForecastAccount(account, holdingsByAccountId.getOrDefault(account.getId(), Collections.emptyList())))
            .toList();

        FundProfitForecastMetrics metrics = summarizeFundProfitForecast(holdings);
        FundProfitForecastResponse response = new FundProfitForecastResponse();
        response.setUserId(userId);
        response.setHoldingAmount(metrics.holdingAmount());
        response.setEstimateProfit(metrics.estimateProfit());
        response.setEstimateProfitRate(metrics.estimateProfitRate());
        response.setTotalProfit(metrics.totalProfit());
        response.setTotalProfitRate(metrics.totalProfitRate());
        response.setFundCount(metrics.fundCount());
        response.setEstimatedAt(metrics.estimatedAt());
        response.setAccounts(accounts);
        response.setHoldings(holdings);
        return response;
    }

    public FundProfitPageResponse fundProfitPage(Long userId, Long accountId, String view, String anchor, String selected) {
        String normalizedView = normalizeFundProfitView(view);
        FundProfitPageContext context = loadFundProfitPageContext(userId, accountId);
        FundProfitPeriodMeta selectedPeriod = resolveFundProfitSelectedPeriod(
            normalizedView,
            anchor,
            selected,
            context.earliestDate(),
            context.latestDate()
        );
        Map<Long, PositionPeriodProfit> positionProfitMap = buildPositionPeriodProfitMap(context, selectedPeriod);
        FundProfitAggregate aggregate = aggregateFundProfit(positionProfitMap.values());
        List<FundProfitContributionResponse> contributors = buildFundProfitContributors(context, positionProfitMap);
        List<FundProfitDetailResponse> details = buildFundProfitDetails(context, positionProfitMap);

        FundProfitPageResponse response = new FundProfitPageResponse();
        response.setUserId(userId);
        response.setAccountId(accountId);
        response.setView(normalizedView);
        response.setAnchor(resolveFundProfitAnchorValue(normalizedView, selectedPeriod));
        response.setSelectedKey(selectedPeriod.key());
        response.setLastSyncedAt(context.lastSyncedAt());
        response.setAccounts(buildFundProfitAccounts(context));
        response.setSummary(buildFundProfitSummary(context, normalizedView));
        response.setInsight(buildFundProfitInsight(selectedPeriod, aggregate, contributors, details.size()));
        response.setTrendPoints(buildFundProfitTrendPoints(context, selectedPeriod.endDate()));
        response.setCalendarItems(buildFundProfitCalendarItems(context, normalizedView, selectedPeriod));
        response.setSelection(buildFundProfitSelection(selectedPeriod, aggregate));
        response.setContributors(contributors);
        response.setDetails(details);
        return response;
    }

    private String normalizeFundProfitView(String view) {
        return switch (view == null ? "" : view.trim().toLowerCase(Locale.ROOT)) {
            case "month" -> "month";
            case "year" -> "year";
            default -> "day";
        };
    }

    private FundProfitPageContext loadFundProfitPageContext(Long userId, Long accountId) {
        List<AccountEntity> investmentAccounts = listActiveAccountsByTypeCode(userId, accountId, INVESTMENT_ACCOUNT_TYPE_CODE);
        Map<Long, AccountEntity> accountMap = investmentAccounts.stream()
            .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Set<Long> accountIds = accountMap.keySet();
        if (accountIds.isEmpty()) {
            LocalDate today = LocalDate.now();
            return new FundProfitPageContext(
                investmentAccounts,
                accountMap,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                today,
                today,
                null
            );
        }

        List<InvestmentPositionEntity> rawPositions = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getUserId, userId)
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS)
            .in(InvestmentPositionEntity::getAccountId, accountIds));
        if (rawPositions.isEmpty()) {
            LocalDate today = LocalDate.now();
            return new FundProfitPageContext(
                investmentAccounts,
                accountMap,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                today,
                today,
                null
            );
        }

        Map<Long, InvestmentProductEntity> allProducts = productMapper.selectByIds(rawPositions.stream()
                .map(InvestmentPositionEntity::getProductId)
                .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        List<InvestmentPositionEntity> positions = rawPositions.stream()
            .filter(position -> {
                InvestmentProductEntity product = allProducts.get(position.getProductId());
                return product != null && FUND_PRODUCT_TYPE.equals(product.getProductType());
            })
            .sorted(Comparator.comparing(InvestmentPositionEntity::getMarketValue, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
            .toList();
        Map<Long, InvestmentProductEntity> products = positions.stream()
            .map(position -> allProducts.get(position.getProductId()))
            .filter(item -> item != null)
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity(), (left, right) -> left));
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId = positions.isEmpty()
            ? Collections.emptyMap()
            : priceHistoryService.loadConfirmedTransactionsByPosition(userId, accountId, positions);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId = positions.isEmpty()
            ? Collections.emptyMap()
            : priceHistoryService.loadPriceHistoryByProduct(products.keySet());
        LocalDate latestDate = resolveFundProfitDisplayLatestDate(
            resolveFundProfitLatestDate(positions, priceHistoryByProductId)
        );
        LocalDate earliestDate = resolveFundProfitEarliestDate(positions, transactionsByPositionId, priceHistoryByProductId, latestDate);
        LocalDateTime lastSyncedAt = positions.stream()
            .map(InvestmentPositionEntity::getLastSyncedAt)
            .filter(item -> item != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);
        return new FundProfitPageContext(
            investmentAccounts,
            accountMap,
            positions,
            products,
            transactionsByPositionId,
            priceHistoryByProductId,
            earliestDate,
            latestDate,
            lastSyncedAt
        );
    }

    private LocalDate resolveFundProfitLatestDate(
        List<InvestmentPositionEntity> positions,
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId
    ) {
        LocalDate latestDate = positions.stream()
            .map(InvestmentPositionEntity::getLastSyncedAt)
            .filter(item -> item != null)
            .map(LocalDateTime::toLocalDate)
            .max(LocalDate::compareTo)
            .orElse(null);
        for (NavigableMap<LocalDate, BigDecimal> history : priceHistoryByProductId.values()) {
            if (history == null || history.isEmpty()) {
                continue;
            }
            LocalDate historyDate = history.lastKey();
            if (latestDate == null || historyDate.isAfter(latestDate)) {
                latestDate = historyDate;
            }
        }
        return latestDate == null ? LocalDate.now() : latestDate;
    }

    private LocalDate resolveFundProfitDisplayLatestDate(LocalDate latestDate) {
        LocalDate normalizedLatestDate = latestDate == null ? LocalDate.now() : latestDate;
        if (!normalizedLatestDate.equals(LocalDate.now())) {
            return normalizedLatestDate;
        }
        return LocalTime.now().isBefore(FUND_PROFIT_SYNC_READY_TIME)
            ? normalizedLatestDate.minusDays(1)
            : normalizedLatestDate;
    }

    private LocalDate resolveFundProfitEarliestDate(
        List<InvestmentPositionEntity> positions,
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId,
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId,
        LocalDate fallbackDate
    ) {
        LocalDate earliestDate = null;
        for (InvestmentPositionEntity position : positions) {
            earliestDate = minDate(earliestDate, position.getCreatedAt() == null ? null : position.getCreatedAt().toLocalDate());
            earliestDate = minDate(earliestDate, position.getSubscriptionConfirmedAt() == null ? null : position.getSubscriptionConfirmedAt().toLocalDate());
            for (InvestmentTransactionEntity transaction : transactionsByPositionId.getOrDefault(position.getId(), Collections.emptyList())) {
                earliestDate = minDate(earliestDate, priceHistoryService.resolveTransactionEffectiveDate(transaction));
            }
        }
        for (NavigableMap<LocalDate, BigDecimal> history : priceHistoryByProductId.values()) {
            if (history == null || history.isEmpty()) {
                continue;
            }
            earliestDate = minDate(earliestDate, history.firstKey());
        }
        return earliestDate == null ? fallbackDate : earliestDate;
    }

    private FundProfitPeriodMeta resolveFundProfitSelectedPeriod(
        String view,
        String anchor,
        String selected,
        LocalDate earliestDate,
        LocalDate latestDate
    ) {
        if ("month".equals(view)) {
            int anchorYear = clampYear(
                parseYearValue(anchor),
                earliestDate.getYear(),
                latestDate.getYear(),
                latestDate.getYear()
            );
            YearMonth defaultMonth = anchorYear == latestDate.getYear()
                ? YearMonth.from(latestDate)
                : YearMonth.of(anchorYear, 12);
            YearMonth selectedMonth = parseYearMonthValue(selected);
            if (selectedMonth == null || selectedMonth.getYear() != anchorYear) {
                selectedMonth = defaultMonth;
            }
            if (selectedMonth.isAfter(YearMonth.from(latestDate))) {
                selectedMonth = YearMonth.from(latestDate);
            }
            LocalDate startDate = selectedMonth.atDay(1);
            LocalDate endDate = selectedMonth.atEndOfMonth();
            if (endDate.isAfter(latestDate)) {
                endDate = latestDate;
            }
            return new FundProfitPeriodMeta(
                "month",
                selectedMonth.toString(),
                String.format("%02d月", selectedMonth.getMonthValue()),
                startDate,
                endDate,
                startDate.minusDays(1)
            );
        }

        if ("year".equals(view)) {
            int selectedYear = clampYear(
                parseYearValue(selected) != null ? parseYearValue(selected) : parseYearValue(anchor),
                earliestDate.getYear(),
                latestDate.getYear(),
                latestDate.getYear()
            );
            LocalDate startDate = LocalDate.of(selectedYear, 1, 1);
            LocalDate endDate = LocalDate.of(selectedYear, 12, 31);
            if (endDate.isAfter(latestDate)) {
                endDate = latestDate;
            }
            return new FundProfitPeriodMeta(
                "year",
                String.valueOf(selectedYear),
                selectedYear + "年",
                startDate,
                endDate,
                startDate.minusDays(1)
            );
        }

        YearMonth anchorMonth = clampYearMonth(
            parseYearMonthValue(anchor),
            YearMonth.from(earliestDate),
            YearMonth.from(latestDate),
            YearMonth.from(latestDate)
        );
        LocalDate selectedDate = parseDateValue(selected);
        if (selectedDate == null || !YearMonth.from(selectedDate).equals(anchorMonth)) {
            selectedDate = anchorMonth.equals(YearMonth.from(latestDate))
                ? latestDate
                : anchorMonth.atEndOfMonth();
        }
        LocalDate startDate = selectedDate;
        return new FundProfitPeriodMeta(
            "day",
            selectedDate.toString(),
            String.format("%02d/%02d", selectedDate.getMonthValue(), selectedDate.getDayOfMonth()),
            startDate,
            startDate,
            startDate.minusDays(1)
        );
    }

    private Map<Long, PositionPeriodProfit> buildPositionPeriodProfitMap(
        FundProfitPageContext context,
        FundProfitPeriodMeta period
    ) {
        Map<Long, PositionPeriodProfit> result = new LinkedHashMap<>();
        for (InvestmentPositionEntity position : context.positions()) {
            PositionPeriodProfit profit = calculatePositionPeriodProfit(
                position,
                period.startDate(),
                period.endDate(),
                period.comparisonDate(),
                context.transactionsByPositionId().getOrDefault(position.getId(), Collections.emptyList()),
                context.priceHistoryByProductId().get(position.getProductId())
            );
            result.put(position.getId(), profit);
        }
        return result;
    }

    private PositionPeriodProfit calculatePositionPeriodProfit(
        InvestmentPositionEntity position,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate comparisonDate,
        List<InvestmentTransactionEntity> transactions,
        NavigableMap<LocalDate, BigDecimal> priceHistory
    ) {
        BigDecimal startQuantity = priceHistoryService.resolvePositionQuantityAtDate(position, comparisonDate, transactions);
        BigDecimal endQuantity = priceHistoryService.resolvePositionQuantityAtDate(position, endDate, transactions);
        BigDecimal startPrice = priceHistoryService.resolvePositionPriceAtDate(position, comparisonDate, priceHistory);
        BigDecimal endPrice = priceHistoryService.resolvePositionPriceAtDate(position, endDate, priceHistory);
        BigDecimal startValue = startQuantity.multiply(startPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal endValue = endQuantity.multiply(endPrice).setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashIn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashOut = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (InvestmentTransactionEntity transaction : transactions) {
            LocalDate effectiveDate = priceHistoryService.resolveTransactionEffectiveDate(transaction);
            if (effectiveDate == null || effectiveDate.isBefore(startDate) || effectiveDate.isAfter(endDate)) {
                continue;
            }
            if (priceHistoryService.isPositiveTradeType(transaction.getTradeType())) {
                cashIn = cashIn.add(defaultZero(transaction.getAmount()))
                    .add(defaultZero(transaction.getFeeAmount()))
                    .add(defaultZero(transaction.getTaxAmount()))
                    .setScale(2, RoundingMode.HALF_UP);
            } else if (priceHistoryService.isNegativeTradeType(transaction.getTradeType())) {
                cashOut = cashOut.add(defaultZero(transaction.getAmount()))
                    .subtract(defaultZero(transaction.getFeeAmount()))
                    .subtract(defaultZero(transaction.getTaxAmount()))
                    .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal profit = endValue.subtract(startValue).subtract(cashIn).add(cashOut).setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = startValue.add(cashIn).setScale(2, RoundingMode.HALF_UP);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            base = endValue;
        }

        if (shouldUseStoredTodayProfit(position, startDate, endDate)) {
            return new PositionPeriodProfit(
                startValue,
                endValue,
                cashIn,
                cashOut,
                defaultZero(position.getDayProfit()).setScale(2, RoundingMode.HALF_UP),
                defaultZero(position.getDayProfitRate()).setScale(4, RoundingMode.HALF_UP),
                endValue,
                endQuantity,
                endPrice
            );
        }

        return new PositionPeriodProfit(
            startValue,
            endValue,
            cashIn,
            cashOut,
            profit,
            rate(profit, base),
            endValue,
            endQuantity,
            endPrice
        );
    }

    private boolean shouldUseStoredTodayProfit(
        InvestmentPositionEntity position,
        LocalDate startDate,
        LocalDate endDate
    ) {
        return position != null
            && startDate != null
            && endDate != null
            && startDate.equals(endDate)
            && LocalDate.now().equals(endDate)
            && hasTodayDayProfit(position);
    }

    private FundProfitAggregate aggregateFundProfit(Iterable<PositionPeriodProfit> profits) {
        BigDecimal startValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal endValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashIn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashOut = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int positiveCount = 0;
        int negativeCount = 0;
        for (PositionPeriodProfit item : profits) {
            if (item == null) {
                continue;
            }
            startValue = startValue.add(defaultZero(item.startValue())).setScale(2, RoundingMode.HALF_UP);
            endValue = endValue.add(defaultZero(item.endValue())).setScale(2, RoundingMode.HALF_UP);
            cashIn = cashIn.add(defaultZero(item.cashIn())).setScale(2, RoundingMode.HALF_UP);
            cashOut = cashOut.add(defaultZero(item.cashOut())).setScale(2, RoundingMode.HALF_UP);
            profit = profit.add(defaultZero(item.profit())).setScale(2, RoundingMode.HALF_UP);
            if (defaultZero(item.profit()).compareTo(BigDecimal.ZERO) > 0) {
                positiveCount++;
            } else if (defaultZero(item.profit()).compareTo(BigDecimal.ZERO) < 0) {
                negativeCount++;
            }
        }
        BigDecimal base = startValue.add(cashIn).setScale(2, RoundingMode.HALF_UP);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            base = endValue;
        }
        return new FundProfitAggregate(
            startValue,
            endValue,
            cashIn,
            cashOut,
            profit,
            rate(profit, base),
            positiveCount,
            negativeCount
        );
    }

    private List<FundProfitPageAccountResponse> buildFundProfitAccounts(FundProfitPageContext context) {
        Map<Long, List<InvestmentPositionEntity>> positionsByAccountId = context.positions().stream()
            .collect(Collectors.groupingBy(InvestmentPositionEntity::getAccountId));
        return context.accounts().stream()
            .map(account -> {
                List<InvestmentPositionEntity> positions = positionsByAccountId.getOrDefault(account.getId(), Collections.emptyList());
                FundProfitPageAccountResponse item = new FundProfitPageAccountResponse();
                item.setAccountId(account.getId());
                item.setAccountName(account.getName());
                item.setHoldingAmount(sum(positions, InvestmentPositionEntity::getMarketValue));
                item.setTotalProfit(sum(positions, InvestmentPositionEntity::getCumulativeProfit));
                item.setTotalProfitRate(rate(item.getTotalProfit(), sum(positions, InvestmentPositionEntity::getCostAmount)));
                item.setFundCount(positions.size());
                return item;
            })
            .toList();
    }

    private FundProfitPageSummaryResponse buildFundProfitSummary(FundProfitPageContext context, String view) {
        FundProfitPageSummaryResponse response = new FundProfitPageSummaryResponse();
        BigDecimal holdingAmount = sum(context.positions(), InvestmentPositionEntity::getMarketValue);
        BigDecimal investedAmount = sum(context.positions(), InvestmentPositionEntity::getCostAmount);
        BigDecimal totalProfit = sum(context.positions(), InvestmentPositionEntity::getCumulativeProfit);
        boolean todayProfitAvailable = hasTodayFundProfitData(context.positions(), context.latestDate());

        response.setHoldingAmount(holdingAmount);
        response.setInvestedAmount(investedAmount);
        response.setTotalProfit(totalProfit);
        response.setTotalProfitRate(rate(totalProfit, investedAmount));
        response.setFundCount(context.positions().size());
        response.setLastSyncedAt(context.lastSyncedAt());

        LocalDate latestDate = context.latestDate();
        List<FundProfitPageSummaryMetricResponse> shortcuts = new ArrayList<>();
        shortcuts.add(buildFundProfitShortcutMetric(
            context,
            "today",
            "今日",
            new FundProfitPeriodMeta(
            "day",
            latestDate.toString(),
            "今日",
            latestDate,
            latestDate,
            latestDate.minusDays(1)
            ),
            todayProfitAvailable
        ));
        shortcuts.add(buildFundProfitShortcutMetric(context, "7d", "近7日", new FundProfitPeriodMeta(
            "day",
            latestDate.minusDays(6).toString(),
            "近7日",
            latestDate.minusDays(6),
            latestDate,
            latestDate.minusDays(7)
        )));
        LocalDate monthStart = latestDate.withDayOfMonth(1);
        shortcuts.add(buildFundProfitShortcutMetric(context, "month", "本月", new FundProfitPeriodMeta(
            "month",
            YearMonth.from(latestDate).toString(),
            "本月",
            monthStart,
            latestDate,
            monthStart.minusDays(1)
        )));

        response.setActiveShortcut("day".equals(view) && todayProfitAvailable ? "today" : "month");
        response.setShortcuts(shortcuts);
        return response;
    }

    private FundProfitPageSummaryMetricResponse buildFundProfitShortcutMetric(
        FundProfitPageContext context,
        String key,
        String label,
        FundProfitPeriodMeta period
    ) {
        return buildFundProfitShortcutMetric(context, key, label, period, true);
    }

    private FundProfitPageSummaryMetricResponse buildFundProfitShortcutMetric(
        FundProfitPageContext context,
        String key,
        String label,
        FundProfitPeriodMeta period,
        boolean visible
    ) {
        FundProfitAggregate aggregate = aggregateFundProfit(buildPositionPeriodProfitMap(context, period).values());
        FundProfitPageSummaryMetricResponse item = new FundProfitPageSummaryMetricResponse();
        item.setKey(key);
        item.setLabel(label);
        item.setProfit(visible ? aggregate.profit() : null);
        item.setProfitRate(visible ? aggregate.profitRate() : null);
        return item;
    }

    private String buildFundProfitInsight(
        FundProfitPeriodMeta period,
        FundProfitAggregate aggregate,
        List<FundProfitContributionResponse> contributors,
        int detailCount
    ) {
        if (detailCount == 0) {
            return period.label() + "暂无基金收益明细";
        }
        if (contributors.isEmpty()) {
            return period.label() + "暂无明显收益贡献差异";
        }
        if (defaultZero(aggregate.profit()).compareTo(BigDecimal.ZERO) >= 0) {
            FundProfitContributionResponse top = contributors.get(0);
            return period.label() + "共有 " + aggregate.positiveCount() + "/" + detailCount + " 只基金录得正收益，"
                + top.getProductName() + " 贡献最多。";
        }
        FundProfitContributionResponse worst = contributors.stream()
            .min(Comparator.comparing(item -> defaultZero(item.getContributionAmount())))
            .orElse(contributors.get(contributors.size() - 1));
        return period.label() + "回撤占优，主要拖累来自 " + worst.getProductName() + "。";
    }

    private List<FundProfitTrendPointResponse> buildFundProfitTrendPoints(FundProfitPageContext context, LocalDate endDate) {
        LocalDate latestDate = context.latestDate();
        LocalDate normalizedEndDate = endDate.isAfter(latestDate) ? latestDate : endDate;
        List<FundProfitTrendPointResponse> items = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = normalizedEndDate.minusDays(offset);
            FundProfitPeriodMeta period = new FundProfitPeriodMeta(
                "day",
                date.toString(),
                date.toString(),
                date,
                date,
                date.minusDays(1)
            );
            FundProfitTrendPointResponse point = new FundProfitTrendPointResponse();
            point.setKey(date.toString());
            point.setDate(date);
            point.setLabel(date.getMonthValue() + "/" + date.getDayOfMonth());
            if (isNonTradingDay(date)) {
                point.setProfit(null);
            } else {
                FundProfitAggregate aggregate = aggregateFundProfit(buildPositionPeriodProfitMap(context, period).values());
                point.setProfit(aggregate.profit());
            }
            items.add(point);
        }
        return items;
    }

    private List<FundProfitCalendarCellResponse> buildFundProfitCalendarItems(
        FundProfitPageContext context,
        String view,
        FundProfitPeriodMeta selectedPeriod
    ) {
        if ("month".equals(view)) {
            int year = selectedPeriod.startDate().getYear();
            List<FundProfitCalendarCellResponse> items = new ArrayList<>();
            for (int month = 1; month <= 12; month++) {
                YearMonth yearMonth = YearMonth.of(year, month);
                LocalDate startDate = yearMonth.atDay(1);
                LocalDate endDate = yearMonth.atEndOfMonth();
                BigDecimal profit = null;
                BigDecimal profitRate = null;
                if (!startDate.isAfter(context.latestDate())) {
                    if (endDate.isAfter(context.latestDate())) {
                        endDate = context.latestDate();
                    }
                    FundProfitAggregate aggregate = aggregateFundProfit(buildPositionPeriodProfitMap(
                        context,
                        new FundProfitPeriodMeta("month", yearMonth.toString(), String.format("%02d月", month), startDate, endDate, startDate.minusDays(1))
                    ).values());
                    profit = aggregate.profit();
                    profitRate = aggregate.profitRate();
                }
                FundProfitCalendarCellResponse item = new FundProfitCalendarCellResponse();
                item.setKey(yearMonth.toString());
                item.setLabel(String.format("%02d月", month));
                item.setSecondaryLabel("收益");
                item.setStartDate(startDate);
                item.setEndDate(endDate);
                item.setProfit(profit);
                item.setProfitRate(profitRate);
                item.setSelected(yearMonth.toString().equals(selectedPeriod.key()));
                item.setCurrent(yearMonth.equals(YearMonth.from(context.latestDate())));
                items.add(item);
            }
            return items;
        }

        if ("year".equals(view)) {
            int selectedYear = selectedPeriod.startDate().getYear();
            int startYear = Math.max(context.earliestDate().getYear(), selectedYear - 5);
            List<FundProfitCalendarCellResponse> items = new ArrayList<>();
            for (int year = selectedYear; year >= startYear; year--) {
                LocalDate startDate = LocalDate.of(year, 1, 1);
                LocalDate endDate = LocalDate.of(year, 12, 31);
                if (endDate.isAfter(context.latestDate())) {
                    endDate = context.latestDate();
                }
                FundProfitAggregate aggregate = aggregateFundProfit(buildPositionPeriodProfitMap(
                    context,
                    new FundProfitPeriodMeta("year", String.valueOf(year), year + "年", startDate, endDate, startDate.minusDays(1))
                ).values());
                FundProfitCalendarCellResponse item = new FundProfitCalendarCellResponse();
                item.setKey(String.valueOf(year));
                item.setLabel(year + "年");
                item.setSecondaryLabel("年度收益");
                item.setStartDate(startDate);
                item.setEndDate(endDate);
                item.setProfit(aggregate.profit());
                item.setProfitRate(aggregate.profitRate());
                item.setSelected(String.valueOf(year).equals(selectedPeriod.key()));
                item.setCurrent(year == context.latestDate().getYear());
                items.add(item);
            }
            return items;
        }

        YearMonth month = YearMonth.from(selectedPeriod.startDate());
        int daysInMonth = month.lengthOfMonth();
        List<FundProfitCalendarCellResponse> items = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            BigDecimal profit = null;
            BigDecimal profitRate = null;
            if (!date.isAfter(context.latestDate()) && !isNonTradingDay(date)) {
                FundProfitAggregate aggregate = aggregateFundProfit(buildPositionPeriodProfitMap(
                    context,
                    new FundProfitPeriodMeta("day", date.toString(), date.toString(), date, date, date.minusDays(1))
                ).values());
                profit = aggregate.profit();
                profitRate = aggregate.profitRate();
            }
            FundProfitCalendarCellResponse item = new FundProfitCalendarCellResponse();
            item.setKey(date.toString());
            item.setLabel(String.valueOf(day));
            item.setSecondaryLabel(date.getDayOfWeek().name().substring(0, 3));
            item.setStartDate(date);
            item.setEndDate(date);
            item.setProfit(profit);
            item.setProfitRate(profitRate);
            item.setSelected(date.toString().equals(selectedPeriod.key()));
            item.setCurrent(date.equals(context.latestDate()));
            items.add(item);
        }
        return items;
    }

    private FundProfitSelectionResponse buildFundProfitSelection(FundProfitPeriodMeta period, FundProfitAggregate aggregate) {
        FundProfitSelectionResponse response = new FundProfitSelectionResponse();
        response.setKey(period.key());
        response.setLabel(period.label());
        response.setTitle(period.label() + "收益");
        response.setStartDate(period.startDate());
        response.setEndDate(period.endDate());
        response.setComparisonDate(period.comparisonDate());
        response.setProfit(aggregate.profit());
        response.setProfitRate(aggregate.profitRate());
        response.setPositiveFundCount(aggregate.positiveCount());
        response.setNegativeFundCount(aggregate.negativeCount());
        return response;
    }

    private List<FundProfitContributionResponse> buildFundProfitContributors(
        FundProfitPageContext context,
        Map<Long, PositionPeriodProfit> positionProfitMap
    ) {
        return context.positions().stream()
            .map(position -> {
                PositionPeriodProfit profit = positionProfitMap.get(position.getId());
                if (profit == null) {
                    return null;
                }
                InvestmentProductEntity product = context.products().get(position.getProductId());
                AccountEntity account = context.accountMap().get(position.getAccountId());
                FundProfitContributionResponse item = new FundProfitContributionResponse();
                item.setPositionId(position.getId());
                item.setProductId(position.getProductId());
                item.setProductName(product == null ? "未命名基金" : product.getName());
                item.setProductSymbol(product == null ? null : product.getSymbol());
                item.setAccountName(account == null ? null : account.getName());
                item.setContributionAmount(profit.profit());
                item.setContributionRate(profit.profitRate());
                item.setHoldingAmount(profit.holdingAmount());
                item.setHoldingQuantity(profit.holdingQuantity());
                return item;
            })
            .filter(item -> item != null)
            .sorted(Comparator.comparing(FundProfitContributionResponse::getContributionAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
            .limit(3)
            .toList();
    }

    private List<FundProfitDetailResponse> buildFundProfitDetails(
        FundProfitPageContext context,
        Map<Long, PositionPeriodProfit> positionProfitMap
    ) {
        return context.positions().stream()
            .map(position -> {
                PositionPeriodProfit profit = positionProfitMap.get(position.getId());
                if (profit == null) {
                    return null;
                }
                InvestmentProductEntity product = context.products().get(position.getProductId());
                AccountEntity account = context.accountMap().get(position.getAccountId());
                FundProfitDetailResponse item = new FundProfitDetailResponse();
                item.setPositionId(position.getId());
                item.setProductId(position.getProductId());
                item.setProductName(product == null ? "未命名基金" : product.getName());
                item.setProductSymbol(product == null ? null : product.getSymbol());
                item.setAccountName(account == null ? null : account.getName());
                item.setHoldingQuantity(profit.holdingQuantity());
                item.setNetValue(profit.endPrice());
                item.setHoldingAmount(profit.holdingAmount());
                item.setCostAmount(defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP));
                item.setPeriodProfit(profit.profit());
                item.setPeriodProfitRate(profit.profitRate());
                return item;
            })
            .filter(item -> item != null)
            .sorted(Comparator.comparing(FundProfitDetailResponse::getPeriodProfit, Comparator.nullsLast(BigDecimal::compareTo)).reversed()
                .thenComparing(FundProfitDetailResponse::getHoldingAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
            .toList();
    }

    private String resolveFundProfitAnchorValue(String view, FundProfitPeriodMeta period) {
        if ("month".equals(view) || "year".equals(view)) {
            return String.valueOf(period.startDate().getYear());
        }
        return YearMonth.from(period.startDate()).toString();
    }

    private boolean hasTodayDayProfit(InvestmentPositionEntity position) {
        return position != null
            && position.getLastSyncedAt() != null
            && LocalDate.now().equals(position.getLastSyncedAt().toLocalDate())
            && !position.getLastSyncedAt().toLocalTime().isBefore(FUND_PROFIT_SYNC_READY_TIME);
    }

    private boolean hasTodayFundProfitData(List<InvestmentPositionEntity> positions, LocalDate latestDate) {
        return latestDate != null
            && LocalDate.now().equals(latestDate)
            && !positions.isEmpty()
            && positions.stream().allMatch(this::hasTodayDayProfit);
    }

    private List<AccountEntity> listActiveAccountsByTypeCode(Long userId, Long accountId, String accountTypeCode) {
        List<AccountEntity> accounts = accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
            .eq(userId != null, AccountEntity::getUserId, userId)
            .eq(accountId != null, AccountEntity::getId, accountId)
            .eq(AccountEntity::getStatus, ACTIVE_STATUS)
            .orderByAsc(AccountEntity::getSortOrder)
            .orderByAsc(AccountEntity::getId));
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> accountTypeCodes = loadAccountTypeCodesByAccountId(accounts.stream()
            .map(AccountEntity::getId)
            .collect(Collectors.toSet()));

        return accounts.stream()
            .filter(account -> accountTypeCode.equals(accountTypeCodes.get(account.getId())))
            .toList();
    }

    private Map<Long, String> loadAccountTypeCodesByAccountId(Set<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<AccountEntity> accounts = accountMapper.selectByIds(accountIds);
        if (accounts.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> accountTypeIdsByAccountId = accounts.stream()
            .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getAccountTypeId));
        Map<Long, String> typeCodesByTypeId = accountTypeMapper.selectByIds(new HashSet<>(accountTypeIdsByAccountId.values()))
            .stream()
            .collect(Collectors.toMap(AccountTypeEntity::getId, AccountTypeEntity::getCode));

        return accountTypeIdsByAccountId.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> typeCodesByTypeId.get(entry.getValue())));
    }

    private boolean isNonTradingDay(LocalDate date) {
        // 市场日历统一判定：周末 + 节假日 API + yml 静态兜底（见 MarketCalendarService）
        return marketCalendarService.isClosed(date);
    }

    private FundProfitForecastHoldingResponse buildFundProfitForecastHolding(
        InvestmentPositionEntity position,
        AccountEntity account,
        InvestmentProductEntity product,
        Map<String, JsonNode> estimateInfoBySymbol
    ) {
        JsonNode estimateInfo = estimateInfoBySymbol.getOrDefault(product.getSymbol(), objectMapper.createObjectNode());
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal officialNetValue = resolveOfficialFundNetValue(position, estimateInfo);
        BigDecimal estimatedNetValue = resolveEstimatedFundNetValue(position, estimateInfo, officialNetValue);
        BigDecimal holdingAmount = resolveEstimatedHoldingAmount(position, holdingQuantity, estimatedNetValue);
        BigDecimal previousHoldingAmount = resolvePreviousHoldingAmount(position, holdingQuantity, officialNetValue, holdingAmount);
        BigDecimal estimateProfit = holdingAmount.subtract(previousHoldingAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalProfit = holdingAmount.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);

        FundProfitForecastHoldingResponse response = new FundProfitForecastHoldingResponse();
        response.setAccountId(position.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setPositionId(position.getId());
        response.setProductId(position.getProductId());
        response.setProductName(product.getName());
        response.setProductSymbol(product.getSymbol());
        response.setUnitName(product.getUnitName());
        response.setHoldingQuantity(holdingQuantity);
        response.setCostAmount(costAmount);
        response.setHoldingAmount(holdingAmount);
        response.setEstimateProfit(estimateProfit);
        response.setEstimateProfitRate(rate(estimateProfit, previousHoldingAmount));
        response.setTotalProfit(totalProfit);
        response.setTotalProfitRate(rate(totalProfit, costAmount));
        response.setEstimatedNetValue(estimatedNetValue);
        response.setOfficialNetValue(officialNetValue);
        response.setEstimatedAt(resolveFundEstimateTime(position, estimateInfo));
        return response;
    }

    private FundProfitForecastAccountResponse buildFundProfitForecastAccount(
        AccountEntity account,
        List<FundProfitForecastHoldingResponse> holdings
    ) {
        FundProfitForecastMetrics metrics = summarizeFundProfitForecast(holdings);
        FundProfitForecastAccountResponse response = new FundProfitForecastAccountResponse();
        response.setAccountId(account.getId());
        response.setAccountName(account.getName());
        response.setHoldingAmount(metrics.holdingAmount());
        response.setEstimateProfit(metrics.estimateProfit());
        response.setEstimateProfitRate(metrics.estimateProfitRate());
        response.setTotalProfit(metrics.totalProfit());
        response.setTotalProfitRate(metrics.totalProfitRate());
        response.setFundCount(metrics.fundCount());
        response.setEstimatedAt(metrics.estimatedAt());
        return response;
    }

    private FundProfitForecastMetrics summarizeFundProfitForecast(List<FundProfitForecastHoldingResponse> holdings) {
        BigDecimal holdingAmount = holdings.stream()
            .map(FundProfitForecastHoldingResponse::getHoldingAmount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimateProfit = holdings.stream()
            .map(FundProfitForecastHoldingResponse::getEstimateProfit)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalProfit = holdings.stream()
            .map(FundProfitForecastHoldingResponse::getTotalProfit)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousHoldingAmount = holdings.stream()
            .map(item -> defaultZero(item.getHoldingAmount()).subtract(defaultZero(item.getEstimateProfit())).setScale(2, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCostAmount = holdings.stream()
            .map(FundProfitForecastHoldingResponse::getCostAmount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        LocalDateTime estimatedAt = holdings.stream()
            .map(FundProfitForecastHoldingResponse::getEstimatedAt)
            .filter(item -> item != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        return new FundProfitForecastMetrics(
            holdingAmount,
            estimateProfit,
            rate(estimateProfit, previousHoldingAmount),
            totalProfit,
            rate(totalProfit, totalCostAmount),
            holdings.size(),
            estimatedAt
        );
    }

    private BigDecimal resolveOfficialFundNetValue(InvestmentPositionEntity position, JsonNode estimateInfo) {
        BigDecimal officialNetValue = safeDecimal(estimateInfo.path("dwjz").asText(null));
        if (officialNetValue != null && officialNetValue.compareTo(BigDecimal.ZERO) > 0) {
            return officialNetValue.setScale(6, RoundingMode.HALF_UP);
        }

        return defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveEstimatedFundNetValue(
        InvestmentPositionEntity position,
        JsonNode estimateInfo,
        BigDecimal officialNetValue
    ) {
        BigDecimal estimatedNetValue = safeDecimal(estimateInfo.path("gsz").asText(null));
        if (estimatedNetValue != null && estimatedNetValue.compareTo(BigDecimal.ZERO) > 0) {
            return estimatedNetValue.setScale(6, RoundingMode.HALF_UP);
        }

        if (officialNetValue != null && officialNetValue.compareTo(BigDecimal.ZERO) > 0) {
            return officialNetValue.setScale(6, RoundingMode.HALF_UP);
        }

        return defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveEstimatedHoldingAmount(
        InvestmentPositionEntity position,
        BigDecimal holdingQuantity,
        BigDecimal estimatedNetValue
    ) {
        if (estimatedNetValue != null && estimatedNetValue.compareTo(BigDecimal.ZERO) > 0) {
            return holdingQuantity.multiply(estimatedNetValue).setScale(2, RoundingMode.HALF_UP);
        }

        return defaultZero(position.getMarketValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePreviousHoldingAmount(
        InvestmentPositionEntity position,
        BigDecimal holdingQuantity,
        BigDecimal officialNetValue,
        BigDecimal estimatedHoldingAmount
    ) {
        if (officialNetValue != null && officialNetValue.compareTo(BigDecimal.ZERO) > 0) {
            return holdingQuantity.multiply(officialNetValue).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal fallback = defaultZero(position.getMarketValue())
            .subtract(defaultZero(position.getDayProfit()))
            .setScale(2, RoundingMode.HALF_UP);
        if (fallback.compareTo(BigDecimal.ZERO) > 0) {
            return fallback;
        }

        return defaultZero(estimatedHoldingAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime resolveFundEstimateTime(InvestmentPositionEntity position, JsonNode estimateInfo) {
        String raw = estimateInfo.path("gztime").asText(null);
        if (StringUtils.hasText(raw)) {
            try {
                return LocalDateTime.parse(raw, FUND_ESTIMATE_TIME_WITH_SECOND_FORMAT);
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(raw, FUND_ESTIMATE_TIME_FORMAT);
                } catch (DateTimeParseException ignoredAgain) {
                    // fall back to the latest synced time on the position
                }
            }
        }

        return position.getLastSyncedAt();
    }

    private JsonNode fetchFundEstimateInfo(String code) {
        return quoteSyncService.fetchFundEstimateInfo(code);
    }
}
