package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.finance.dto.InvestmentDividendResponse;
import com.example.finance.dto.InvestmentDividendIncomeItemResponse;
import com.example.finance.dto.InvestmentDividendIncomePageResponse;
import com.example.finance.dto.InvestmentDividendIncomeSummaryResponse;
import com.example.finance.dto.InvestmentAssetDetailResponse;
import com.example.finance.dto.InvestmentAutoInvestPlanRequest;
import com.example.finance.dto.InvestmentAutoInvestPlanResponse;
import com.example.finance.dto.InvestmentChartPointResponse;
import com.example.finance.dto.InvestmentDetailStatResponse;
import com.example.finance.dto.FundProfitForecastAccountResponse;
import com.example.finance.dto.FundProfitForecastHoldingResponse;
import com.example.finance.dto.FundProfitForecastResponse;
import com.example.finance.dto.FundProfitCalendarCellResponse;
import com.example.finance.dto.FundProfitContributionResponse;
import com.example.finance.dto.FundProfitDetailResponse;
import com.example.finance.dto.InvestmentFundRedeemFeeOptionResponse;
import com.example.finance.dto.FundProfitPageAccountResponse;
import com.example.finance.dto.FundProfitPageResponse;
import com.example.finance.dto.FundProfitPageSummaryMetricResponse;
import com.example.finance.dto.FundProfitPageSummaryResponse;
import com.example.finance.dto.FundProfitSelectionResponse;
import com.example.finance.dto.FundProfitTrendPointResponse;
import com.example.finance.dto.InvestmentPositionRequest;
import com.example.finance.dto.InvestmentPositionResponse;
import com.example.finance.dto.InvestmentProductRequest;
import com.example.finance.dto.InvestmentProductResponse;
import com.example.finance.dto.InvestmentSummaryResponse;
import com.example.finance.dto.InvestmentTrendAllocationResponse;
import com.example.finance.dto.InvestmentTrendContributorResponse;
import com.example.finance.dto.InvestmentTrendPointResponse;
import com.example.finance.dto.InvestmentTrendResponse;
import com.example.finance.dto.InvestmentTransactionRequest;
import com.example.finance.dto.InvestmentTransactionResponse;
import com.example.finance.entity.AccountEntity;
import com.example.finance.entity.AccountTypeEntity;
import com.example.finance.entity.InvestmentAutoInvestPlanEntity;
import com.example.finance.entity.InvestmentDividendPlanEntity;
import com.example.finance.entity.InvestmentDividendRecordEntity;
import com.example.finance.entity.InvestmentPositionEntity;
import com.example.finance.entity.InvestmentPriceQuoteEntity;
import com.example.finance.entity.InvestmentProductEntity;
import com.example.finance.entity.InvestmentTransactionEntity;
import com.example.finance.mapper.AccountMapper;
import com.example.finance.mapper.AccountTypeMapper;
import com.example.finance.mapper.InvestmentAutoInvestPlanMapper;
import com.example.finance.mapper.InvestmentDividendPlanMapper;
import com.example.finance.mapper.InvestmentDividendRecordMapper;
import com.example.finance.mapper.InvestmentPositionMapper;
import com.example.finance.mapper.InvestmentPriceQuoteMapper;
import com.example.finance.mapper.InvestmentProductMapper;
import com.example.finance.mapper.InvestmentTransactionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class InvestmentService {

    private record FundQuoteSnapshot(
        LocalDate quoteDate,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
    }

    private record TrendRangeMeta(
        String rangeKey,
        String rangeLabel,
        LocalDate startDate,
        LocalDate endDate,
        boolean monthlyBuckets
    ) {
    }

    private record FundRedeemFeeRule(
        String label,
        int minHoldingDaysInclusive,
        Integer maxHoldingDaysExclusive,
        BigDecimal feeRate
    ) {
    }

    private record FundHoldingLot(
        LocalDate acquiredDate,
        BigDecimal quantity
    ) {
    }

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

    private record DividendPrediction(
        boolean stable,
        BigDecimal estimatedAmount
    ) {
    }

    private record DividendProfile(
        boolean stable,
        BigDecimal annualDividendPerUnit,
        int stableYears,
        LocalDate lastDividendDate,
        String source
    ) {
    }

    private static final Logger log = LoggerFactory.getLogger(InvestmentService.class);
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String DEFAULT_UNIT_NAME = "份";
    private static final String ACTIVE_STATUS = "active";
    private static final String INVESTMENT_ACCOUNT_TYPE_CODE = "investment";
    private static final String NORMAL_STATUS = "normal";
    private static final String VOIDED_STATUS = "voided";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String FUND_PRODUCT_TYPE = "fund";
    private static final String FUND_QUOTE_SOURCE = "EASTMONEY_FUND_DAILY";
    private static final String FUND_DIVIDEND_PLAN_SOURCE = "EASTMONEY_FUND_FHSP";
    private static final String STOCK_DIVIDEND_HISTORY_SOURCE = "EASTMONEY_STOCK_BONUS";
    private static final String SUBSCRIPTION_STATUS_CONFIRMED = "confirmed";
    private static final String SUBSCRIPTION_STATUS_PENDING = "pending";
    private static final String SETTLEMENT_STATUS_CONFIRMED = "confirmed";
    private static final String SETTLEMENT_STATUS_PENDING = "pending";
    private static final String AUTO_INVEST_STATUS_ACTIVE = "active";
    private static final String AUTO_INVEST_STATUS_PAUSED = "paused";
    private static final String AUTO_INVEST_STATUS_CANCELLED = "cancelled";
    private static final String AUTO_INVEST_FREQUENCY_DAILY = "daily";
    private static final String AUTO_INVEST_FREQUENCY_WEEKLY = "weekly";
    private static final String AUTO_INVEST_FREQUENCY_MONTHLY = "monthly";
    private static final String SUBSCRIPTION_TIME_SLOT_BEFORE_1500 = "before_1500";
    private static final String SUBSCRIPTION_TIME_SLOT_AFTER_1500 = "after_1500";
    private static final int DEFAULT_FUND_CONFIRM_DAYS = 1;
    private static final int QDII_FUND_CONFIRM_DAYS = 2;
    private static final int DIVIDEND_HISTORY_YEARS = 3;
    private static final int MIN_STABLE_DIVIDEND_YEARS = 2;
    private static final LocalTime FUND_SUBSCRIPTION_CUTOFF_TIME = LocalTime.of(15, 0);
    private static final Set<String> POSITION_ACCOUNT_TYPE_CODES = Set.of("investment", "gold");
    private static final DateTimeFormatter NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter FUND_ESTIMATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FUND_ESTIMATE_TIME_WITH_SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter POSITION_SYNC_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InvestmentProductMapper productMapper;
    private final InvestmentPositionMapper positionMapper;
    private final InvestmentTransactionMapper transactionMapper;
    private final InvestmentAutoInvestPlanMapper autoInvestPlanMapper;
    private final InvestmentDividendPlanMapper dividendPlanMapper;
    private final InvestmentDividendRecordMapper dividendRecordMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final InvestmentPriceQuoteMapper priceQuoteMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final HttpClient httpClient;
    private final Set<LocalDate> marketClosedDates;

    public InvestmentService(
        InvestmentProductMapper productMapper,
        InvestmentPositionMapper positionMapper,
        InvestmentTransactionMapper transactionMapper,
        InvestmentAutoInvestPlanMapper autoInvestPlanMapper,
        InvestmentDividendPlanMapper dividendPlanMapper,
        InvestmentDividendRecordMapper dividendRecordMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        InvestmentPriceQuoteMapper priceQuoteMapper,
        ObjectMapper objectMapper,
        @Value("${finance.investment.market-closed-dates:}") String marketClosedDatesConfig
    ) {
        this.productMapper = productMapper;
        this.positionMapper = positionMapper;
        this.transactionMapper = transactionMapper;
        this.autoInvestPlanMapper = autoInvestPlanMapper;
        this.dividendPlanMapper = dividendPlanMapper;
        this.dividendRecordMapper = dividendRecordMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.priceQuoteMapper = priceQuoteMapper;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.marketClosedDates = parseMarketClosedDates(marketClosedDatesConfig);
    }

    public List<InvestmentProductResponse> listProducts(String productType, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return fetchExternalProducts(keyword.trim(), productType);
    }

    public InvestmentProductResponse createProduct(InvestmentProductRequest request) {
        InvestmentProductEntity entity = fillProduct(new InvestmentProductEntity(), request);
        evaluateDividendProfile(entity);
        productMapper.insert(entity);
        return toProductResponse(productMapper.selectById(entity.getId()));
    }

    public List<InvestmentPositionResponse> listPositions(Long userId, Long accountId, String productType, String status) {
        LambdaQueryWrapper<InvestmentPositionEntity> wrapper = new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(userId != null, InvestmentPositionEntity::getUserId, userId)
            .eq(accountId != null, InvestmentPositionEntity::getAccountId, accountId)
            .eq(StringUtils.hasText(status), InvestmentPositionEntity::getStatus, status)
            .orderByDesc(InvestmentPositionEntity::getMarketValue)
            .orderByDesc(InvestmentPositionEntity::getId);

        List<InvestmentPositionEntity> positions = filterPositionsByAccountType(
            positionMapper.selectList(wrapper),
            INVESTMENT_ACCOUNT_TYPE_CODE
        );
        List<InvestmentPositionResponse> responses = toPositionResponses(positions);
        if (!StringUtils.hasText(productType)) {
            return responses;
        }
        return responses.stream()
            .filter(item -> productType.equals(item.getProductType()))
            .toList();
    }

    public Optional<InvestmentPositionResponse> getPosition(Long id) {
        InvestmentPositionEntity position = positionMapper.selectById(id);
        if (position == null) {
            return Optional.empty();
        }
        return Optional.of(toPositionResponse(
            position,
            productMapper.selectById(position.getProductId()),
            accountMapper.selectById(position.getAccountId())
        ));
    }

    public Optional<InvestmentAssetDetailResponse> getPositionDetail(Long id) {
        InvestmentPositionEntity position = positionMapper.selectById(id);
        if (position == null) {
            return Optional.empty();
        }

        InvestmentProductEntity product = productMapper.selectById(position.getProductId());
        AccountEntity account = accountMapper.selectById(position.getAccountId());
        InvestmentPositionResponse positionResponse = toPositionResponse(position, product, account);
        InvestmentAssetDetailResponse response = new InvestmentAssetDetailResponse();
        response.setPosition(positionResponse);
        response.setProductType(product == null ? null : product.getProductType());
        response.setName(product == null ? null : product.getName());
        response.setSymbol(product == null ? null : product.getSymbol());
        response.setMarket(product == null ? null : product.getMarket());
        response.setUnitName(product == null ? null : product.getUnitName());
        response.setSource("本地持仓");
        response.setDescription(position.getRemark());
        response.setHoldingStats(buildHoldingStats(positionResponse));
        response.setMarketStats(new ArrayList<>());
        response.setDividendRecords(buildRecentDividendRecords(position, product));
        response.setChartPoints(Collections.emptyList());

        response.setLatestPrice(positionResponse.getCurrentPrice());
        response.setUpdatedAt(positionResponse.getLastSyncedAt() == null ? null : positionResponse.getLastSyncedAt().toString());
        response.setChartType(product != null && "stock".equals(product.getProductType()) ? "candlestick" : "line");
        response.setFundRedeemFeeOptions(product != null && FUND_PRODUCT_TYPE.equals(product.getProductType())
            ? buildFundRedeemFeeOptions(product.getSymbol())
            : Collections.emptyList());
        response.setSource("本地持仓");
        response.setDescription(isPendingFundSubscription(position)
            ? "该基金按场外申购规则处理中，份额将在确认后生成。"
            : "页面先展示本地持仓数据，行情和走势由前端直接从公开接口加载。");
        response.setMarketStats(List.of(
            stat("资产类型", product == null ? "-" : productTypeName(product.getProductType()), null),
            stat("资产代码", product == null ? "-" : blankToDash(product.getSymbol()), null),
            stat("市场", product == null ? "-" : blankToDash(product.getMarket()), null),
            stat("当前净值", moneyText(positionResponse.getCurrentPrice(), "stock".equals(product == null ? null : product.getProductType()) ? 2 : 4), null),
            stat("申购状态", isPendingFundSubscription(position) ? "待确认" : "已确认", null),
            stat("最新同步", response.getUpdatedAt() == null ? "-" : response.getUpdatedAt(), null)
        ));
        return Optional.of(response);
    }

    @Transactional
    public InvestmentPositionResponse createPosition(InvestmentPositionRequest request) {
        AccountEntity account = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        AccountEntity fundingAccount = resolveFundingAccountForCreate(request, account);
        InvestmentProductEntity product = request.getProductId() != null
            ? requireProduct(request.getProductId())
            : createOrLoadProduct(request.getProduct());
        ensureDividendProfile(product);

        if (isFundSubscriptionProduct(product)) {
            return createFundSubscriptionPosition(request, account, fundingAccount, product);
        }

        validateDirectPositionRequest(request);

        InvestmentPositionEntity entity = new InvestmentPositionEntity();
        fillPosition(entity, request, product.getId());
        if (fundingAccount != null) {
            deductFundingAccount(fundingAccount, entity.getCostAmount());
        }
        positionMapper.insert(entity);
        createBuyTransaction(
            entity,
            entity.getHoldingQuantity(),
            entity.getAvgCostPrice(),
            entity.getCostAmount(),
            "新增资产买入"
        );
        syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
        return toPositionResponse(positionMapper.selectById(entity.getId()), product, account);
    }

    @Transactional
    public Optional<InvestmentPositionResponse> updatePosition(Long id, InvestmentPositionRequest request) {
        InvestmentPositionEntity entity = positionMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Long oldAccountId = entity.getAccountId();
        AccountEntity account = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        InvestmentProductEntity product = request.getProductId() != null
            ? requireProduct(request.getProductId())
            : createOrLoadProduct(request.getProduct());
        if (isPendingFundSubscription(entity)) {
            entity.setIncludeInNetWorth(request.getIncludeInNetWorth() == null ? entity.getIncludeInNetWorth() : request.getIncludeInNetWorth());
            entity.setRemark(request.getRemark());
            positionMapper.updateById(entity);
            return Optional.of(toPositionResponse(positionMapper.selectById(id), product, account));
        }
        validateDirectPositionRequest(request);
        fillPosition(entity, request, product.getId());
        positionMapper.updateById(entity);
        syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
        if (!request.getAccountId().equals(oldAccountId)) {
            syncInvestmentAccountBalance(request.getUserId(), oldAccountId);
        }
        return Optional.of(toPositionResponse(positionMapper.selectById(id), product, account));
    }

    @Transactional
    public boolean deletePosition(Long id, Long userId) {
        InvestmentPositionEntity entity = positionMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        transactionMapper.delete(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getUserId, userId)
            .eq(InvestmentTransactionEntity::getPositionId, id));
        autoInvestPlanMapper.delete(new LambdaQueryWrapper<InvestmentAutoInvestPlanEntity>()
            .eq(InvestmentAutoInvestPlanEntity::getUserId, userId)
            .eq(InvestmentAutoInvestPlanEntity::getPositionId, id));
        dividendRecordMapper.delete(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
            .eq(InvestmentDividendRecordEntity::getUserId, userId)
            .eq(InvestmentDividendRecordEntity::getPositionId, id));
        boolean deleted = positionMapper.deleteById(id) > 0;
        if (deleted) {
            syncInvestmentAccountBalance(userId, entity.getAccountId());
        }
        return deleted;
    }

    public InvestmentSummaryResponse summary(Long userId, Long accountId) {
        return buildSummaryResponse(userId, loadActiveInvestmentPositions(userId, accountId));
    }

    public InvestmentTrendResponse trend(Long userId, Long accountId, String range) {
        List<InvestmentPositionEntity> positions = loadActiveInvestmentPositions(userId, accountId);
        InvestmentSummaryResponse summary = buildSummaryResponse(userId, positions);
        TrendRangeMeta rangeMeta = resolveTrendRange(range, userId, accountId, positions);

        Map<Long, InvestmentProductEntity> products = positions.isEmpty()
            ? Collections.emptyMap()
            : productMapper.selectByIds(positions.stream()
                .map(InvestmentPositionEntity::getProductId)
                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId = loadConfirmedTransactionsByPosition(userId, accountId, positions);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId = loadPriceHistoryByProduct(products.keySet());
        List<LocalDate> bucketDates = buildTrendBucketDates(rangeMeta);
        List<InvestmentTrendPointResponse> trendPoints = buildTrendPoints(
            bucketDates,
            rangeMeta,
            positions,
            transactionsByPositionId,
            priceHistoryByProductId
        );

        BigDecimal periodChangeAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal periodChangeRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (trendPoints.size() >= 2) {
            BigDecimal startValue = defaultZero(trendPoints.get(0).getValue()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal endValue = defaultZero(trendPoints.get(trendPoints.size() - 1).getValue()).setScale(2, RoundingMode.HALF_UP);
            periodChangeAmount = endValue.subtract(startValue).setScale(2, RoundingMode.HALF_UP);
            periodChangeRate = rate(periodChangeAmount, startValue);
        }

        InvestmentTrendResponse response = new InvestmentTrendResponse();
        response.setUserId(userId);
        response.setAccountId(accountId);
        response.setRange(rangeMeta.rangeKey());
        response.setRangeLabel(rangeMeta.rangeLabel());
        response.setStartDate(rangeMeta.startDate());
        response.setEndDate(rangeMeta.endDate());
        response.setTotalMarketValue(summary.getTotalMarketValue());
        response.setCumulativeProfit(summary.getCumulativeProfit());
        response.setCumulativeProfitRate(summary.getCumulativeProfitRate());
        response.setPeriodChangeAmount(periodChangeAmount);
        response.setPeriodChangeRate(periodChangeRate);
        response.setLastSyncedAt(summary.getLastSyncedAt());
        response.setTrendPoints(trendPoints);
        response.setAllocations(buildTrendAllocations(positions, products, summary.getTotalMarketValue()));
        response.setContributors(buildTrendContributors(
            rangeMeta,
            positions,
            products,
            transactionsByPositionId,
            priceHistoryByProductId
        ));
        return response;
    }

    private InvestmentSummaryResponse buildSummaryResponse(Long userId, List<InvestmentPositionEntity> positions) {
        BigDecimal totalMarketValue = sum(positions, InvestmentPositionEntity::getMarketValue);
        BigDecimal holdingProfit = sum(positions, InvestmentPositionEntity::getHoldingProfit);
        BigDecimal cumulativeProfit = sum(positions, InvestmentPositionEntity::getCumulativeProfit);
        boolean allPositionsSyncedToday = !positions.isEmpty() && positions.stream().allMatch(this::hasTodayDayProfit);
        BigDecimal dayProfit = allPositionsSyncedToday
            ? sum(positions, InvestmentPositionEntity::getDayProfit)
            : null;

        InvestmentSummaryResponse response = new InvestmentSummaryResponse();
        response.setUserId(userId);
        response.setTotalMarketValue(totalMarketValue);
        response.setDayProfit(dayProfit);
        response.setDayProfitRate(dayProfit == null ? null : rate(dayProfit, totalMarketValue.subtract(dayProfit)));
        response.setHoldingProfit(holdingProfit);
        response.setHoldingProfitRate(rate(holdingProfit, totalMarketValue.subtract(holdingProfit)));
        response.setCumulativeProfit(cumulativeProfit);
        response.setCumulativeProfitRate(rate(cumulativeProfit, totalMarketValue.subtract(holdingProfit)));
        response.setLastSyncedAt(positions.stream()
            .map(InvestmentPositionEntity::getLastSyncedAt)
            .filter(item -> item != null)
            .max(LocalDateTime::compareTo)
            .orElse(null));
        return response;
    }

    private List<InvestmentPositionEntity> loadActiveInvestmentPositions(Long userId, Long accountId) {
        return filterPositionsByAccountType(positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(userId != null, InvestmentPositionEntity::getUserId, userId)
            .eq(accountId != null, InvestmentPositionEntity::getAccountId, accountId)
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS)), INVESTMENT_ACCOUNT_TYPE_CODE);
    }

    private TrendRangeMeta resolveTrendRange(
        String range,
        Long userId,
        Long accountId,
        List<InvestmentPositionEntity> positions
    ) {
        String normalizedRange = switch (range == null ? "" : range.trim().toLowerCase(Locale.ROOT)) {
            case "7d" -> "7d";
            case "30d" -> "30d";
            case "all" -> "all";
            default -> "ytd";
        };
        LocalDate endDate = positions.stream()
            .map(InvestmentPositionEntity::getLastSyncedAt)
            .filter(item -> item != null)
            .map(LocalDateTime::toLocalDate)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now());
        LocalDate startDate;
        boolean monthlyBuckets;
        String rangeLabel;
        switch (normalizedRange) {
            case "7d" -> {
                startDate = endDate.minusDays(6);
                monthlyBuckets = false;
                rangeLabel = "近7日";
            }
            case "30d" -> {
                startDate = endDate.minusDays(29);
                monthlyBuckets = false;
                rangeLabel = "近30日";
            }
            case "all" -> {
                LocalDate earliestPositionDate = positions.stream()
                    .map(InvestmentPositionEntity::getCreatedAt)
                    .filter(item -> item != null)
                    .map(LocalDateTime::toLocalDate)
                    .min(LocalDate::compareTo)
                    .orElse(endDate);
                InvestmentTransactionEntity earliestTransaction = transactionMapper.selectOne(new LambdaQueryWrapper<InvestmentTransactionEntity>()
                    .eq(userId != null, InvestmentTransactionEntity::getUserId, userId)
                    .eq(accountId != null, InvestmentTransactionEntity::getAccountId, accountId)
                    .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
                    .orderByAsc(InvestmentTransactionEntity::getTradeAt)
                    .last("LIMIT 1"));
                LocalDate earliestTransactionDate = earliestTransaction == null || earliestTransaction.getTradeAt() == null
                    ? earliestPositionDate
                    : earliestTransaction.getTradeAt().toLocalDate();
                startDate = earliestPositionDate.isBefore(earliestTransactionDate) ? earliestPositionDate : earliestTransactionDate;
                monthlyBuckets = true;
                rangeLabel = "全部";
            }
            default -> {
                startDate = endDate.withDayOfYear(1);
                monthlyBuckets = true;
                rangeLabel = "年内";
            }
        }
        if (startDate.isAfter(endDate)) {
            startDate = endDate;
        }
        return new TrendRangeMeta(normalizedRange, rangeLabel, startDate, endDate, monthlyBuckets);
    }

    private Map<Long, List<InvestmentTransactionEntity>> loadConfirmedTransactionsByPosition(
        Long userId,
        Long accountId,
        List<InvestmentPositionEntity> positions
    ) {
        if (positions.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> positionIds = positions.stream()
            .map(InvestmentPositionEntity::getId)
            .collect(Collectors.toSet());
        if (positionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
                .eq(userId != null, InvestmentTransactionEntity::getUserId, userId)
                .eq(accountId != null, InvestmentTransactionEntity::getAccountId, accountId)
                .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
                .in(InvestmentTransactionEntity::getPositionId, positionIds)
                .orderByAsc(InvestmentTransactionEntity::getTradeAt)
                .orderByAsc(InvestmentTransactionEntity::getId))
            .stream()
            .collect(Collectors.groupingBy(InvestmentTransactionEntity::getPositionId));
    }

    private Map<Long, NavigableMap<LocalDate, BigDecimal>> loadPriceHistoryByProduct(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, NavigableMap<LocalDate, BigDecimal>> history = new HashMap<>();
        for (InvestmentPriceQuoteEntity quote : priceQuoteMapper.selectList(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
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
        return history;
    }

    private List<LocalDate> buildTrendBucketDates(TrendRangeMeta rangeMeta) {
        if (!rangeMeta.monthlyBuckets()) {
            List<LocalDate> dates = new ArrayList<>();
            LocalDate cursor = rangeMeta.startDate();
            while (!cursor.isAfter(rangeMeta.endDate())) {
                dates.add(cursor);
                cursor = cursor.plusDays(1);
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

    private List<InvestmentTrendPointResponse> buildTrendPoints(
        List<LocalDate> bucketDates,
        TrendRangeMeta rangeMeta,
        List<InvestmentPositionEntity> positions,
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId,
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId
    ) {
        List<InvestmentTrendPointResponse> points = new ArrayList<>();
        for (LocalDate bucketDate : bucketDates) {
            InvestmentTrendPointResponse point = new InvestmentTrendPointResponse();
            point.setKey(bucketDate.toString());
            point.setLabel(buildTrendPointLabel(bucketDate, rangeMeta));
            point.setValue(calculatePortfolioMarketValueAtDate(
                bucketDate,
                positions,
                transactionsByPositionId,
                priceHistoryByProductId
            ));
            points.add(point);
        }
        return points;
    }

    private String buildTrendPointLabel(LocalDate bucketDate, TrendRangeMeta rangeMeta) {
        if (!rangeMeta.monthlyBuckets()) {
            return bucketDate.getMonthValue() + "/" + bucketDate.getDayOfMonth();
        }
        if ("all".equals(rangeMeta.rangeKey()) && rangeMeta.startDate().getYear() != rangeMeta.endDate().getYear()) {
            return bucketDate.getYear() + "-" + String.format("%02d", bucketDate.getMonthValue());
        }
        return bucketDate.getMonthValue() + "月";
    }

    private BigDecimal calculatePortfolioMarketValueAtDate(
        LocalDate targetDate,
        List<InvestmentPositionEntity> positions,
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId,
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId
    ) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (InvestmentPositionEntity position : positions) {
            BigDecimal quantity = resolvePositionQuantityAtDate(position, targetDate, transactionsByPositionId.get(position.getId()));
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal price = resolvePositionPriceAtDate(position, targetDate, priceHistoryByProductId.get(position.getProductId()));
            total = total.add(quantity.multiply(price).setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private List<InvestmentTrendAllocationResponse> buildTrendAllocations(
        List<InvestmentPositionEntity> positions,
        Map<Long, InvestmentProductEntity> products,
        BigDecimal totalMarketValue
    ) {
        if (positions.isEmpty() || totalMarketValue == null || totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }

        Map<String, BigDecimal> marketValueByProductType = new LinkedHashMap<>();
        for (InvestmentPositionEntity position : positions) {
            String productType = Optional.ofNullable(products.get(position.getProductId()))
                .map(InvestmentProductEntity::getProductType)
                .orElse("other");
            marketValueByProductType.merge(
                productType,
                defaultZero(position.getMarketValue()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal::add
            );
        }

        return marketValueByProductType.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .map(entry -> {
                InvestmentTrendAllocationResponse item = new InvestmentTrendAllocationResponse();
                item.setProductType(entry.getKey());
                item.setLabel(productTypeName(entry.getKey()));
                item.setMarketValue(entry.getValue().setScale(2, RoundingMode.HALF_UP));
                item.setPercent(rate(entry.getValue(), totalMarketValue));
                return item;
            })
            .toList();
    }

    private List<InvestmentTrendContributorResponse> buildTrendContributors(
        TrendRangeMeta rangeMeta,
        List<InvestmentPositionEntity> positions,
        Map<Long, InvestmentProductEntity> products,
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId,
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId
    ) {
        if (positions.isEmpty()) {
            return Collections.emptyList();
        }

        List<InvestmentTrendContributorResponse> contributors = new ArrayList<>();
        for (InvestmentPositionEntity position : positions) {
            List<InvestmentTransactionEntity> transactions = transactionsByPositionId.getOrDefault(position.getId(), Collections.emptyList());
            BigDecimal startQuantity = resolvePositionQuantityAtDate(position, rangeMeta.startDate(), transactions);
            BigDecimal endQuantity = resolvePositionQuantityAtDate(position, rangeMeta.endDate(), transactions);
            BigDecimal startPrice = resolvePositionPriceAtDate(position, rangeMeta.startDate(), priceHistoryByProductId.get(position.getProductId()));
            BigDecimal endPrice = resolvePositionPriceAtDate(position, rangeMeta.endDate(), priceHistoryByProductId.get(position.getProductId()));
            BigDecimal startValue = startQuantity.multiply(startPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal endValue = endQuantity.multiply(endPrice).setScale(2, RoundingMode.HALF_UP);

            BigDecimal cashIn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal cashOut = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            for (InvestmentTransactionEntity transaction : transactions) {
                LocalDate effectiveDate = resolveTransactionEffectiveDate(transaction);
                if (effectiveDate == null || effectiveDate.isBefore(rangeMeta.startDate()) || effectiveDate.isAfter(rangeMeta.endDate())) {
                    continue;
                }
                if (isPositiveTradeType(transaction.getTradeType())) {
                    cashIn = cashIn.add(defaultZero(transaction.getAmount()))
                        .add(defaultZero(transaction.getFeeAmount()))
                        .add(defaultZero(transaction.getTaxAmount()))
                        .setScale(2, RoundingMode.HALF_UP);
                } else if (isNegativeTradeType(transaction.getTradeType())) {
                    cashOut = cashOut.add(defaultZero(transaction.getAmount()))
                        .subtract(defaultZero(transaction.getFeeAmount()))
                        .subtract(defaultZero(transaction.getTaxAmount()))
                        .setScale(2, RoundingMode.HALF_UP);
                }
            }

            BigDecimal contributionAmount = endValue.subtract(startValue).subtract(cashIn).add(cashOut).setScale(2, RoundingMode.HALF_UP);
            BigDecimal contributionBase = startValue.add(cashIn).setScale(2, RoundingMode.HALF_UP);
            if (contributionAmount.compareTo(BigDecimal.ZERO) == 0
                && endValue.compareTo(BigDecimal.ZERO) == 0
                && startValue.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            InvestmentProductEntity product = products.get(position.getProductId());
            InvestmentTrendContributorResponse item = new InvestmentTrendContributorResponse();
            item.setPositionId(position.getId());
            item.setProductId(position.getProductId());
            item.setProductType(product == null ? "other" : product.getProductType());
            item.setProductName(product == null ? "未命名资产" : product.getName());
            item.setProductSymbol(product == null ? null : product.getSymbol());
            item.setContributionAmount(contributionAmount);
            item.setContributionRate(rate(contributionAmount, contributionBase.compareTo(BigDecimal.ZERO) > 0 ? contributionBase : endValue));
            contributors.add(item);
        }

        return contributors.stream()
            .sorted((left, right) -> defaultZero(right.getContributionAmount()).abs()
                .compareTo(defaultZero(left.getContributionAmount()).abs()))
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
        if (transactions == null || transactions.isEmpty()) {
            return quantity.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
        }

        for (InvestmentTransactionEntity transaction : transactions) {
            LocalDate effectiveDate = resolveTransactionEffectiveDate(transaction);
            if (effectiveDate != null && effectiveDate.isAfter(targetDate)) {
                quantity = quantity.subtract(signedTransactionQuantity(transaction)).setScale(6, RoundingMode.HALF_UP);
            }
        }
        return quantity.compareTo(BigDecimal.ZERO) > 0 ? quantity : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
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

        BigDecimal currentPrice = defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
        if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            return currentPrice;
        }
        return defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
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

    private LocalDate resolveTransactionEffectiveDate(InvestmentTransactionEntity transaction) {
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

    private BigDecimal signedTransactionQuantity(InvestmentTransactionEntity transaction) {
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
            : loadConfirmedTransactionsByPosition(userId, accountId, positions);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId = positions.isEmpty()
            ? Collections.emptyMap()
            : loadPriceHistoryByProduct(products.keySet());
        LocalDate latestDate = resolveFundProfitLatestDate(positions, priceHistoryByProductId);
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
                earliestDate = minDate(earliestDate, resolveTransactionEffectiveDate(transaction));
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
        BigDecimal startQuantity = resolvePositionQuantityAtDate(position, comparisonDate, transactions);
        BigDecimal endQuantity = resolvePositionQuantityAtDate(position, endDate, transactions);
        BigDecimal startPrice = resolvePositionPriceAtDate(position, comparisonDate, priceHistory);
        BigDecimal endPrice = resolvePositionPriceAtDate(position, endDate, priceHistory);
        BigDecimal startValue = startQuantity.multiply(startPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal endValue = endQuantity.multiply(endPrice).setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashIn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashOut = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (InvestmentTransactionEntity transaction : transactions) {
            LocalDate effectiveDate = resolveTransactionEffectiveDate(transaction);
            if (effectiveDate == null || effectiveDate.isBefore(startDate) || effectiveDate.isAfter(endDate)) {
                continue;
            }
            if (isPositiveTradeType(transaction.getTradeType())) {
                cashIn = cashIn.add(defaultZero(transaction.getAmount()))
                    .add(defaultZero(transaction.getFeeAmount()))
                    .add(defaultZero(transaction.getTaxAmount()))
                    .setScale(2, RoundingMode.HALF_UP);
            } else if (isNegativeTradeType(transaction.getTradeType())) {
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
            FundProfitAggregate aggregate = aggregateFundProfit(buildPositionPeriodProfitMap(context, period).values());
            FundProfitTrendPointResponse point = new FundProfitTrendPointResponse();
            point.setKey(date.toString());
            point.setDate(date);
            point.setLabel(date.getMonthValue() + "/" + date.getDayOfMonth());
            point.setProfit(aggregate.profit());
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
            if (!date.isAfter(context.latestDate())) {
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

    private Integer parseYearValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private YearMonth parseYearMonthValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate parseDateValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private int clampYear(Integer value, int min, int max, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    private YearMonth clampYearMonth(YearMonth value, YearMonth min, YearMonth max, YearMonth fallback) {
        YearMonth resolved = value == null ? fallback : value;
        if (resolved.isBefore(min)) {
            return min;
        }
        if (resolved.isAfter(max)) {
            return max;
        }
        return resolved;
    }

    private LocalDate minDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }

    public int syncDailyFundProfits() {
        List<InvestmentPositionEntity> positions = listActiveFundPositions();
        if (positions.isEmpty()) {
            log.info("基金收益同步跳过：当前没有基金持仓");
            return 0;
        }

        Map<Long, InvestmentProductEntity> products = loadFundProducts(positions);
        if (products.isEmpty()) {
            log.info("基金收益同步跳过：当前没有可同步的基金产品");
            return 0;
        }

        Map<Long, Long> accountUsers = new HashMap<>();
        int syncedProducts = 0;
        int syncedPositions = 0;
        for (Map.Entry<Long, List<InvestmentPositionEntity>> entry : groupPositionsByProduct(positions, products).entrySet()) {
            InvestmentProductEntity product = products.get(entry.getKey());
            if (product == null) {
                continue;
            }
            try {
                int updatedCount = syncFundProfitForProduct(product, entry.getValue(), accountUsers);
                if (updatedCount > 0) {
                    syncedProducts++;
                    syncedPositions += updatedCount;
                }
            } catch (Exception ex) {
                log.warn("基金收益同步失败，productId={}, symbol={}, reason={}",
                    product.getId(), product.getSymbol(), ex.getMessage());
            }
        }

        accountUsers.forEach((accountId, userId) -> syncInvestmentAccountBalance(userId, accountId));
        log.info("基金收益同步完成：{} 个基金产品，{} 条持仓已更新", syncedProducts, syncedPositions);
        return syncedPositions;
    }

    public int syncFundDividendPlans() {
        List<InvestmentPositionEntity> positions = listActiveFundPositions();
        if (positions.isEmpty()) {
            log.info("基金分红计划同步跳过：当前没有基金持仓");
            return 0;
        }

        Map<Long, InvestmentProductEntity> products = loadFundProducts(positions);
        if (products.isEmpty()) {
            log.info("基金分红计划同步跳过：当前没有可同步的基金产品");
            return 0;
        }

        int syncedProducts = 0;
        int syncedPlans = 0;
        for (InvestmentProductEntity product : products.values()) {
            try {
                int updatedCount = syncFundDividendPlansForProduct(product);
                if (updatedCount > 0) {
                    syncedProducts++;
                    syncedPlans += updatedCount;
                }
            } catch (Exception ex) {
                log.warn("基金分红计划同步失败，productId={}, symbol={}, reason={}",
                    product.getId(), product.getSymbol(), ex.getMessage());
            }
        }

        log.info("基金分红计划同步完成：{} 个基金产品，{} 条计划已写入", syncedProducts, syncedPlans);
        return syncedPlans;
    }

    public int settlePendingFundTrades() {
        int settledPositions = settlePendingFundPositions();
        int settledTransactions = settlePendingFundTransactions();
        return settledPositions + settledTransactions;
    }

    private int settlePendingFundPositions() {
        List<InvestmentPositionEntity> positions = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS)
            .eq(InvestmentPositionEntity::getSubscriptionStatus, SUBSCRIPTION_STATUS_PENDING));
        if (positions.isEmpty()) {
            log.info("基金申购结算跳过：当前没有待确认的基金持仓");
            return 0;
        }

        Map<Long, InvestmentProductEntity> products = loadFundProducts(positions);
        if (products.isEmpty()) {
            log.info("基金申购结算跳过：当前没有可结算的基金产品");
            return 0;
        }

        Map<Long, Long> accountUsers = new HashMap<>();
        LocalDate settlementDate = LocalDate.now();
        int settledProducts = 0;
        int settledPositions = 0;
        for (Map.Entry<Long, List<InvestmentPositionEntity>> entry : groupPositionsByProduct(positions, products).entrySet()) {
            InvestmentProductEntity product = products.get(entry.getKey());
            if (product == null) {
                continue;
            }
            try {
                int updatedCount = settlePendingFundTradesForProduct(product, entry.getValue(), accountUsers, settlementDate);
                if (updatedCount > 0) {
                    settledProducts++;
                    settledPositions += updatedCount;
                }
            } catch (Exception ex) {
                log.warn("基金申购结算失败，productId={}, symbol={}, reason={}",
                    product.getId(), product.getSymbol(), ex.getMessage());
            }
        }

        accountUsers.forEach((accountId, userId) -> syncInvestmentAccountBalance(userId, accountId));
        log.info("基金申购结算完成：{} 个基金产品，{} 条持仓已处理", settledProducts, settledPositions);
        return settledPositions;
    }

    private int settlePendingFundTransactions() {
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .eq(InvestmentTransactionEntity::getSettlementStatus, SETTLEMENT_STATUS_PENDING));
        if (transactions.isEmpty()) {
            log.info("基金交易结算跳过：当前没有待确认的基金交易");
            return 0;
        }

        Map<Long, InvestmentProductEntity> products = productMapper.selectByIds(
                transactions.stream().map(InvestmentTransactionEntity::getProductId).collect(Collectors.toSet())
            ).stream()
            .filter(product -> product != null
                && FUND_PRODUCT_TYPE.equals(product.getProductType())
                && StringUtils.hasText(product.getSymbol()))
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        if (products.isEmpty()) {
            log.info("基金交易结算跳过：当前没有可结算的基金产品");
            return 0;
        }

        LocalDate settlementDate = LocalDate.now();
        Map<Long, List<InvestmentTransactionEntity>> transactionsByProduct = transactions.stream()
            .filter(transaction -> products.containsKey(transaction.getProductId()))
            .collect(Collectors.groupingBy(InvestmentTransactionEntity::getProductId));
        Map<Long, Long> accountUsers = new HashMap<>();
        int settledProducts = 0;
        int settledTransactions = 0;
        for (Map.Entry<Long, List<InvestmentTransactionEntity>> entry : transactionsByProduct.entrySet()) {
            InvestmentProductEntity product = products.get(entry.getKey());
            if (product == null) {
                continue;
            }
            try {
                int updatedCount = settlePendingFundTransactionsForProduct(product, entry.getValue(), accountUsers, settlementDate);
                if (updatedCount > 0) {
                    settledProducts++;
                    settledTransactions += updatedCount;
                }
            } catch (Exception ex) {
                log.warn("基金交易结算失败，productId={}, symbol={}, reason={}",
                    product.getId(), product.getSymbol(), ex.getMessage());
            }
        }

        accountUsers.forEach((accountId, userId) -> syncInvestmentAccountBalance(userId, accountId));
        log.info("基金交易结算完成：{} 个基金产品，{} 条交易已处理", settledProducts, settledTransactions);
        return settledTransactions;
    }

    public List<InvestmentTransactionResponse> listTransactions(Long userId, Long accountId, Long positionId) {
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(userId != null, InvestmentTransactionEntity::getUserId, userId)
            .eq(accountId != null, InvestmentTransactionEntity::getAccountId, accountId)
            .eq(positionId != null, InvestmentTransactionEntity::getPositionId, positionId)
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByDesc(InvestmentTransactionEntity::getTradeAt)
            .orderByDesc(InvestmentTransactionEntity::getId));
        if (transactions.isEmpty() && positionId != null) {
            return inferInitialTransaction(userId, accountId, positionId);
        }
        return toTransactionResponses(transactions);
    }

    @Transactional
    public InvestmentTransactionResponse createTransaction(InvestmentTransactionRequest request) {
        AccountEntity investmentAccount = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        InvestmentProductEntity product = requireProduct(request.getProductId());
        InvestmentPositionEntity position = requirePosition(request);
        if (isFundSubscriptionProduct(product)) {
            return createPendingFundTransaction(request, investmentAccount, position, product);
        }
        BigDecimal quantity = defaultZero(request.getQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal price = defaultZero(request.getPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeAmount = defaultZero(request.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = defaultZero(request.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易数量必须大于0");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易金额必须大于0");
        }

        if ("buy".equals(request.getTradeType())) {
            AccountEntity fundingAccount = requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
            deductFundingAccount(fundingAccount, amount.add(feeAmount).add(taxAmount));
            applyBuyTransaction(position, quantity, price, amount, feeAmount, taxAmount);
        } else if ("sell".equals(request.getTradeType())) {
            AccountEntity fundingAccount = resolveFundingAccountForSell(request, investmentAccount);
            if (fundingAccount != null) {
                creditFundingAccount(fundingAccount, amount.subtract(feeAmount).subtract(taxAmount));
            }
            applySellTransaction(position, quantity, price, amount, feeAmount, taxAmount);
        }

        InvestmentTransactionEntity entity = new InvestmentTransactionEntity();
        entity.setTransactionNo(generateTransactionNo());
        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setPositionId(request.getPositionId());
        entity.setProductId(request.getProductId());
        entity.setTradeType(request.getTradeType());
        entity.setQuantity(quantity);
        entity.setPrice(price);
        entity.setAmount(amount);
        entity.setFeeAmount(feeAmount);
        entity.setTaxAmount(taxAmount);
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setFundingAccountId(request.getFundingAccountId());
        entity.setTradeAt(request.getTradeAt());
        entity.setStatus(NORMAL_STATUS);
        entity.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
        entity.setSettlementAppliedDate(null);
        entity.setSettlementExpectedDate(null);
        entity.setSettlementConfirmedAt(LocalDateTime.now());
        entity.setRemark(request.getRemark());
        transactionMapper.insert(entity);
        positionMapper.updateById(position);
        syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
        return toTransactionResponse(entity, product, accountMapper.selectById(entity.getAccountId()));
    }

    public boolean deleteTransaction(Long id, Long userId) {
        InvestmentTransactionEntity entity = transactionMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        entity.setStatus(VOIDED_STATUS);
        transactionMapper.updateById(entity);
        return true;
    }

    public List<InvestmentAutoInvestPlanResponse> listAutoInvestPlans(Long userId, Long accountId, Long positionId, String status) {
        return autoInvestPlanMapper.selectList(new LambdaQueryWrapper<InvestmentAutoInvestPlanEntity>()
                .eq(userId != null, InvestmentAutoInvestPlanEntity::getUserId, userId)
                .eq(accountId != null, InvestmentAutoInvestPlanEntity::getAccountId, accountId)
                .eq(positionId != null, InvestmentAutoInvestPlanEntity::getPositionId, positionId)
                .eq(StringUtils.hasText(status), InvestmentAutoInvestPlanEntity::getStatus, status)
                .orderByAsc(InvestmentAutoInvestPlanEntity::getNextExecuteDate)
                .orderByDesc(InvestmentAutoInvestPlanEntity::getId))
            .stream()
            .map(this::toAutoInvestPlanResponse)
            .toList();
    }

    @Transactional
    public InvestmentAutoInvestPlanResponse createAutoInvestPlan(InvestmentAutoInvestPlanRequest request) {
        InvestmentPositionEntity position = requireAutoInvestPosition(request.getUserId(), request.getPositionId());
        validateAutoInvestPlanRequest(request, position, null);

        InvestmentAutoInvestPlanEntity entity = new InvestmentAutoInvestPlanEntity();
        fillAutoInvestPlan(entity, request, position);
        autoInvestPlanMapper.insert(entity);
        return toAutoInvestPlanResponse(autoInvestPlanMapper.selectById(entity.getId()));
    }

    @Transactional
    public Optional<InvestmentAutoInvestPlanResponse> updateAutoInvestPlan(Long id, InvestmentAutoInvestPlanRequest request) {
        InvestmentAutoInvestPlanEntity entity = autoInvestPlanMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }
        InvestmentPositionEntity position = requireAutoInvestPosition(request.getUserId(), request.getPositionId());
        validateAutoInvestPlanRequest(request, position, id);

        fillAutoInvestPlan(entity, request, position);
        autoInvestPlanMapper.updateById(entity);
        return Optional.of(toAutoInvestPlanResponse(autoInvestPlanMapper.selectById(id)));
    }

    @Transactional
    public boolean deleteAutoInvestPlan(Long id, Long userId) {
        InvestmentAutoInvestPlanEntity entity = autoInvestPlanMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        return autoInvestPlanMapper.deleteById(id) > 0;
    }

    public int executeDueAutoInvestPlans() {
        LocalDate today = LocalDate.now();
        if (isNonTradingDay(today)) {
            log.info("基金定投执行跳过：{} 为休市日，顺延至下一个交易日", today);
            return 0;
        }
        List<InvestmentAutoInvestPlanEntity> plans = autoInvestPlanMapper.selectList(new LambdaQueryWrapper<InvestmentAutoInvestPlanEntity>()
            .eq(InvestmentAutoInvestPlanEntity::getStatus, AUTO_INVEST_STATUS_ACTIVE)
            .le(InvestmentAutoInvestPlanEntity::getNextExecuteDate, today)
            .orderByAsc(InvestmentAutoInvestPlanEntity::getNextExecuteDate)
            .orderByAsc(InvestmentAutoInvestPlanEntity::getId));
        if (plans.isEmpty()) {
            log.info("基金定投执行跳过：当前没有到期计划");
            return 0;
        }

        int executedCount = 0;
        for (InvestmentAutoInvestPlanEntity plan : plans) {
            try {
                if (executeAutoInvestPlan(plan.getId(), today)) {
                    executedCount++;
                }
            } catch (Exception ex) {
                log.warn("基金定投执行失败，planId={}, positionId={}, reason={}",
                    plan.getId(), plan.getPositionId(), ex.getMessage());
            }
        }
        log.info("基金定投执行完成：{} 条计划已提交申购", executedCount);
        return executedCount;
    }

    public List<InvestmentDividendResponse> listDividends(Long userId, Long accountId) {
        List<InvestmentDividendRecordEntity> records = dividendRecordMapper.selectList(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
            .eq(userId != null, InvestmentDividendRecordEntity::getUserId, userId)
            .eq(accountId != null, InvestmentDividendRecordEntity::getAccountId, accountId)
            .eq(InvestmentDividendRecordEntity::getStatus, NORMAL_STATUS)
            .orderByDesc(InvestmentDividendRecordEntity::getPaidAt));
        List<InvestmentDividendResponse> actual = records.stream().map(this::toDividendRecordResponse).toList();
        if (!actual.isEmpty()) {
            return actual;
        }

        List<Long> productIds = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(userId != null, InvestmentPositionEntity::getUserId, userId)
                .eq(accountId != null, InvestmentPositionEntity::getAccountId, accountId)
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .map(InvestmentPositionEntity::getProductId)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyList();
        }
        return dividendPlanMapper.selectList(new LambdaQueryWrapper<InvestmentDividendPlanEntity>()
                .in(InvestmentDividendPlanEntity::getProductId, productIds)
                .orderByDesc(InvestmentDividendPlanEntity::getPayDate))
            .stream()
            .map(this::toDividendPlanResponse)
            .toList();
    }

    public InvestmentDividendIncomePageResponse dividendIncome(Long userId) {
        List<InvestmentPositionEntity> sourcePositions = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getUserId, userId)
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS));
        List<InvestmentPositionEntity> accountFilteredPositions = filterPositionsByAccountType(sourcePositions, INVESTMENT_ACCOUNT_TYPE_CODE);
        if (accountFilteredPositions.isEmpty()) {
            return emptyDividendIncomePage(userId);
        }

        Set<Long> productIds = accountFilteredPositions.stream()
            .map(InvestmentPositionEntity::getProductId)
            .collect(Collectors.toSet());
        Map<Long, InvestmentProductEntity> products = productMapper.selectByIds(productIds).stream()
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        products.values().forEach(this::ensureDividendProfile);

        List<InvestmentPositionEntity> positions = accountFilteredPositions.stream()
            .filter(position -> {
                InvestmentProductEntity product = products.get(position.getProductId());
                return product != null
                    && ("stock".equals(product.getProductType()) || FUND_PRODUCT_TYPE.equals(product.getProductType()))
                    && Boolean.TRUE.equals(product.getStableDividend());
            })
            .toList();
        if (positions.isEmpty()) {
            return emptyDividendIncomePage(userId);
        }

        Set<Long> filteredProductIds = positions.stream()
            .map(InvestmentPositionEntity::getProductId)
            .collect(Collectors.toSet());
        Set<Long> positionIds = positions.stream()
            .map(InvestmentPositionEntity::getId)
            .collect(Collectors.toSet());

        Map<Long, List<InvestmentDividendRecordEntity>> recordsByProductId = dividendRecordMapper.selectList(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
                .in(!positionIds.isEmpty(), InvestmentDividendRecordEntity::getPositionId, positionIds)
                .eq(InvestmentDividendRecordEntity::getStatus, NORMAL_STATUS))
            .stream()
            .collect(Collectors.groupingBy(InvestmentDividendRecordEntity::getProductId));

        Map<Long, List<InvestmentPositionEntity>> positionsByProductId = positions.stream()
            .collect(Collectors.groupingBy(InvestmentPositionEntity::getProductId));

        List<InvestmentDividendIncomeItemResponse> items = positionsByProductId.entrySet().stream()
            .map(entry -> toDividendIncomeItem(
                entry.getKey(),
                entry.getValue(),
                products.get(entry.getKey()),
                recordsByProductId.getOrDefault(entry.getKey(), Collections.emptyList())
            ))
            .filter(Objects::nonNull)
            .sorted(Comparator
                .comparing(InvestmentDividendIncomeItemResponse::getEstimatedDividendAmount, Comparator.nullsFirst(BigDecimal::compareTo))
                .reversed()
                .thenComparing(InvestmentDividendIncomeItemResponse::getActualDividendAmount, Comparator.nullsFirst(BigDecimal::compareTo))
                .reversed()
                .thenComparing(InvestmentDividendIncomeItemResponse::getMarketValue, Comparator.nullsFirst(BigDecimal::compareTo))
                .reversed()
                .thenComparing(InvestmentDividendIncomeItemResponse::getProductName, Comparator.nullsLast(String::compareTo)))
            .toList();

        BigDecimal totalMarketValue = items.stream()
            .map(InvestmentDividendIncomeItemResponse::getMarketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCostAmount = items.stream()
            .map(InvestmentDividendIncomeItemResponse::getCostAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedDividendAmount = items.stream()
            .map(InvestmentDividendIncomeItemResponse::getEstimatedDividendAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualDividendAmount = items.stream()
            .map(InvestmentDividendIncomeItemResponse::getActualDividendAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        InvestmentDividendIncomeSummaryResponse summary = new InvestmentDividendIncomeSummaryResponse();
        summary.setEstimatedDividendAmount(scaleMoney(estimatedDividendAmount));
        summary.setEstimatedDividendRate(scaleRate(rate(estimatedDividendAmount, totalMarketValue)));
        summary.setActualDividendAmount(scaleMoney(actualDividendAmount));
        summary.setActualDividendRate(scaleRate(rate(actualDividendAmount, totalCostAmount)));
        summary.setHoldingCount(items.size());

        LocalDateTime updatedAt = positions.stream()
            .map(InvestmentPositionEntity::getLastSyncedAt)
            .filter(value -> value != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        InvestmentDividendIncomePageResponse response = new InvestmentDividendIncomePageResponse();
        response.setUserId(userId);
        response.setSummary(summary);
        response.setItems(items);
        response.setUpdatedAt(updatedAt);
        return response;
    }

    private InvestmentProductEntity createOrLoadProduct(InvestmentProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("投资产品不能为空");
        }
        InvestmentProductEntity exists = productMapper.selectOne(new LambdaQueryWrapper<InvestmentProductEntity>()
            .eq(InvestmentProductEntity::getProductType, request.getProductType())
            .eq(InvestmentProductEntity::getSymbol, request.getSymbol())
            .eq(StringUtils.hasText(request.getMarket()), InvestmentProductEntity::getMarket, request.getMarket())
            .last("LIMIT 1"));
        if (exists != null) {
            ensureDividendProfile(exists);
            return exists;
        }
        InvestmentProductEntity entity = fillProduct(new InvestmentProductEntity(), request);
        evaluateDividendProfile(entity);
        productMapper.insert(entity);
        return productMapper.selectById(entity.getId());
    }

    private InvestmentProductEntity fillProduct(InvestmentProductEntity entity, InvestmentProductRequest request) {
        entity.setProductType(request.getProductType());
        entity.setMarket(request.getMarket());
        entity.setExchangeCode(request.getExchangeCode());
        entity.setSymbol(request.getSymbol());
        entity.setName(request.getName());
        entity.setShortName(request.getShortName());
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setUnitName(StringUtils.hasText(request.getUnitName()) ? request.getUnitName() : DEFAULT_UNIT_NAME);
        entity.setPricePrecision(request.getPricePrecision() != null ? request.getPricePrecision() : 4);
        entity.setStableDividend(Boolean.FALSE);
        entity.setPredictedAnnualDividendPerUnit(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        entity.setDividendStableYears(0);
        entity.setDividendLastPaidDate(null);
        entity.setDividendDataSource(null);
        entity.setDividendEvaluatedAt(null);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : ACTIVE_STATUS);
        entity.setRemark(request.getRemark());
        return entity;
    }

    private void fillPosition(InvestmentPositionEntity entity, InvestmentPositionRequest request, Long productId) {
        BigDecimal quantity = defaultZero(request.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(request.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = defaultZero(request.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal marketValue = quantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = quantity.compareTo(BigDecimal.ZERO) > 0
            ? costAmount.divide(quantity, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cumulativeProfit = defaultZero(entity.getCumulativeProfit()).setScale(2, RoundingMode.HALF_UP);

        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setProductId(productId);
        entity.setHoldingQuantity(quantity);
        entity.setAvailableQuantity(defaultZero(request.getAvailableQuantity() == null ? quantity : request.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP));
        entity.setFrozenQuantity(defaultZero(request.getFrozenQuantity()).setScale(6, RoundingMode.HALF_UP));
        entity.setCostAmount(costAmount);
        entity.setAvgCostPrice(avgCostPrice);
        entity.setCurrentPrice(currentPrice);
        entity.setMarketValue(marketValue);
        entity.setHoldingProfit(holdingProfit);
        entity.setHoldingProfitRate(rate(holdingProfit, costAmount));
        entity.setCumulativeProfit(cumulativeProfit);
        entity.setCumulativeProfitRate(rate(cumulativeProfit, costAmount));
        entity.setDayProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setDayProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        entity.setIncludeInNetWorth(request.getIncludeInNetWorth() == null ? Boolean.TRUE : request.getIncludeInNetWorth());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : ACTIVE_STATUS);
        entity.setSubscriptionStatus(SUBSCRIPTION_STATUS_CONFIRMED);
        entity.setSubscriptionAppliedDate(null);
        entity.setSubscriptionExpectedConfirmDate(null);
        entity.setSubscriptionConfirmedAt(LocalDateTime.now());
        entity.setLastSyncedAt(LocalDateTime.now());
        entity.setRemark(request.getRemark());
    }

    private void createBuyTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        String remark
    ) {
        createBuyTransaction(position, quantity, price, amount, remark, LocalDateTime.now());
    }

    private void createBuyTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        String remark,
        LocalDateTime tradeAt
    ) {
        InvestmentTransactionEntity transaction = new InvestmentTransactionEntity();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setUserId(position.getUserId());
        transaction.setAccountId(position.getAccountId());
        transaction.setPositionId(position.getId());
        transaction.setProductId(position.getProductId());
        transaction.setTradeType("buy");
        transaction.setQuantity(defaultZero(quantity).setScale(6, RoundingMode.HALF_UP));
        transaction.setPrice(defaultZero(price).setScale(6, RoundingMode.HALF_UP));
        transaction.setAmount(defaultZero(amount).setScale(2, RoundingMode.HALF_UP));
        transaction.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        transaction.setFundingAccountId(null);
        transaction.setTradeAt(tradeAt);
        transaction.setStatus(NORMAL_STATUS);
        transaction.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
        transaction.setSettlementAppliedDate(null);
        transaction.setSettlementExpectedDate(null);
        transaction.setSettlementConfirmedAt(tradeAt);
        transaction.setRemark(remark);
        transactionMapper.insert(transaction);
    }

    private InvestmentPositionEntity requireAutoInvestPosition(Long userId, Long positionId) {
        InvestmentPositionEntity position = positionMapper.selectById(positionId);
        if (position == null || !userId.equals(position.getUserId())) {
            throw new IllegalArgumentException("投资持仓不存在");
        }
        if (!ACTIVE_STATUS.equals(position.getStatus())) {
            throw new IllegalArgumentException("仅支持对有效持仓设置定投");
        }
        if (isPendingFundSubscription(position)) {
            throw new IllegalArgumentException("待确认基金暂不支持设置定投");
        }
        return position;
    }

    private void validateAutoInvestPlanRequest(InvestmentAutoInvestPlanRequest request, InvestmentPositionEntity position, Long currentPlanId) {
        if (!position.getAccountId().equals(request.getAccountId())) {
            throw new IllegalArgumentException("定投计划与持仓所属账户不一致");
        }
        InvestmentProductEntity product = requireProduct(position.getProductId());
        if (!isFundSubscriptionProduct(product)) {
            throw new IllegalArgumentException("当前仅支持基金设置定投");
        }
        requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());

        String frequency = StringUtils.hasText(request.getFrequency()) ? request.getFrequency().trim() : "";
        if (!AUTO_INVEST_FREQUENCY_DAILY.equals(frequency)
            && !AUTO_INVEST_FREQUENCY_WEEKLY.equals(frequency)
            && !AUTO_INVEST_FREQUENCY_MONTHLY.equals(frequency)) {
            throw new IllegalArgumentException("定投周期仅支持每日、每周或每月");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("定投金额必须大于0");
        }
        if (request.getNextExecuteDate() == null) {
            throw new IllegalArgumentException("请选择下次执行日期");
        }
        if (StringUtils.hasText(request.getStatus())
            && !AUTO_INVEST_STATUS_ACTIVE.equals(request.getStatus())
            && !AUTO_INVEST_STATUS_PAUSED.equals(request.getStatus())
            && !AUTO_INVEST_STATUS_CANCELLED.equals(request.getStatus())) {
            throw new IllegalArgumentException("定投计划状态不正确");
        }
        if (!AUTO_INVEST_STATUS_CANCELLED.equals(request.getStatus())) {
            validateAutoInvestPlanUniqueness(request.getUserId(), position.getId(), currentPlanId);
        }
    }

    private void fillAutoInvestPlan(
        InvestmentAutoInvestPlanEntity entity,
        InvestmentAutoInvestPlanRequest request,
        InvestmentPositionEntity position
    ) {
        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setPositionId(position.getId());
        entity.setProductId(position.getProductId());
        entity.setFundingAccountId(request.getFundingAccountId());
        entity.setFrequency(request.getFrequency().trim());
        entity.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setNextExecuteDate(request.getNextExecuteDate());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : AUTO_INVEST_STATUS_ACTIVE);
        entity.setRemark(request.getRemark());
    }

    @Transactional
    private boolean executeAutoInvestPlan(Long planId, LocalDate today) {
        InvestmentAutoInvestPlanEntity plan = autoInvestPlanMapper.selectById(planId);
        if (plan == null) {
            log.info("基金定投执行跳过：计划不存在，planId={}", planId);
            return false;
        }
        if (!AUTO_INVEST_STATUS_ACTIVE.equals(plan.getStatus())) {
            log.info("基金定投执行跳过：计划不是启用状态，planId={}, status={}", planId, plan.getStatus());
            return false;
        }
        if (plan.getNextExecuteDate() == null || plan.getNextExecuteDate().isAfter(today)) {
            log.info("基金定投执行跳过：计划未到执行日，planId={}, nextExecuteDate={}", planId, plan.getNextExecuteDate());
            return false;
        }

        LocalDate currentExecuteDate = plan.getNextExecuteDate();
        LocalDate effectiveExecuteDate = nextTradingDay(currentExecuteDate);
        if (effectiveExecuteDate.isAfter(today)) {
            log.info("基金定投执行跳过：计划顺延至下一个交易日，planId={}, nextExecuteDate={}, effectiveExecuteDate={}",
                planId, currentExecuteDate, effectiveExecuteDate);
            return false;
        }
        LocalDateTime executedAt = LocalDateTime.now();
        LocalDate nextExecuteDate = resolveNextAutoInvestExecuteDate(plan.getFrequency(), currentExecuteDate, today);
        int claimedRows = autoInvestPlanMapper.update(null, new LambdaUpdateWrapper<InvestmentAutoInvestPlanEntity>()
            .set(InvestmentAutoInvestPlanEntity::getLastExecutedAt, executedAt)
            .set(InvestmentAutoInvestPlanEntity::getNextExecuteDate, nextExecuteDate)
            .eq(InvestmentAutoInvestPlanEntity::getId, planId)
            .eq(InvestmentAutoInvestPlanEntity::getStatus, AUTO_INVEST_STATUS_ACTIVE)
            .eq(InvestmentAutoInvestPlanEntity::getNextExecuteDate, currentExecuteDate));
        if (claimedRows == 0) {
            log.info("基金定投执行跳过：计划已被其他任务处理，planId={}", planId);
            return false;
        }

        AccountEntity investmentAccount = requireInvestmentAccount(plan.getUserId(), plan.getAccountId());
        InvestmentPositionEntity position = requireAutoInvestPosition(plan.getUserId(), plan.getPositionId());
        InvestmentProductEntity product = requireProduct(plan.getProductId());
        if (!isFundSubscriptionProduct(product)) {
            throw new IllegalArgumentException("当前仅支持基金定投");
        }

        InvestmentTransactionRequest request = new InvestmentTransactionRequest();
        request.setUserId(plan.getUserId());
        request.setAccountId(plan.getAccountId());
        request.setPositionId(plan.getPositionId());
        request.setProductId(plan.getProductId());
        request.setTradeType("buy");
        request.setQuantity(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        request.setPrice(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        request.setAmount(plan.getAmount());
        request.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        request.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        request.setCurrencyCode(plan.getCurrencyCode());
        request.setTradeAt(effectiveExecuteDate.atTime(9, 5));
        request.setFundingAccountId(plan.getFundingAccountId());
        request.setSubscriptionTimeSlot(SUBSCRIPTION_TIME_SLOT_BEFORE_1500);
        request.setRemark(buildAutoInvestExecutionRemark(plan));
        createPendingFundTransaction(request, investmentAccount, position, product);
        return true;
    }

    private void validateAutoInvestPlanUniqueness(Long userId, Long positionId, Long currentPlanId) {
        Long duplicatedCount = autoInvestPlanMapper.selectCount(new LambdaQueryWrapper<InvestmentAutoInvestPlanEntity>()
            .eq(InvestmentAutoInvestPlanEntity::getUserId, userId)
            .eq(InvestmentAutoInvestPlanEntity::getPositionId, positionId)
            .ne(currentPlanId != null, InvestmentAutoInvestPlanEntity::getId, currentPlanId)
            .in(InvestmentAutoInvestPlanEntity::getStatus, List.of(AUTO_INVEST_STATUS_ACTIVE, AUTO_INVEST_STATUS_PAUSED)));
        if (duplicatedCount != null && duplicatedCount > 0) {
            throw new IllegalArgumentException("该基金已有有效的定投计划，请先修改现有计划");
        }
    }

    private String buildAutoInvestExecutionRemark(InvestmentAutoInvestPlanEntity plan) {
        String base = "定投执行[planId=" + plan.getId() + "]";
        return StringUtils.hasText(plan.getRemark()) ? base + "：" + plan.getRemark().trim() : base;
    }

    private LocalDate resolveNextAutoInvestExecuteDate(String frequency, LocalDate currentDate, LocalDate today) {
        LocalDate rawNext = currentDate;
        LocalDate next;
        do {
            if (AUTO_INVEST_FREQUENCY_MONTHLY.equals(frequency)) {
                rawNext = rawNext.plusMonths(1);
            } else if (AUTO_INVEST_FREQUENCY_WEEKLY.equals(frequency)) {
                rawNext = rawNext.plusWeeks(1);
            } else {
                rawNext = rawNext.plusDays(1);
            }
            next = nextTradingDay(rawNext);
        } while (!next.isAfter(today));
        return next;
    }

    private AccountEntity requireInvestmentAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("投资账户不存在");
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !POSITION_ACCOUNT_TYPE_CODES.contains(accountType.getCode())) {
            throw new IllegalArgumentException("请选择投资或黄金账户");
        }
        return account;
    }

    private AccountEntity requireCashFundingAccount(Long userId, Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("请选择资金账户");
        }
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("资金账户不存在");
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !CASH_ACCOUNT_TYPE_CODE.equals(accountType.getCode())) {
            throw new IllegalArgumentException("资金账户必须为现金账户");
        }
        return account;
    }

    private AccountEntity resolveFundingAccountForCreate(InvestmentPositionRequest request, AccountEntity account) {
        if (request.getFundingAccountId() != null) {
            return requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
        }
        return null;
    }

    private AccountEntity resolveFundingAccountForSell(InvestmentTransactionRequest request, AccountEntity account) {
        if (request.getFundingAccountId() != null) {
            return requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
        }

        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType != null && "gold".equals(accountType.getCode())) {
            return null;
        }

        throw new IllegalArgumentException("请选择资金账户");
    }

    private boolean isFundSubscriptionProduct(InvestmentProductEntity product) {
        return product != null && FUND_PRODUCT_TYPE.equals(product.getProductType());
    }

    private boolean isPendingFundSubscription(InvestmentPositionEntity position) {
        return position != null && SUBSCRIPTION_STATUS_PENDING.equals(position.getSubscriptionStatus());
    }

    private void validateDirectPositionRequest(InvestmentPositionRequest request) {
        BigDecimal quantity = request.getHoldingQuantity();
        BigDecimal currentPrice = request.getCurrentPrice();
        BigDecimal availableQuantity = request.getAvailableQuantity();
        BigDecimal frozenQuantity = defaultZero(request.getFrozenQuantity());
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("持仓数量必须大于0");
        }
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("当前价格必须大于0");
        }
        if (availableQuantity != null && availableQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("可用份额不能小于0");
        }
        if (frozenQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("冻结份额不能小于0");
        }
        if (frozenQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("冻结份额不能大于当前份额");
        }
        if (availableQuantity != null && availableQuantity.add(frozenQuantity).compareTo(quantity) > 0) {
            throw new IllegalArgumentException("可用份额与冻结份额之和不能大于当前份额");
        }
    }

    private InvestmentPositionResponse createFundSubscriptionPosition(
        InvestmentPositionRequest request,
        AccountEntity account,
        AccountEntity fundingAccount,
        InvestmentProductEntity product
    ) {
        JsonNode baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        BigDecimal officialPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        LocalDateTime tradeAt = request.getTradeAt() == null ? LocalDateTime.now() : request.getTradeAt();
        LocalDate appliedDate = resolveFundSubscriptionTradeDate(request.getSubscriptionTimeSlot(), tradeAt);
        LocalDate expectedConfirmDate = resolveFundExpectedConfirmDate(appliedDate, baseInfo, product);
        BigDecimal costAmount = defaultZero(request.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        if (costAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("申购金额必须大于0");
        }

        InvestmentPositionEntity entity = new InvestmentPositionEntity();
        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setProductId(product.getId());
        entity.setHoldingQuantity(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        entity.setAvailableQuantity(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        entity.setFrozenQuantity(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        entity.setCostAmount(costAmount);
        entity.setAvgCostPrice(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        entity.setCurrentPrice(defaultZero(officialPrice).setScale(6, RoundingMode.HALF_UP));
        entity.setMarketValue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setDayProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setDayProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        entity.setHoldingProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setHoldingProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        entity.setCumulativeProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setCumulativeProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        entity.setIncludeInNetWorth(request.getIncludeInNetWorth() == null ? Boolean.TRUE : request.getIncludeInNetWorth());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : ACTIVE_STATUS);
        entity.setSubscriptionStatus(SUBSCRIPTION_STATUS_PENDING);
        entity.setSubscriptionAppliedDate(appliedDate);
        entity.setSubscriptionExpectedConfirmDate(expectedConfirmDate);
        entity.setSubscriptionConfirmedAt(null);
        entity.setLastSyncedAt(tradeAt);
        entity.setRemark(request.getRemark());

        if (fundingAccount != null) {
            deductFundingAccount(fundingAccount, costAmount);
        }
        positionMapper.insert(entity);
        InvestmentPriceQuoteEntity immediateQuote = resolveImmediateSettlementQuote(product, baseInfo, appliedDate, LocalDateTime.now());
        if (immediateQuote != null) {
            LocalDateTime confirmedAt = immediateQuote.getQuoteTime() == null ? tradeAt : immediateQuote.getQuoteTime();
            confirmPendingFundSubscription(entity, immediateQuote, confirmedAt);
            positionMapper.updateById(entity);
        }
        syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
        return toPositionResponse(positionMapper.selectById(entity.getId()), product, account);
    }

    private InvestmentTransactionResponse createPendingFundTransaction(
        InvestmentTransactionRequest request,
        AccountEntity investmentAccount,
        InvestmentPositionEntity position,
        InvestmentProductEntity product
    ) {
        JsonNode baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        LocalDateTime tradeAt = request.getTradeAt() == null ? LocalDateTime.now() : request.getTradeAt();
        LocalDate appliedDate = resolveFundSubscriptionTradeDate(request.getSubscriptionTimeSlot(), tradeAt);
        LocalDate expectedSettlementDate = resolveFundExpectedConfirmDate(appliedDate, baseInfo, product);

        if ("buy".equals(request.getTradeType())) {
            BigDecimal amount = defaultZero(request.getAmount()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("申购金额必须大于0");
            }
            BigDecimal feeAmount = defaultZero(request.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = defaultZero(request.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);
            AccountEntity fundingAccount = requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
            deductFundingAccount(fundingAccount, amount.add(feeAmount).add(taxAmount));

            InvestmentPriceQuoteEntity immediateQuote = resolveImmediateSettlementQuote(product, baseInfo, appliedDate, LocalDateTime.now());
            if (immediateQuote != null) {
                BigDecimal confirmedPrice = resolveConfirmedFundPrice(immediateQuote);
                if (confirmedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("基金净值数据无效，暂无法确认份额");
                }
                BigDecimal quantity = scaleFundQuantity(amount.divide(confirmedPrice, 6, RoundingMode.HALF_UP));
                applyBuyTransaction(position, quantity, confirmedPrice, amount, feeAmount, taxAmount);

                InvestmentTransactionEntity entity = buildConfirmedFundTransactionEntity(
                    request,
                    position,
                    quantity,
                    confirmedPrice,
                    amount,
                    feeAmount,
                    taxAmount,
                    tradeAt,
                    appliedDate,
                    expectedSettlementDate,
                    immediateQuote.getQuoteTime() == null ? tradeAt : immediateQuote.getQuoteTime()
                );
                transactionMapper.insert(entity);
                positionMapper.updateById(position);
                syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
                return toTransactionResponse(entity, product, investmentAccount);
            }

            InvestmentTransactionEntity entity = buildPendingFundTransactionEntity(
                request,
                position,
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                amount,
                feeAmount,
                taxAmount,
                tradeAt,
                appliedDate,
                expectedSettlementDate
            );
            transactionMapper.insert(entity);
            return toTransactionResponse(entity, product, investmentAccount);
        }

        if ("sell".equals(request.getTradeType())) {
            BigDecimal quantity = scaleFundQuantity(defaultZero(request.getQuantity()));
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("赎回份额必须大于0");
            }
            BigDecimal taxAmount = defaultZero(request.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);
            AccountEntity fundingAccount = resolveFundingAccountForSell(request, investmentAccount);
            BigDecimal estimatedPrice = defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
            BigDecimal estimatedAmount = estimatedPrice.compareTo(BigDecimal.ZERO) > 0
                ? quantity.multiply(estimatedPrice).setScale(2, RoundingMode.HALF_UP)
                : defaultZero(request.getAmount()).setScale(2, RoundingMode.HALF_UP);
            if (estimatedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("回款金额必须大于0");
            }
            BigDecimal feeAmount = request.getFeeAmount() == null
                ? calculateFundSellFeeAmount(position, product, quantity, appliedDate, estimatedPrice)
                : defaultZero(request.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);

            freezePendingFundSellQuantity(position, quantity);
            positionMapper.updateById(position);

            InvestmentTransactionEntity entity = buildPendingFundTransactionEntity(
                request,
                position,
                quantity,
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                estimatedAmount,
                feeAmount,
                taxAmount,
                tradeAt,
                appliedDate,
                expectedSettlementDate
            );
            entity.setFundingAccountId(fundingAccount == null ? null : fundingAccount.getId());
            transactionMapper.insert(entity);
            return toTransactionResponse(entity, product, investmentAccount);
        }

        throw new IllegalArgumentException("基金仅支持加仓或减仓");
    }

    private InvestmentTransactionEntity buildPendingFundTransactionEntity(
        InvestmentTransactionRequest request,
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        LocalDateTime tradeAt,
        LocalDate appliedDate,
        LocalDate expectedSettlementDate
    ) {
        InvestmentTransactionEntity entity = new InvestmentTransactionEntity();
        entity.setTransactionNo(generateTransactionNo());
        entity.setUserId(request.getUserId());
        entity.setAccountId(request.getAccountId());
        entity.setPositionId(position.getId());
        entity.setProductId(request.getProductId());
        entity.setTradeType(request.getTradeType());
        entity.setQuantity(defaultZero(quantity).setScale(6, RoundingMode.HALF_UP));
        entity.setPrice(defaultZero(price).setScale(6, RoundingMode.HALF_UP));
        entity.setAmount(defaultZero(amount).setScale(2, RoundingMode.HALF_UP));
        entity.setFeeAmount(defaultZero(feeAmount).setScale(2, RoundingMode.HALF_UP));
        entity.setTaxAmount(defaultZero(taxAmount).setScale(2, RoundingMode.HALF_UP));
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY_CODE);
        entity.setFundingAccountId(request.getFundingAccountId());
        entity.setTradeAt(tradeAt);
        entity.setStatus(NORMAL_STATUS);
        entity.setSettlementStatus(SETTLEMENT_STATUS_PENDING);
        entity.setSettlementAppliedDate(appliedDate);
        entity.setSettlementExpectedDate(expectedSettlementDate);
        entity.setSettlementConfirmedAt(null);
        entity.setRemark(request.getRemark());
        return entity;
    }

    private InvestmentTransactionEntity buildConfirmedFundTransactionEntity(
        InvestmentTransactionRequest request,
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        LocalDateTime tradeAt,
        LocalDate appliedDate,
        LocalDate expectedSettlementDate,
        LocalDateTime confirmedAt
    ) {
        InvestmentTransactionEntity entity = buildPendingFundTransactionEntity(
            request,
            position,
            quantity,
            price,
            amount,
            feeAmount,
            taxAmount,
            tradeAt,
            appliedDate,
            expectedSettlementDate
        );
        entity.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
        entity.setSettlementConfirmedAt(confirmedAt);
        return entity;
    }

    private LocalDate resolveFundExpectedConfirmDate(LocalDate appliedDate, JsonNode baseInfo, InvestmentProductEntity product) {
        return isQdiiFund(baseInfo, product)
            ? addTradingDays(appliedDate, resolveFundConfirmDelayDays(baseInfo, product))
            : appliedDate;
    }

    private int resolveFundConfirmDelayDays(JsonNode baseInfo, InvestmentProductEntity product) {
        return isQdiiFund(baseInfo, product)
            ? QDII_FUND_CONFIRM_DAYS
            : DEFAULT_FUND_CONFIRM_DAYS;
    }

    private boolean isQdiiFund(JsonNode baseInfo, InvestmentProductEntity product) {
        String fundType = baseInfo.path("FTYPE").asText("");
        String shortName = baseInfo.path("SHORTNAME").asText(product == null ? "" : product.getName());
        String productName = product == null ? "" : product.getName();
        return containsIgnoreCase(fundType, "QDII")
            || fundType.contains("海外")
            || containsIgnoreCase(shortName, "QDII")
            || containsIgnoreCase(productName, "QDII");
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value)
            && StringUtils.hasText(keyword)
            && value.toUpperCase(Locale.ROOT).contains(keyword.toUpperCase(Locale.ROOT));
    }

    private Set<LocalDate> parseMarketClosedDates(String marketClosedDatesConfig) {
        if (!StringUtils.hasText(marketClosedDatesConfig)) {
            return Collections.emptySet();
        }
        Set<LocalDate> dates = new HashSet<>();
        for (String rawDate : marketClosedDatesConfig.split(",")) {
            String value = rawDate.trim();
            if (value.isEmpty()) {
                continue;
            }
            dates.add(LocalDate.parse(value));
        }
        return Collections.unmodifiableSet(dates);
    }

    private LocalDate resolveFundSubscriptionTradeDate(String subscriptionTimeSlot, LocalDateTime tradeAt) {
        LocalDateTime effectiveTradeAt = tradeAt == null ? LocalDateTime.now() : tradeAt;
        LocalDate candidateDate = effectiveTradeAt.toLocalDate();
        if (isNonTradingDay(candidateDate) || isAfterFundCutoff(subscriptionTimeSlot, effectiveTradeAt.toLocalTime())) {
            candidateDate = candidateDate.plusDays(1);
        }
        return nextTradingDay(candidateDate);
    }

    private boolean isAfterFundCutoff(String subscriptionTimeSlot, LocalTime tradeTime) {
        if (SUBSCRIPTION_TIME_SLOT_AFTER_1500.equals(subscriptionTimeSlot)) {
            return true;
        }
        if (SUBSCRIPTION_TIME_SLOT_BEFORE_1500.equals(subscriptionTimeSlot)) {
            return false;
        }
        LocalTime effectiveTradeTime = tradeTime == null ? LocalTime.now() : tradeTime;
        return !effectiveTradeTime.isBefore(FUND_SUBSCRIPTION_CUTOFF_TIME);
    }

    private InvestmentPriceQuoteEntity resolveImmediateSettlementQuote(
        InvestmentProductEntity product,
        JsonNode baseInfo,
        LocalDate appliedDate,
        LocalDateTime syncedAt
    ) {
        upsertFundQuoteFromBaseInfo(product, baseInfo, syncedAt);
        return findSettlementQuoteByProductAndDate(product.getId(), appliedDate);
    }

    private void upsertFundQuoteFromBaseInfo(InvestmentProductEntity product, JsonNode baseInfo, LocalDateTime syncedAt) {
        if (product == null || baseInfo == null) {
            return;
        }
        BigDecimal latestPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        LocalDate quoteDate = safeDate(baseInfo.path("FSRQ").asText(null));
        if (latestPrice == null || quoteDate == null) {
            return;
        }

        InvestmentPriceQuoteEntity existingQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .eq(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .last("LIMIT 1"));
        InvestmentPriceQuoteEntity previousQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .lt(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .orderByDesc(InvestmentPriceQuoteEntity::getQuoteDate)
            .last("LIMIT 1"));

        BigDecimal changeRate = safeDecimal(baseInfo.path("RZDF").asText(null));
        BigDecimal preClosePrice = resolveFundPreClose(existingQuote, previousQuote, latestPrice, changeRate);
        BigDecimal changeAmount = preClosePrice == null
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : latestPrice.subtract(preClosePrice).setScale(6, RoundingMode.HALF_UP);
        BigDecimal normalizedChangeRate = changeRate != null
            ? changeRate.setScale(4, RoundingMode.HALF_UP)
            : rate(changeAmount, preClosePrice);

        saveFundQuote(product.getId(), existingQuote, quoteDate, syncedAt, latestPrice, preClosePrice, changeAmount, normalizedChangeRate);
    }

    private LocalDate addTradingDays(LocalDate baseDate, int tradingDays) {
        LocalDate result = nextTradingDay(baseDate);
        int remainingDays = Math.max(tradingDays, 0);
        while (remainingDays > 0) {
            result = nextTradingDay(result.plusDays(1));
            remainingDays--;
        }
        return result;
    }

    private LocalDate nextTradingDay(LocalDate date) {
        LocalDate result = date;
        while (isNonTradingDay(result)) {
            result = result.plusDays(1);
        }
        return result;
    }

    private boolean isNonTradingDay(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> marketClosedDates.contains(date);
        };
    }

    private void deductFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        BigDecimal nextBalance = currentBalance.subtract(defaultZero(amount).setScale(2, RoundingMode.HALF_UP));
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("资金账户余额不足");
        }
        account.setCurrentBalance(nextBalance);
        accountMapper.updateById(account);
    }

    private void creditFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal netAmount = defaultZero(amount).setScale(2, RoundingMode.HALF_UP);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("到账金额不能小于0");
        }
        BigDecimal currentBalance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        account.setCurrentBalance(currentBalance.add(netAmount).setScale(2, RoundingMode.HALF_UP));
        accountMapper.updateById(account);
    }

    private InvestmentPositionEntity requirePosition(InvestmentTransactionRequest request) {
        if (request.getPositionId() == null) {
            throw new IllegalArgumentException("请选择投资持仓");
        }
        InvestmentPositionEntity position = positionMapper.selectById(request.getPositionId());
        if (position == null || !request.getUserId().equals(position.getUserId())) {
            throw new IllegalArgumentException("投资持仓不存在");
        }
        if (isPendingFundSubscription(position)) {
            throw new IllegalArgumentException("基金申购待确认，暂不支持加仓或减仓");
        }
        if (!request.getAccountId().equals(position.getAccountId())) {
            throw new IllegalArgumentException("投资账户不匹配");
        }
        if (!request.getProductId().equals(position.getProductId())) {
            throw new IllegalArgumentException("投资产品不匹配");
        }
        return position;
    }

    private void applyBuyTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount
    ) {
        BigDecimal nextHoldingQuantity = defaultZero(position.getHoldingQuantity()).add(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextAvailableQuantity = defaultZero(position.getAvailableQuantity()).add(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).add(amount).add(feeAmount).add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        position.setHoldingQuantity(nextHoldingQuantity);
        position.setAvailableQuantity(nextAvailableQuantity);
        position.setCostAmount(nextCostAmount);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            position.setCurrentPrice(price);
        }
        recalculatePositionMetrics(position, ACTIVE_STATUS);
    }

    private void applySellTransaction(
        InvestmentPositionEntity position,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal taxAmount
    ) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal availableQuantity = defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (quantity.compareTo(holdingQuantity) > 0 || quantity.compareTo(availableQuantity) > 0) {
            throw new IllegalArgumentException("卖出数量不能超过当前持仓");
        }

        BigDecimal nextHoldingQuantity = holdingQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextAvailableQuantity = availableQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal soldCostAmount = avgCostPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousCumulativeProfit = defaultZero(position.getCumulativeProfit()).setScale(2, RoundingMode.HALF_UP);
        if (nextCostAmount.compareTo(BigDecimal.ZERO) < 0) {
            nextCostAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        position.setHoldingQuantity(nextHoldingQuantity);
        position.setAvailableQuantity(nextAvailableQuantity);
        position.setCostAmount(nextCostAmount);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            position.setCurrentPrice(price);
        }
        recalculatePositionMetrics(position, nextHoldingQuantity.compareTo(BigDecimal.ZERO) == 0 ? "closed" : ACTIVE_STATUS);

        BigDecimal realizedProfit = amount.subtract(feeAmount).subtract(taxAmount).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        position.setCumulativeProfit(previousCumulativeProfit.add(realizedProfit).setScale(2, RoundingMode.HALF_UP));
        position.setCumulativeProfitRate(rate(position.getCumulativeProfit(), position.getCostAmount()));
    }

    private void freezePendingFundSellQuantity(InvestmentPositionEntity position, BigDecimal quantity) {
        BigDecimal availableQuantity = defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal frozenQuantity = defaultZero(position.getFrozenQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (quantity.compareTo(availableQuantity) > 0) {
            throw new IllegalArgumentException("赎回份额不能超过可用持仓");
        }
        position.setAvailableQuantity(availableQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP));
        position.setFrozenQuantity(frozenQuantity.add(quantity).setScale(6, RoundingMode.HALF_UP));
        position.setLastSyncedAt(LocalDateTime.now());
    }

    private void settlePendingFundSellTransaction(
        InvestmentPositionEntity position,
        InvestmentTransactionEntity transaction,
        BigDecimal confirmedPrice
    ) {
        BigDecimal quantity = defaultZero(transaction.getQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal frozenQuantity = defaultZero(position.getFrozenQuantity()).setScale(6, RoundingMode.HALF_UP);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("赎回份额必须大于0");
        }
        if (quantity.compareTo(holdingQuantity) > 0 || quantity.compareTo(frozenQuantity) > 0) {
            throw new IllegalArgumentException("待确认赎回份额超过当前冻结持仓");
        }

        BigDecimal actualAmount = quantity.multiply(confirmedPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeAmount = defaultZero(transaction.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedAmount = defaultZero(transaction.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (estimatedAmount.compareTo(BigDecimal.ZERO) > 0 && feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal effectiveFeeRate = feeAmount.divide(estimatedAmount, 10, RoundingMode.HALF_UP);
            feeAmount = actualAmount.multiply(effectiveFeeRate).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal taxAmount = defaultZero(transaction.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextHoldingQuantity = holdingQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal nextFrozenQuantity = frozenQuantity.subtract(quantity).setScale(6, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = defaultZero(position.getAvgCostPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal soldCostAmount = avgCostPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nextCostAmount = defaultZero(position.getCostAmount()).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousCumulativeProfit = defaultZero(position.getCumulativeProfit()).setScale(2, RoundingMode.HALF_UP);

        if (nextCostAmount.compareTo(BigDecimal.ZERO) < 0) {
            nextCostAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        position.setHoldingQuantity(nextHoldingQuantity);
        position.setFrozenQuantity(nextFrozenQuantity);
        position.setCostAmount(nextCostAmount);
        position.setCurrentPrice(confirmedPrice.setScale(6, RoundingMode.HALF_UP));
        recalculatePositionMetrics(position, nextHoldingQuantity.compareTo(BigDecimal.ZERO) == 0 ? "closed" : ACTIVE_STATUS);

        BigDecimal realizedProfit = actualAmount.subtract(feeAmount).subtract(taxAmount).subtract(soldCostAmount).setScale(2, RoundingMode.HALF_UP);
        position.setCumulativeProfit(previousCumulativeProfit.add(realizedProfit).setScale(2, RoundingMode.HALF_UP));
        position.setCumulativeProfitRate(rate(position.getCumulativeProfit(), position.getCostAmount()));
        transaction.setAmount(actualAmount);
        transaction.setFeeAmount(feeAmount);
        transaction.setPrice(confirmedPrice.setScale(6, RoundingMode.HALF_UP));
    }

    private void recalculatePositionMetrics(InvestmentPositionEntity position, String status) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = defaultZero(position.getCurrentPrice()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal marketValue = holdingQuantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgCostPrice = holdingQuantity.compareTo(BigDecimal.ZERO) > 0
            ? costAmount.divide(holdingQuantity, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cumulativeProfit = defaultZero(position.getCumulativeProfit()).setScale(2, RoundingMode.HALF_UP);

        position.setHoldingQuantity(holdingQuantity);
        position.setAvailableQuantity(defaultZero(position.getAvailableQuantity()).setScale(6, RoundingMode.HALF_UP));
        position.setCostAmount(costAmount);
        position.setAvgCostPrice(avgCostPrice);
        position.setMarketValue(marketValue);
        position.setHoldingProfit(holdingProfit);
        position.setHoldingProfitRate(rate(holdingProfit, costAmount));
        position.setCumulativeProfit(cumulativeProfit);
        position.setCumulativeProfitRate(rate(cumulativeProfit, costAmount));
        position.setStatus(status);
        if (!isPendingFundSubscription(position)) {
            position.setSubscriptionStatus(SUBSCRIPTION_STATUS_CONFIRMED);
            position.setSubscriptionConfirmedAt(LocalDateTime.now());
        }
        position.setLastSyncedAt(LocalDateTime.now());
    }

    private void syncInvestmentAccountBalance(Long userId, Long accountId) {
        if (accountId == null) {
            return;
        }
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !userId.equals(account.getUserId())) {
            return;
        }
        AccountTypeEntity accountType = accountTypeMapper.selectById(account.getAccountTypeId());
        if (accountType == null || !POSITION_ACCOUNT_TYPE_CODES.contains(accountType.getCode())) {
            return;
        }
        BigDecimal marketValue = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
                .eq(InvestmentPositionEntity::getUserId, userId)
                .eq(InvestmentPositionEntity::getAccountId, accountId)
                .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS))
            .stream()
            .map(InvestmentPositionEntity::getMarketValue)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        account.setCurrentBalance(marketValue);
        accountMapper.updateById(account);
    }

    private InvestmentProductEntity requireProduct(Long productId) {
        InvestmentProductEntity product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("投资产品不存在");
        }
        return product;
    }

    private List<InvestmentPositionResponse> toPositionResponses(List<InvestmentPositionEntity> positions) {
        if (positions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, InvestmentProductEntity> products = productMapper.selectByIds(positions.stream().map(InvestmentPositionEntity::getProductId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        Map<Long, AccountEntity> accounts = accountMapper.selectByIds(positions.stream().map(InvestmentPositionEntity::getAccountId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        return positions.stream().map(item -> toPositionResponse(item, products.get(item.getProductId()), accounts.get(item.getAccountId()))).toList();
    }

    private InvestmentPositionResponse toPositionResponse(InvestmentPositionEntity entity, InvestmentProductEntity product, AccountEntity account) {
        InvestmentPositionResponse response = new InvestmentPositionResponse();
        boolean dayProfitVisible = hasTodayDayProfit(entity);
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setAccountId(entity.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setProductId(entity.getProductId());
        if (product != null) {
            response.setProductType(product.getProductType());
            response.setProductName(product.getName());
            response.setProductSymbol(product.getSymbol());
            response.setMarket(product.getMarket());
            response.setUnitName(product.getUnitName());
            response.setCurrencyCode(product.getCurrencyCode());
        }
        response.setHoldingQuantity(entity.getHoldingQuantity());
        response.setAvailableQuantity(entity.getAvailableQuantity());
        response.setFrozenQuantity(entity.getFrozenQuantity());
        response.setCostAmount(entity.getCostAmount());
        response.setAvgCostPrice(entity.getAvgCostPrice());
        response.setCurrentPrice(entity.getCurrentPrice());
        response.setMarketValue(entity.getMarketValue());
        response.setDayProfit(dayProfitVisible ? entity.getDayProfit() : null);
        response.setDayProfitRate(dayProfitVisible ? entity.getDayProfitRate() : null);
        response.setHoldingProfit(entity.getHoldingProfit());
        response.setHoldingProfitRate(entity.getHoldingProfitRate());
        response.setCumulativeProfit(entity.getCumulativeProfit());
        response.setCumulativeProfitRate(entity.getCumulativeProfitRate());
        response.setIncludeInNetWorth(entity.getIncludeInNetWorth());
        response.setStatus(entity.getStatus());
        response.setLastSyncedAt(entity.getLastSyncedAt());
        response.setSubscriptionStatus(entity.getSubscriptionStatus());
        response.setSubscriptionAppliedDate(entity.getSubscriptionAppliedDate());
        response.setSubscriptionExpectedConfirmDate(entity.getSubscriptionExpectedConfirmDate());
        response.setSubscriptionConfirmedAt(entity.getSubscriptionConfirmedAt());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private List<InvestmentTransactionResponse> toTransactionResponses(List<InvestmentTransactionEntity> transactions) {
        if (transactions.isEmpty()) return Collections.emptyList();
        Set<Long> productIds = transactions.stream().map(InvestmentTransactionEntity::getProductId).collect(Collectors.toSet());
        Set<Long> accountIds = transactions.stream().map(InvestmentTransactionEntity::getAccountId).collect(Collectors.toSet());
        Map<Long, InvestmentProductEntity> products = productMapper.selectByIds(productIds).stream().collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        Map<Long, AccountEntity> accounts = accountMapper.selectByIds(accountIds).stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        return transactions.stream().map(item -> toTransactionResponse(item, products.get(item.getProductId()), accounts.get(item.getAccountId()))).toList();
    }

    private List<InvestmentTransactionResponse> inferInitialTransaction(Long userId, Long accountId, Long positionId) {
        InvestmentPositionEntity position = positionMapper.selectById(positionId);
        if (position == null || (userId != null && !userId.equals(position.getUserId())) || (accountId != null && !accountId.equals(position.getAccountId()))) {
            return Collections.emptyList();
        }
        if (isPendingFundSubscription(position)) {
            return Collections.emptyList();
        }
        InvestmentTransactionResponse response = new InvestmentTransactionResponse();
        response.setId(-position.getId());
        response.setTransactionNo("INIT-" + position.getId());
        response.setUserId(position.getUserId());
        response.setAccountId(position.getAccountId());
        AccountEntity account = accountMapper.selectById(position.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setPositionId(position.getId());
        response.setProductId(position.getProductId());
        InvestmentProductEntity product = productMapper.selectById(position.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setTradeType("buy");
        response.setQuantity(position.getHoldingQuantity());
        response.setPrice(position.getAvgCostPrice());
        response.setAmount(position.getCostAmount());
        response.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        response.setFundingAccountId(null);
        response.setTradeAt(position.getCreatedAt());
        response.setStatus(NORMAL_STATUS);
        response.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
        response.setSettlementAppliedDate(null);
        response.setSettlementExpectedDate(null);
        response.setSettlementConfirmedAt(position.getCreatedAt());
        response.setRemark("初始买入");
        response.setCreatedAt(position.getCreatedAt());
        response.setUpdatedAt(position.getUpdatedAt());
        return List.of(response);
    }

    private InvestmentTransactionResponse toTransactionResponse(InvestmentTransactionEntity entity, InvestmentProductEntity product, AccountEntity account) {
        InvestmentTransactionResponse response = new InvestmentTransactionResponse();
        response.setId(entity.getId());
        response.setTransactionNo(entity.getTransactionNo());
        response.setUserId(entity.getUserId());
        response.setAccountId(entity.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        response.setPositionId(entity.getPositionId());
        response.setProductId(entity.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setTradeType(entity.getTradeType());
        response.setQuantity(entity.getQuantity());
        response.setPrice(entity.getPrice());
        response.setAmount(entity.getAmount());
        response.setFeeAmount(entity.getFeeAmount());
        response.setTaxAmount(entity.getTaxAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setFundingAccountId(entity.getFundingAccountId());
        response.setTradeAt(entity.getTradeAt());
        response.setStatus(entity.getStatus());
        response.setSettlementStatus(entity.getSettlementStatus());
        response.setSettlementAppliedDate(entity.getSettlementAppliedDate());
        response.setSettlementExpectedDate(entity.getSettlementExpectedDate());
        response.setSettlementConfirmedAt(entity.getSettlementConfirmedAt());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private InvestmentAutoInvestPlanResponse toAutoInvestPlanResponse(InvestmentAutoInvestPlanEntity entity) {
        InvestmentAutoInvestPlanResponse response = new InvestmentAutoInvestPlanResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setAccountId(entity.getAccountId());
        response.setPositionId(entity.getPositionId());
        response.setProductId(entity.getProductId());
        response.setFundingAccountId(entity.getFundingAccountId());
        response.setFrequency(entity.getFrequency());
        response.setAmount(entity.getAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setNextExecuteDate(entity.getNextExecuteDate());
        response.setLastExecutedAt(entity.getLastExecutedAt());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        AccountEntity account = accountMapper.selectById(entity.getAccountId());
        response.setAccountName(account == null ? null : account.getName());
        AccountEntity fundingAccount = accountMapper.selectById(entity.getFundingAccountId());
        response.setFundingAccountName(fundingAccount == null ? null : fundingAccount.getName());
        InvestmentProductEntity product = productMapper.selectById(entity.getProductId());
        if (product != null) {
            response.setProductName(product.getName());
            response.setProductSymbol(product.getSymbol());
        }
        return response;
    }

    private InvestmentProductResponse toProductResponse(InvestmentProductEntity entity) {
        InvestmentProductResponse response = new InvestmentProductResponse();
        response.setId(entity.getId());
        response.setProductType(entity.getProductType());
        response.setMarket(entity.getMarket());
        response.setExchangeCode(entity.getExchangeCode());
        response.setSymbol(entity.getSymbol());
        response.setName(entity.getName());
        response.setShortName(entity.getShortName());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setUnitName(entity.getUnitName());
        response.setPricePrecision(entity.getPricePrecision());
        response.setStableDividend(entity.getStableDividend());
        response.setPredictedAnnualDividendPerUnit(entity.getPredictedAnnualDividendPerUnit());
        response.setDividendStableYears(entity.getDividendStableYears());
        response.setDividendLastPaidDate(entity.getDividendLastPaidDate());
        response.setDividendDataSource(entity.getDividendDataSource());
        response.setDividendEvaluatedAt(entity.getDividendEvaluatedAt());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private List<InvestmentPositionEntity> listActiveFundPositions() {
        return positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS));
    }

    private Map<Long, InvestmentProductEntity> loadFundProducts(List<InvestmentPositionEntity> positions) {
        return productMapper.selectByIds(
                positions.stream().map(InvestmentPositionEntity::getProductId).collect(Collectors.toSet())
            ).stream()
            .filter(product -> product != null
                && FUND_PRODUCT_TYPE.equals(product.getProductType())
                && StringUtils.hasText(product.getSymbol()))
            .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
    }

    private Map<Long, List<InvestmentPositionEntity>> groupPositionsByProduct(
        List<InvestmentPositionEntity> positions,
        Map<Long, InvestmentProductEntity> products
    ) {
        return positions.stream()
            .filter(position -> products.containsKey(position.getProductId()))
            .collect(Collectors.groupingBy(InvestmentPositionEntity::getProductId));
    }

    private int syncFundProfitForProduct(
        InvestmentProductEntity product,
        List<InvestmentPositionEntity> positions,
        Map<Long, Long> accountUsers
    ) {
        FundQuoteSnapshot snapshot = fetchAndSaveLatestFundQuote(product);
        if (snapshot == null) {
            return 0;
        }

        int updatedCount = 0;
        for (InvestmentPositionEntity position : positions) {
            if (isPendingFundSubscription(position)) {
                updatePendingFundPositionSnapshot(position, snapshot.latestPrice(), snapshot.syncedAt());
            } else {
                applyFundQuoteToPosition(position, snapshot.latestPrice(), snapshot.preClosePrice(), snapshot.syncedAt());
            }
            positionMapper.updateById(position);
            accountUsers.put(position.getAccountId(), position.getUserId());
            updatedCount++;
        }
        return updatedCount;
    }

    private int syncFundDividendPlansForProduct(InvestmentProductEntity product) {
        List<InvestmentDividendPlanEntity> remotePlans = fetchFundDividendPlans(product);
        int changedCount = 0;
        for (InvestmentDividendPlanEntity remotePlan : remotePlans) {
            InvestmentDividendPlanEntity existing = dividendPlanMapper.selectOne(new LambdaQueryWrapper<InvestmentDividendPlanEntity>()
                .eq(InvestmentDividendPlanEntity::getProductId, remotePlan.getProductId())
                .eq(InvestmentDividendPlanEntity::getDividendYear, remotePlan.getDividendYear())
                .eq(InvestmentDividendPlanEntity::getPayDate, remotePlan.getPayDate())
                .last("LIMIT 1"));

            if (existing == null) {
                dividendPlanMapper.insert(remotePlan);
                changedCount++;
                continue;
            }

            boolean changed = mergeFundDividendPlan(existing, remotePlan);
            if (changed) {
                dividendPlanMapper.updateById(existing);
                changedCount++;
            }
        }
        evaluateDividendProfile(product);
        if (product.getId() != null) {
            productMapper.updateById(product);
        }
        return changedCount;
    }

    private int settlePendingFundTradesForProduct(
        InvestmentProductEntity product,
        List<InvestmentPositionEntity> positions,
        Map<Long, Long> accountUsers,
        LocalDate settlementDate
    ) {
        FundQuoteSnapshot snapshot = fetchAndSaveLatestFundQuote(product);
        if (snapshot == null) {
            return 0;
        }

        int updatedCount = 0;
        for (InvestmentPositionEntity position : positions) {
            InvestmentPriceQuoteEntity appliedQuote = findSettlementQuoteByProductAndDate(product.getId(), position.getSubscriptionAppliedDate());
            if (shouldConfirmFundSubscription(position, settlementDate) && appliedQuote != null) {
                confirmPendingFundSubscription(position, appliedQuote, snapshot.syncedAt());
                applyFundQuoteToPosition(position, snapshot.latestPrice(), snapshot.preClosePrice(), snapshot.syncedAt());
                positionMapper.updateById(position);
                accountUsers.put(position.getAccountId(), position.getUserId());
                updatedCount++;
            }
        }
        return updatedCount;
    }

    private int settlePendingFundTransactionsForProduct(
        InvestmentProductEntity product,
        List<InvestmentTransactionEntity> transactions,
        Map<Long, Long> accountUsers,
        LocalDate settlementDate
    ) {
        FundQuoteSnapshot snapshot = fetchAndSaveLatestFundQuote(product);
        if (snapshot == null) {
            return 0;
        }

        int updatedCount = 0;
        for (InvestmentTransactionEntity transaction : transactions) {
            InvestmentPriceQuoteEntity appliedQuote = findSettlementQuoteByProductAndDate(product.getId(), transaction.getSettlementAppliedDate());
            if (!shouldSettlePendingFundTransaction(transaction, settlementDate) || appliedQuote == null) {
                continue;
            }
            InvestmentPositionEntity position = positionMapper.selectById(transaction.getPositionId());
            if (position == null) {
                continue;
            }

            BigDecimal confirmedPrice = resolveConfirmedFundPrice(appliedQuote);
            if (confirmedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if ("buy".equals(transaction.getTradeType())) {
                BigDecimal quantity = scaleFundQuantity(
                    defaultZero(transaction.getAmount()).setScale(2, RoundingMode.HALF_UP)
                        .divide(confirmedPrice, 6, RoundingMode.HALF_UP)
                );
                applyBuyTransaction(
                    position,
                    quantity,
                    confirmedPrice,
                    defaultZero(transaction.getAmount()).setScale(2, RoundingMode.HALF_UP),
                    defaultZero(transaction.getFeeAmount()).setScale(2, RoundingMode.HALF_UP),
                    defaultZero(transaction.getTaxAmount()).setScale(2, RoundingMode.HALF_UP)
                );
                transaction.setQuantity(quantity);
                transaction.setPrice(confirmedPrice);
            } else if ("sell".equals(transaction.getTradeType())) {
                settlePendingFundSellTransaction(position, transaction, confirmedPrice);
                if (transaction.getFundingAccountId() != null) {
                    AccountEntity fundingAccount = requireCashFundingAccount(transaction.getUserId(), transaction.getFundingAccountId());
                    creditFundingAccount(
                        fundingAccount,
                        defaultZero(transaction.getAmount())
                            .subtract(defaultZero(transaction.getFeeAmount()))
                            .subtract(defaultZero(transaction.getTaxAmount()))
                    );
                }
            } else {
                continue;
            }

            transaction.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
            transaction.setSettlementConfirmedAt(snapshot.syncedAt());
            applyFundQuoteToPosition(position, snapshot.latestPrice(), snapshot.preClosePrice(), snapshot.syncedAt());
            positionMapper.updateById(position);
            transactionMapper.updateById(transaction);
            accountUsers.put(position.getAccountId(), position.getUserId());
            updatedCount++;
        }
        return updatedCount;
    }

    private FundQuoteSnapshot fetchAndSaveLatestFundQuote(InvestmentProductEntity product) {
        JsonNode baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        BigDecimal latestPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        LocalDate quoteDate = safeDate(baseInfo.path("FSRQ").asText(null));
        if (latestPrice == null || quoteDate == null) {
            log.warn("基金净值同步跳过：symbol={} 未返回有效净值或净值日期", product.getSymbol());
            return null;
        }

        InvestmentPriceQuoteEntity existingQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .eq(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .last("LIMIT 1"));
        InvestmentPriceQuoteEntity previousQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .lt(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .orderByDesc(InvestmentPriceQuoteEntity::getQuoteDate)
            .last("LIMIT 1"));

        BigDecimal changeRate = safeDecimal(baseInfo.path("RZDF").asText(null));
        BigDecimal preClosePrice = resolveFundPreClose(existingQuote, previousQuote, latestPrice, changeRate);
        BigDecimal changeAmount = preClosePrice == null
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : latestPrice.subtract(preClosePrice).setScale(6, RoundingMode.HALF_UP);
        BigDecimal normalizedChangeRate = changeRate != null
            ? changeRate.setScale(4, RoundingMode.HALF_UP)
            : rate(changeAmount, preClosePrice);
        LocalDateTime syncedAt = LocalDateTime.now();

        saveFundQuote(product.getId(), existingQuote, quoteDate, syncedAt, latestPrice, preClosePrice, changeAmount, normalizedChangeRate);
        return new FundQuoteSnapshot(quoteDate, latestPrice, preClosePrice, syncedAt);
    }

    private void saveFundQuote(
        Long productId,
        InvestmentPriceQuoteEntity existingQuote,
        LocalDate quoteDate,
        LocalDateTime syncedAt,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        BigDecimal changeAmount,
        BigDecimal changeRate
    ) {
        InvestmentPriceQuoteEntity quote = existingQuote == null ? new InvestmentPriceQuoteEntity() : existingQuote;
        quote.setProductId(productId);
        quote.setQuoteDate(quoteDate);
        quote.setQuoteTime(syncedAt);
        quote.setClosePrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        quote.setLatestPrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        quote.setPreClosePrice(preClosePrice == null ? null : preClosePrice.setScale(6, RoundingMode.HALF_UP));
        quote.setChangeAmount(changeAmount.setScale(6, RoundingMode.HALF_UP));
        quote.setChangeRate(changeRate.setScale(4, RoundingMode.HALF_UP));
        quote.setSource(FUND_QUOTE_SOURCE);
        if (existingQuote == null) {
            priceQuoteMapper.insert(quote);
        } else {
            priceQuoteMapper.updateById(quote);
        }
    }

    private void applyFundQuoteToPosition(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal marketValue = holdingQuantity.multiply(latestPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousMarketValue = preClosePrice == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : holdingQuantity.multiply(preClosePrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dayProfit = preClosePrice == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : marketValue.subtract(previousMarketValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);

        position.setCurrentPrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        position.setMarketValue(marketValue);
        position.setDayProfit(dayProfit);
        position.setDayProfitRate(rate(dayProfit, previousMarketValue));
        position.setHoldingProfit(holdingProfit);
        position.setHoldingProfitRate(rate(holdingProfit, costAmount));
        position.setLastSyncedAt(syncedAt);
    }

    private void updatePendingFundPositionSnapshot(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        LocalDateTime syncedAt
    ) {
        position.setCurrentPrice(defaultZero(latestPrice).setScale(6, RoundingMode.HALF_UP));
        position.setDayProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setDayProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        position.setHoldingProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setHoldingProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        position.setMarketValue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setLastSyncedAt(syncedAt);
    }

    private boolean shouldConfirmFundSubscription(InvestmentPositionEntity position, LocalDate settlementDate) {
        if (!isPendingFundSubscription(position) || position.getSubscriptionAppliedDate() == null) {
            return false;
        }
        return !settlementDate.isBefore(position.getSubscriptionAppliedDate());
    }

    private boolean shouldSettlePendingFundTransaction(InvestmentTransactionEntity transaction, LocalDate settlementDate) {
        if (transaction == null || !SETTLEMENT_STATUS_PENDING.equals(transaction.getSettlementStatus())) {
            return false;
        }
        if (transaction.getSettlementAppliedDate() == null) {
            return false;
        }
        return !settlementDate.isBefore(transaction.getSettlementAppliedDate());
    }

    private InvestmentPriceQuoteEntity findSettlementQuoteByProductAndDate(Long productId, LocalDate quoteDate) {
        if (productId == null || quoteDate == null) {
            return null;
        }
        return priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, productId)
            .ge(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .orderByAsc(InvestmentPriceQuoteEntity::getQuoteDate)
            .last("LIMIT 1"));
    }

    private BigDecimal resolveConfirmedFundPrice(InvestmentPriceQuoteEntity appliedQuote) {
        return defaultZero(
            appliedQuote == null
                ? null
                : (appliedQuote.getClosePrice() != null ? appliedQuote.getClosePrice() : appliedQuote.getLatestPrice())
        ).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleFundQuantity(BigDecimal quantity) {
        return defaultZero(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    private void confirmPendingFundSubscription(
        InvestmentPositionEntity position,
        InvestmentPriceQuoteEntity appliedQuote,
        LocalDateTime syncedAt
    ) {
        BigDecimal confirmedPrice = resolveConfirmedFundPrice(appliedQuote);
        if (confirmedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            updatePendingFundPositionSnapshot(position, confirmedPrice, syncedAt);
            return;
        }

        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal quantity = scaleFundQuantity(costAmount.divide(confirmedPrice, 6, RoundingMode.HALF_UP));
        position.setHoldingQuantity(quantity);
        position.setAvailableQuantity(quantity);
        position.setFrozenQuantity(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        position.setAvgCostPrice(confirmedPrice);
        position.setCurrentPrice(confirmedPrice);
        position.setMarketValue(costAmount);
        position.setDayProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setDayProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        position.setHoldingProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setHoldingProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        position.setSubscriptionStatus(SUBSCRIPTION_STATUS_CONFIRMED);
        position.setSubscriptionConfirmedAt(syncedAt);
        position.setLastSyncedAt(syncedAt);

        LocalDateTime tradeAt = position.getCreatedAt() != null ? position.getCreatedAt() : syncedAt;
        createBuyTransaction(position, quantity, confirmedPrice, costAmount, "基金申购确认", tradeAt);
    }

    private BigDecimal resolveFundPreClose(
        InvestmentPriceQuoteEntity existingQuote,
        InvestmentPriceQuoteEntity previousQuote,
        BigDecimal latestPrice,
        BigDecimal changeRate
    ) {
        if (existingQuote != null && existingQuote.getPreClosePrice() != null) {
            return existingQuote.getPreClosePrice().setScale(6, RoundingMode.HALF_UP);
        }
        if (previousQuote != null) {
            BigDecimal previousPrice = previousQuote.getClosePrice() != null
                ? previousQuote.getClosePrice()
                : previousQuote.getLatestPrice();
            if (previousPrice != null) {
                return previousPrice.setScale(6, RoundingMode.HALF_UP);
            }
        }
        if (changeRate != null && latestPrice != null && changeRate.compareTo(BigDecimal.valueOf(-100)) > 0) {
            BigDecimal divisor = BigDecimal.ONE.add(changeRate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
            if (divisor.compareTo(BigDecimal.ZERO) != 0) {
                return latestPrice.divide(divisor, 6, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    private List<InvestmentDetailStatResponse> buildHoldingStats(InvestmentPositionResponse position) {
        if (SUBSCRIPTION_STATUS_PENDING.equals(position.getSubscriptionStatus())) {
            return List.of(
                stat("所属账户", blankToDash(position.getAccountName()), null),
                stat("申购金额", currencyText(position.getCostAmount(), 2), null),
                stat("申购状态", "待确认", null),
                stat("申请日期", position.getSubscriptionAppliedDate() == null ? "-" : position.getSubscriptionAppliedDate().toString(), null),
                stat("确认日期", "-", null),
                stat("确认后份额", "以确认净值为准", null)
            );
        }
        return List.of(
            stat("所属账户", blankToDash(position.getAccountName()), null),
            stat(
                "持仓数量",
                moneyText(position.getHoldingQuantity(), 2)
                    + " " + blankToDefault(position.getUnitName(), DEFAULT_UNIT_NAME)
                    + "（更新于 " + blankToDash(formatPositionSyncTime(position.getLastSyncedAt())) + "）",
                null
            ),
            stat("持仓成本价", priceText(position.getAvgCostPrice(), position.getProductType()), null),
            stat("当前市值", currencyText(position.getMarketValue(), 2), null),
            stat("持仓收益", currencyText(position.getHoldingProfit(), 2), tone(position.getHoldingProfit())),
            stat("收益率", percentText(position.getHoldingProfitRate()), tone(position.getHoldingProfitRate()))
        );
    }

    private void fillFundDetail(InvestmentAssetDetailResponse response, InvestmentProductEntity product) {
        JsonNode baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        JsonNode estimateInfo = fetchFundEstimateInfo(product.getSymbol());
        BigDecimal officialPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        BigDecimal cumulativePrice = safeDecimal(baseInfo.path("LJJZ").asText(null));
        BigDecimal latestPrice = officialPrice;
        BigDecimal changePercent = safeDecimal(baseInfo.path("RZDF").asText(null));
        String updatedAt = baseInfo.path("FSRQ").asText(null);

        response.setProductType("fund");
        response.setName(blankToDefault(baseInfo.path("SHORTNAME").asText(null), product.getName()));
        response.setSymbol(blankToDefault(baseInfo.path("FCODE").asText(null), product.getSymbol()));
        response.setLatestPrice(latestPrice);
        response.setChangePercent(changePercent);
        response.setUpdatedAt(updatedAt);
        response.setChartType("line");
        response.setFundRedeemFeeOptions(buildFundRedeemFeeOptions(product.getSymbol()));
        response.setSource("东方财富");
        response.setDescription("基金详情和累计净值走势来自东方财富公开接口。");
        response.setMarketStats(List.of(
            stat("资产类型", "基金", null),
            stat("基金代码", product.getSymbol(), null),
            stat("基金类型", blankToDash(baseInfo.path("FTYPE").asText(null)), null),
            stat("当前净值（单位净值）", latestPrice == null ? "-" : moneyText(latestPrice, 4), tone(changePercent)),
            stat("累计净值", blankToDash(baseInfo.path("LJJZ").asText(null)), null),
            stat("当日涨跌幅", percentText(changePercent), tone(changePercent)),
            stat("净值日期", blankToDash(updatedAt), null),
            stat("基金公司", blankToDash(baseInfo.path("JJGS").asText(null)), null),
            stat("申购状态", blankToDash(baseInfo.path("SGZT").asText(null)), null),
            stat("赎回状态", blankToDash(baseInfo.path("SHZT").asText(null)), null)
        ));
        response.setChartPoints(fetchFundTrendPoints(product.getSymbol(), latestPrice, cumulativePrice, updatedAt));
    }

    private void fillStockDetail(InvestmentAssetDetailResponse response, InvestmentProductEntity product) {
        String symbol = toTencentSymbol(product.getSymbol(), product.getExchangeCode());
        JsonNode quote = fetchTencentQuoteFields(symbol);
        BigDecimal latestPrice = safeDecimal(quote.path("price").asText(null));
        BigDecimal change = safeDecimal(quote.path("change").asText(null));
        BigDecimal changePercent = safeDecimal(quote.path("changePercent").asText(null));

        response.setProductType("stock");
        response.setName(blankToDefault(quote.path("name").asText(null), product.getName()));
        response.setSymbol(blankToDefault(quote.path("code").asText(null), product.getSymbol()));
        response.setLatestPrice(latestPrice);
        response.setChange(change);
        response.setChangePercent(changePercent);
        response.setUpdatedAt(formatTencentTime(quote.path("timeRaw").asText(null)));
        response.setChartType("candlestick");
        response.setSource("腾讯行情");
        response.setDescription("股票实时行情和日 K 走势来自腾讯公开行情接口。");
        response.setMarketStats(List.of(
            stat("资产类型", "股票", null),
            stat("股票代码", product.getSymbol(), null),
            stat("市场", blankToDash(product.getExchangeCode()), null),
            stat("当前净值（当前价）", latestPrice == null ? "-" : moneyText(latestPrice, 2), tone(change)),
            stat("涨跌额", moneyText(change, 2), tone(change)),
            stat("涨跌幅", percentText(changePercent), tone(changePercent)),
            stat("今开", moneyText(safeDecimal(quote.path("open").asText(null)), 2), null),
            stat("昨收", moneyText(safeDecimal(quote.path("prevClose").asText(null)), 2), null),
            stat("最高", moneyText(safeDecimal(quote.path("high").asText(null)), 2), null),
            stat("最低", moneyText(safeDecimal(quote.path("low").asText(null)), 2), null),
            stat("成交量（手）", blankToDash(quote.path("volume").asText(null)), null),
            stat("换手率", percentText(safeDecimal(quote.path("turnoverRate").asText(null))), null),
            stat("市盈率", blankToDash(quote.path("pe").asText(null)), null),
            stat("更新时间", blankToDash(response.getUpdatedAt()), null)
        ));
        response.setChartPoints(fetchStockKlinePoints(symbol));
    }

    private JsonNode fetchFundBaseInfo(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fundmobapi.eastmoney.com/FundMApi/FundBaseTypeInformation.ashx?FCODE=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0"
                ))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return objectMapper.readTree(new String(httpResponse.body(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode fetchFundEstimateInfo(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fund.eastmoney.com/data/funddataforgznew.aspx?fc=" + URLEncoder.encode(code, StandardCharsets.UTF_8) + "&t=basewap"
                ))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return objectMapper.readTree(extractJsonpObject(new String(httpResponse.body(), StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String fetchFundFeePage(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fundf10.eastmoney.com/jjfl_" + URLEncoder.encode(code, StandardCharsets.UTF_8) + ".html"
                ))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new String(httpResponse.body(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private BigDecimal calculateFundSellFeeAmount(
        InvestmentPositionEntity position,
        InvestmentProductEntity product,
        BigDecimal quantity,
        LocalDate appliedDate,
        BigDecimal estimatedPrice
    ) {
        if (product == null || !StringUtils.hasText(product.getSymbol())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<FundRedeemFeeRule> rules = fetchFundRedeemFeeRules(product.getSymbol());
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("暂未获取到基金赎回费率，请稍后再试");
        }

        List<FundHoldingLot> availableLots = buildAvailableFundHoldingLots(position);
        if (availableLots.isEmpty()) {
            throw new IllegalArgumentException("当前基金持仓不足，暂无法计算赎回手续费");
        }

        BigDecimal remainingQuantity = scaleFundQuantity(quantity);
        BigDecimal totalFeeAmount = BigDecimal.ZERO;
        BigDecimal normalizedPrice = defaultZero(estimatedPrice).setScale(6, RoundingMode.HALF_UP);
        for (FundHoldingLot lot : availableLots) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal lotQuantity = lot.quantity().min(remainingQuantity).setScale(6, RoundingMode.HALF_UP);
            if (lotQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal feeRate = resolveFundRedeemFeeRate(rules, resolveFundHoldingDays(lot.acquiredDate(), appliedDate));
            BigDecimal lotFeeAmount = lotQuantity.multiply(normalizedPrice).multiply(feeRate);
            totalFeeAmount = totalFeeAmount.add(lotFeeAmount);
            remainingQuantity = remainingQuantity.subtract(lotQuantity).setScale(6, RoundingMode.HALF_UP);
        }

        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("基金持仓份额数据异常，暂无法计算赎回手续费");
        }
        return totalFeeAmount.setScale(2, RoundingMode.HALF_UP);
    }

    private List<InvestmentFundRedeemFeeOptionResponse> buildFundRedeemFeeOptions(String code) {
        return fetchFundRedeemFeeRules(code).stream()
            .map(rule -> {
                InvestmentFundRedeemFeeOptionResponse response = new InvestmentFundRedeemFeeOptionResponse();
                response.setLabel(rule.label());
                response.setFeeRate(defaultZero(rule.feeRate()).setScale(4, RoundingMode.HALF_UP));
                return response;
            })
            .toList();
    }

    private List<FundRedeemFeeRule> fetchFundRedeemFeeRules(String code) {
        String body = fetchFundFeePage(code);
        if (!StringUtils.hasText(body)) {
            return Collections.emptyList();
        }

        Matcher tableMatcher = Pattern.compile(
                "赎回费率.*?<table[^>]*>(.*?)</table>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            )
            .matcher(body);
        if (!tableMatcher.find()) {
            return Collections.emptyList();
        }

        List<FundRedeemFeeRule> rules = new ArrayList<>();
        Matcher rowMatcher = Pattern.compile(
                "<tr>\\s*<td[^>]*>(.*?)</td>\\s*<td[^>]*>(.*?)</td>\\s*</tr>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            )
            .matcher(tableMatcher.group(1));
        while (rowMatcher.find()) {
            FundRedeemFeeRule rule = parseFundRedeemFeeRule(
                normalizeFundFeeText(rowMatcher.group(1)),
                normalizeFundFeeText(rowMatcher.group(2))
            );
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules.stream()
            .sorted(Comparator.comparingInt(FundRedeemFeeRule::minHoldingDaysInclusive))
            .toList();
    }

    private FundRedeemFeeRule parseFundRedeemFeeRule(String holdingPeriodText, String feeRateText) {
        if (!StringUtils.hasText(holdingPeriodText) || !StringUtils.hasText(feeRateText)) {
            return null;
        }

        BigDecimal feeRateValue;
        if (feeRateText.contains("%")) {
            BigDecimal percent = safeDecimal(feeRateText.replace("%", ""));
            if (percent == null) {
                return null;
            }
            feeRateValue = percent.movePointLeft(2);
        } else {
            feeRateValue = safeDecimal(feeRateText);
            if (feeRateValue == null) {
                return null;
            }
        }

        Matcher lessThanMatcher = Pattern.compile("小于(\\d+)天").matcher(holdingPeriodText);
        if (lessThanMatcher.find()) {
            return new FundRedeemFeeRule(holdingPeriodText, 0, Integer.parseInt(lessThanMatcher.group(1)), feeRateValue);
        }

        Matcher rangeMatcher = Pattern.compile("大于等于(\\d+)天[,，]小于(\\d+)天").matcher(holdingPeriodText);
        if (rangeMatcher.find()) {
            return new FundRedeemFeeRule(
                holdingPeriodText,
                Integer.parseInt(rangeMatcher.group(1)),
                Integer.parseInt(rangeMatcher.group(2)),
                feeRateValue
            );
        }

        Matcher greaterEqualMatcher = Pattern.compile("大于等于(\\d+)天").matcher(holdingPeriodText);
        if (greaterEqualMatcher.find()) {
            return new FundRedeemFeeRule(holdingPeriodText, Integer.parseInt(greaterEqualMatcher.group(1)), null, feeRateValue);
        }
        return null;
    }

    private String normalizeFundFeeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
            .replaceAll("<[^>]+>", "")
            .replace("&nbsp;", "")
            .replace(" ", "")
            .replace("\u00A0", "")
            .trim();
    }

    private List<FundHoldingLot> buildAvailableFundHoldingLots(InvestmentPositionEntity position) {
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getPositionId, position.getId())
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByAsc(InvestmentTransactionEntity::getTradeAt)
            .orderByAsc(InvestmentTransactionEntity::getId));

        BigDecimal currentHoldingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal confirmedBuyQuantity = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        BigDecimal confirmedSellQuantity = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        List<FundHoldingLot> lots = new ArrayList<>();
        List<BigDecimal> consumedSellQuantities = new ArrayList<>();

        for (InvestmentTransactionEntity transaction : transactions) {
            if ("buy".equals(transaction.getTradeType())
                && SETTLEMENT_STATUS_CONFIRMED.equals(transaction.getSettlementStatus())) {
                BigDecimal lotQuantity = scaleFundQuantity(defaultZero(transaction.getQuantity()));
                if (lotQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    lots.add(new FundHoldingLot(resolveFundLotAcquireDate(position, transaction), lotQuantity));
                    confirmedBuyQuantity = confirmedBuyQuantity.add(lotQuantity).setScale(6, RoundingMode.HALF_UP);
                }
                continue;
            }
            if ("sell".equals(transaction.getTradeType())
                && (SETTLEMENT_STATUS_CONFIRMED.equals(transaction.getSettlementStatus())
                || SETTLEMENT_STATUS_PENDING.equals(transaction.getSettlementStatus()))) {
                BigDecimal soldQuantity = scaleFundQuantity(defaultZero(transaction.getQuantity()));
                if (soldQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    consumedSellQuantities.add(soldQuantity);
                    if (SETTLEMENT_STATUS_CONFIRMED.equals(transaction.getSettlementStatus())) {
                        confirmedSellQuantity = confirmedSellQuantity.add(soldQuantity).setScale(6, RoundingMode.HALF_UP);
                    }
                }
            }
        }

        BigDecimal inferredInitialQuantity = currentHoldingQuantity.add(confirmedSellQuantity)
            .subtract(confirmedBuyQuantity)
            .setScale(6, RoundingMode.HALF_UP);
        if (inferredInitialQuantity.compareTo(BigDecimal.ZERO) > 0) {
            lots.add(0, new FundHoldingLot(resolveInitialFundLotDate(position), inferredInitialQuantity));
        }

        for (BigDecimal soldQuantity : consumedSellQuantities) {
            consumeFundHoldingLots(lots, soldQuantity);
        }
        return lots.stream()
            .filter(item -> item.quantity().compareTo(BigDecimal.ZERO) > 0)
            .toList();
    }

    private LocalDate resolveFundLotAcquireDate(InvestmentPositionEntity position, InvestmentTransactionEntity transaction) {
        if (transaction.getSettlementConfirmedAt() != null) {
            return transaction.getSettlementConfirmedAt().toLocalDate();
        }
        if (transaction.getSettlementAppliedDate() != null) {
            return transaction.getSettlementAppliedDate();
        }
        if (transaction.getTradeAt() != null) {
            return transaction.getTradeAt().toLocalDate();
        }
        return resolveInitialFundLotDate(position);
    }

    private LocalDate resolveInitialFundLotDate(InvestmentPositionEntity position) {
        if (position.getSubscriptionConfirmedAt() != null) {
            return position.getSubscriptionConfirmedAt().toLocalDate();
        }
        if (position.getSubscriptionAppliedDate() != null) {
            return position.getSubscriptionAppliedDate();
        }
        if (position.getCreatedAt() != null) {
            return position.getCreatedAt().toLocalDate();
        }
        return LocalDate.now();
    }

    private void consumeFundHoldingLots(List<FundHoldingLot> lots, BigDecimal quantity) {
        BigDecimal remainingQuantity = scaleFundQuantity(quantity);
        for (int index = 0; index < lots.size() && remainingQuantity.compareTo(BigDecimal.ZERO) > 0; index++) {
            FundHoldingLot currentLot = lots.get(index);
            BigDecimal currentQuantity = currentLot.quantity().setScale(6, RoundingMode.HALF_UP);
            if (currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal consumedQuantity = currentQuantity.min(remainingQuantity).setScale(6, RoundingMode.HALF_UP);
            BigDecimal nextQuantity = currentQuantity.subtract(consumedQuantity).setScale(6, RoundingMode.HALF_UP);
            lots.set(index, new FundHoldingLot(currentLot.acquiredDate(), nextQuantity));
            remainingQuantity = remainingQuantity.subtract(consumedQuantity).setScale(6, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal resolveFundRedeemFeeRate(List<FundRedeemFeeRule> rules, int holdingDays) {
        for (FundRedeemFeeRule rule : rules) {
            if (holdingDays < rule.minHoldingDaysInclusive()) {
                continue;
            }
            if (rule.maxHoldingDaysExclusive() == null || holdingDays < rule.maxHoldingDaysExclusive()) {
                return defaultZero(rule.feeRate());
            }
        }
        return BigDecimal.ZERO;
    }

    private int resolveFundHoldingDays(LocalDate acquiredDate, LocalDate appliedDate) {
        if (acquiredDate == null || appliedDate == null) {
            return 0;
        }
        return Math.max((int) ChronoUnit.DAYS.between(acquiredDate, appliedDate), 0);
    }

    private List<InvestmentChartPointResponse> fetchFundTrendPoints(
        String code,
        BigDecimal latestPrice,
        BigDecimal latestCumulativePrice,
        String latestDate
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fund.eastmoney.com/pingzhongdata/" + URLEncoder.encode(code, StandardCharsets.UTF_8) + ".js?v=" + System.currentTimeMillis()
                ))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
            JsonNode acRows = objectMapper.readTree(extractJsArray(body, "Data_ACWorthTrend"));
            if (acRows.isArray() && !acRows.isEmpty()) {
                return mergeLatestFundTrendPoint(buildFundAccumulativeTrendPoints(acRows), latestCumulativePrice, latestDate);
            }
            JsonNode netRows = objectMapper.readTree(extractJsArray(body, "Data_netWorthTrend"));
            if (!netRows.isArray()) {
                return Collections.emptyList();
            }
            return mergeLatestFundTrendPoint(buildFundNetWorthTrendPoints(netRows), latestPrice, latestDate);
        } catch (Exception ex) {
            BigDecimal fallbackPrice = latestCumulativePrice != null ? latestCumulativePrice : latestPrice;
            return mergeLatestFundTrendPoint(new ArrayList<>(), fallbackPrice, latestDate);
        }
    }

    private List<InvestmentChartPointResponse> buildFundAccumulativeTrendPoints(JsonNode rows) {
        List<InvestmentChartPointResponse> points = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - 3L * 365L * 24L * 60L * 60L * 1000L;
        for (JsonNode row : rows) {
            long timestamp = row.path(0).asLong(0);
            if (timestamp < cutoff) {
                continue;
            }
            BigDecimal value = safeDecimal(row.path(1).asText(null));
            if (value == null) {
                continue;
            }
            InvestmentChartPointResponse point = new InvestmentChartPointResponse();
            point.setLabel(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString());
            point.setValue(value);
            points.add(point);
        }
        return points;
    }

    private List<InvestmentChartPointResponse> buildFundNetWorthTrendPoints(JsonNode rows) {
        List<InvestmentChartPointResponse> points = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - 3L * 365L * 24L * 60L * 60L * 1000L;
        for (JsonNode row : rows) {
            long timestamp = row.path("x").asLong(0);
            if (timestamp < cutoff) {
                continue;
            }
            BigDecimal value = safeDecimal(row.path("y").asText(null));
            if (value == null) {
                continue;
            }
            InvestmentChartPointResponse point = new InvestmentChartPointResponse();
            point.setLabel(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString());
            point.setValue(value);
            points.add(point);
        }
        return points;
    }

    private List<InvestmentChartPointResponse> mergeLatestFundTrendPoint(
        List<InvestmentChartPointResponse> points,
        BigDecimal latestPrice,
        String latestDate
    ) {
        LocalDate quoteDate = safeDate(latestDate);
        if (quoteDate == null || latestPrice == null) {
            return points;
        }

        String normalizedDate = quoteDate.toString();
        List<InvestmentChartPointResponse> merged = points.stream()
            .filter(point -> !normalizedDate.equals(point.getLabel()))
            .collect(Collectors.toCollection(ArrayList::new));

        InvestmentChartPointResponse latestPoint = new InvestmentChartPointResponse();
        latestPoint.setLabel(normalizedDate);
        latestPoint.setValue(latestPrice);
        merged.add(latestPoint);
        merged.sort(Comparator.comparing(point -> safeDate(point.getLabel()), Comparator.nullsLast(Comparator.naturalOrder())));
        return merged;
    }

    private List<InvestmentDividendPlanEntity> fetchFundDividendPlans(InvestmentProductEntity product) {
        if (product == null || !StringUtils.hasText(product.getSymbol())) {
            return Collections.emptyList();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://fundf10.eastmoney.com/fhsp_" + URLEncoder.encode(product.getSymbol(), StandardCharsets.UTF_8) + ".html"
                ))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                return Collections.emptyList();
            }

            String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
            return parseFundDividendPlansFromHtml(product, body);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<InvestmentDividendPlanEntity> parseFundDividendPlansFromHtml(InvestmentProductEntity product, String body) {
        if (!StringUtils.hasText(body)) {
            return Collections.emptyList();
        }

        Pattern tablePattern = Pattern.compile(
            "<table[^>]*class=['\"][^'\"]*comm\\s+cfxq[^'\"]*['\"][^>]*>.*?<tbody>(.*?)</tbody>.*?</table>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher tableMatcher = tablePattern.matcher(body);
        if (!tableMatcher.find()) {
            return Collections.emptyList();
        }

        String tableBody = tableMatcher.group(1);
        Matcher rowMatcher = Pattern.compile("<tr>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(tableBody);
        List<InvestmentDividendPlanEntity> plans = new ArrayList<>();
        while (rowMatcher.find()) {
            List<String> cells = extractHtmlTableCells(rowMatcher.group(1));
            if (cells.size() < 5) {
                continue;
            }

            Integer dividendYear = parseDividendYear(cells.get(0));
            LocalDate recordDate = safeDate(cells.get(1));
            LocalDate exDividendDate = safeDate(cells.get(2));
            BigDecimal dividendPerUnit = parseDividendPerUnit(cells.get(3));
            LocalDate payDate = safeDate(cells.get(4));
            if (dividendYear == null || dividendPerUnit == null || payDate == null) {
                continue;
            }

            InvestmentDividendPlanEntity plan = new InvestmentDividendPlanEntity();
            plan.setProductId(product.getId());
            plan.setDividendYear(dividendYear);
            plan.setRecordDate(recordDate);
            plan.setExDividendDate(exDividendDate);
            plan.setPayDate(payDate);
            plan.setDividendPerUnit(dividendPerUnit.setScale(6, RoundingMode.HALF_UP));
            plan.setTaxRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
            plan.setCurrencyCode(blankToDefault(product.getCurrencyCode(), DEFAULT_CURRENCY_CODE));
            plan.setStatus(resolveDividendPlanStatus(payDate));
            plan.setSource(FUND_DIVIDEND_PLAN_SOURCE);
            plan.setRemark("基金分红计划由每日净值同步任务自动刷新");
            plans.add(plan);
        }
        return plans;
    }

    private List<String> extractHtmlTableCells(String rowHtml) {
        Matcher cellMatcher = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(rowHtml);
        List<String> cells = new ArrayList<>();
        while (cellMatcher.find()) {
            String value = cellMatcher.group(1)
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .trim();
            cells.add(value);
        }
        return cells;
    }

    private Integer parseDividendYear(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d{4})").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseDividendPerUnit(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(value.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        return safeDecimal(matcher.group(1));
    }

    private String resolveDividendPlanStatus(LocalDate payDate) {
        if (payDate == null) {
            return "confirmed";
        }
        return payDate.isBefore(LocalDate.now()) ? "paid" : "confirmed";
    }

    private boolean mergeFundDividendPlan(InvestmentDividendPlanEntity target, InvestmentDividendPlanEntity source) {
        boolean changed = false;
        if (!Objects.equals(target.getRecordDate(), source.getRecordDate())) {
            target.setRecordDate(source.getRecordDate());
            changed = true;
        }
        if (!Objects.equals(target.getExDividendDate(), source.getExDividendDate())) {
            target.setExDividendDate(source.getExDividendDate());
            changed = true;
        }
        if (!Objects.equals(target.getDividendPerUnit(), source.getDividendPerUnit())) {
            target.setDividendPerUnit(source.getDividendPerUnit());
            changed = true;
        }
        if (!Objects.equals(target.getTaxRate(), source.getTaxRate())) {
            target.setTaxRate(source.getTaxRate());
            changed = true;
        }
        if (!Objects.equals(target.getCurrencyCode(), source.getCurrencyCode())) {
            target.setCurrencyCode(source.getCurrencyCode());
            changed = true;
        }
        if (!Objects.equals(target.getStatus(), source.getStatus())) {
            target.setStatus(source.getStatus());
            changed = true;
        }
        if (!Objects.equals(target.getSource(), source.getSource())) {
            target.setSource(source.getSource());
            changed = true;
        }
        if (!Objects.equals(target.getRemark(), source.getRemark())) {
            target.setRemark(source.getRemark());
            changed = true;
        }
        return changed;
    }

    private JsonNode fetchTencentQuoteFields(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://qt.gtimg.cn/q=" + symbol))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = new String(httpResponse.body(), Charset.forName("GBK"));
            int start = body.indexOf('"');
            int end = body.lastIndexOf('"');
            if (start < 0 || end <= start) {
                return objectMapper.createObjectNode();
            }
            String[] fields = body.substring(start + 1, end).split("~");
            com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
            node.put("name", field(fields, 1));
            node.put("code", field(fields, 2));
            node.put("price", field(fields, 3));
            node.put("prevClose", field(fields, 4));
            node.put("open", field(fields, 5));
            node.put("volume", field(fields, 6));
            node.put("timeRaw", field(fields, 30));
            node.put("change", field(fields, 31));
            node.put("changePercent", field(fields, 32));
            node.put("high", field(fields, 33));
            node.put("low", field(fields, 34));
            node.put("turnoverRate", field(fields, 38));
            node.put("pe", field(fields, 39));
            return node;
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private List<InvestmentChartPointResponse> fetchStockKlinePoints(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=" + URLEncoder.encode(symbol + ",day,,,260,qfq", StandardCharsets.UTF_8)
                ))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            JsonNode root = objectMapper.readTree(new String(httpResponse.body(), StandardCharsets.UTF_8));
            JsonNode rows = root.path("data").path(symbol).path("qfqday");
            if (!rows.isArray() || rows.isEmpty()) {
                rows = root.path("data").path(symbol).path("day");
            }
            if (!rows.isArray()) {
                return Collections.emptyList();
            }
            List<InvestmentChartPointResponse> points = new ArrayList<>();
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 6) {
                    continue;
                }
                InvestmentChartPointResponse point = new InvestmentChartPointResponse();
                point.setLabel(row.get(0).asText());
                point.setOpen(safeDecimal(row.get(1).asText(null)));
                point.setClose(safeDecimal(row.get(2).asText(null)));
                point.setHigh(safeDecimal(row.get(3).asText(null)));
                point.setLow(safeDecimal(row.get(4).asText(null)));
                point.setVolume(safeDecimal(row.get(5).asText(null)));
                point.setValue(point.getClose());
                points.add(point);
            }
            return points;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private InvestmentDetailStatResponse stat(String label, String value, String tone) {
        return new InvestmentDetailStatResponse(label, value, tone);
    }

    private List<InvestmentProductResponse> fetchExternalProducts(String keyword, String productType) {
        if (keyword.matches("\\d{6}") && (!StringUtils.hasText(productType) || "fund".equals(productType))) {
            Optional<InvestmentProductResponse> fund = fetchFundProduct(keyword);
            if (fund.isPresent()) {
                return List.of(fund.get());
            }
        }

        if (keyword.matches("\\d{6}") && (!StringUtils.hasText(productType) || "stock".equals(productType))) {
            Optional<InvestmentProductResponse> stock = fetchStockProduct(keyword);
            if (stock.isPresent()) {
                return List.of(stock.get());
            }
        }

        return fetchEastMoneyProducts(keyword, productType);
    }

    private Optional<InvestmentProductResponse> fetchFundProduct(String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://fundgz.1234567.com.cn/js/" + code + ".js"))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/javascript,text/javascript,*/*")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                return Optional.empty();
            }

            String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(extractJsonpObject(body));
            String name = node.path("name").asText("");
            if (!StringUtils.hasText(name)) {
                return Optional.empty();
            }

            InvestmentProductResponse response = new InvestmentProductResponse();
            response.setProductType("fund");
            response.setMarket("FUND");
            response.setSymbol(node.path("fundcode").asText(code));
            response.setName(name);
            response.setShortName(name);
            response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
            response.setUnitName(DEFAULT_UNIT_NAME);
            response.setPricePrecision(4);
            response.setLatestPrice(decimalText(node.path("gsz").asText(null), node.path("dwjz").asText(null)));
            response.setStatus(ACTIVE_STATUS);
            return Optional.of(response);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private List<InvestmentProductResponse> fetchEastMoneyProducts(String keyword, String productType) {
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://searchapi.eastmoney.com/api/suggest/get?input=" + encodedKeyword + "&type=14&count=20&cb=searchResult"
                ))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                return Collections.emptyList();
            }

            String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
            JsonNode rows = objectMapper.readTree(extractJsonpObject(body)).path("QuotationCodeTable").path("Data");
            if (!rows.isArray()) {
                return Collections.emptyList();
            }

            return java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(row -> toExternalProductFromSuggestion(row, productType))
                .flatMap(Optional::stream)
                .limit(8)
                .toList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Optional<InvestmentProductResponse> toExternalProductFromSuggestion(JsonNode row, String productType) {
        String code = row.path("Code").asText("");
        String classify = row.path("Classify").asText("");
        String securityTypeName = row.path("SecurityTypeName").asText("");
        String resolvedType = resolveProductType(classify, securityTypeName);
        if (!StringUtils.hasText(code) || !StringUtils.hasText(resolvedType)) {
            return Optional.empty();
        }
        if (StringUtils.hasText(productType) && !productType.equals(resolvedType)) {
            return Optional.empty();
        }

        if ("fund".equals(resolvedType)) {
            return fetchFundProduct(code).or(() -> Optional.of(toBasicExternalProduct(row, "fund", "FUND", null, "份", 4)));
        }
        if ("stock".equals(resolvedType)) {
            return fetchStockProduct(code)
                .or(() -> fetchTencentStockProduct(code))
                .or(() -> Optional.of(toBasicExternalProduct(row, "stock", "CN", resolveExchangeCode(row), "股", 2)));
        }
        if ("bond".equals(resolvedType)) {
            return Optional.of(toBasicExternalProduct(row, "bond", "CN", resolveExchangeCode(row), "张", 4));
        }
        return fetchTencentStockProduct(code);
    }

    private Optional<InvestmentProductResponse> fetchTencentStockProduct(String code) {
        for (String symbol : tencentStockSymbols(code)) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://qt.gtimg.cn/q=" + symbol))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
                HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                    continue;
                }

                String body = new String(httpResponse.body(), Charset.forName("GBK"));
                int start = body.indexOf('"');
                int end = body.lastIndexOf('"');
                if (start < 0 || end <= start) {
                    continue;
                }
                String[] fields = body.substring(start + 1, end).split("~");
                if (fields.length < 4 || !StringUtils.hasText(fields[1])) {
                    continue;
                }

                InvestmentProductResponse response = new InvestmentProductResponse();
                response.setProductType("stock");
                response.setMarket("CN");
                response.setExchangeCode(symbol.startsWith("sh") ? "SSE" : "SZSE");
                response.setSymbol(fields[2]);
                response.setName(fields[1]);
                response.setShortName(fields[1]);
                response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
                response.setUnitName("股");
                response.setPricePrecision(2);
                response.setLatestPrice(decimalText(fields[3], null));
                response.setStatus(ACTIVE_STATUS);
                return Optional.of(response);
            } catch (Exception ex) {
                // Try next market prefix.
            }
        }
        return Optional.empty();
    }

    private String resolveProductType(String classify, String securityTypeName) {
        if ("OTCFUND".equals(classify) || securityTypeName.contains("基金")) {
            return "fund";
        }
        if ("AStock".equals(classify) || securityTypeName.contains("A")) {
            return "stock";
        }
        if ("Bond".equals(classify) || securityTypeName.contains("债券")) {
            return "bond";
        }
        return null;
    }

    private InvestmentProductResponse toBasicExternalProduct(JsonNode row, String productType, String market, String exchangeCode, String unitName, int pricePrecision) {
        InvestmentProductResponse response = new InvestmentProductResponse();
        response.setProductType(productType);
        response.setMarket(market);
        response.setExchangeCode(exchangeCode);
        response.setSymbol(row.path("Code").asText(""));
        response.setName(row.path("Name").asText(""));
        response.setShortName(row.path("Name").asText(""));
        response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        response.setUnitName(unitName);
        response.setPricePrecision(pricePrecision);
        response.setStatus(ACTIVE_STATUS);
        return response;
    }

    private String resolveExchangeCode(JsonNode row) {
        String quoteId = row.path("QuoteID").asText("");
        if (quoteId.startsWith("1.")) {
            return "SSE";
        }
        if (quoteId.startsWith("0.")) {
            return "SZSE";
        }
        return null;
    }

    private Optional<InvestmentProductResponse> fetchStockProduct(String code) {
        for (String secid : stockSecids(code)) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://push2.eastmoney.com/api/qt/stock/get?secid=" + secid + "&fields=f57,f58,f43"))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept-Encoding", "identity")
                    .GET()
                    .build();
                HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                    continue;
                }

                String body = new String(httpResponse.body(), StandardCharsets.UTF_8);
                JsonNode data = objectMapper.readTree(body).path("data");
                String name = data.path("f58").asText("");
                if (!StringUtils.hasText(name)) {
                    continue;
                }

                InvestmentProductResponse response = new InvestmentProductResponse();
                response.setProductType("stock");
                response.setMarket("CN");
                response.setExchangeCode(secid.startsWith("1.") ? "SSE" : "SZSE");
                response.setSymbol(data.path("f57").asText(code));
                response.setName(name);
                response.setShortName(name);
                response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
                response.setUnitName("股");
                response.setPricePrecision(2);
                BigDecimal rawPrice = decimalText(data.path("f43").asText(null), null);
                response.setLatestPrice(rawPrice == null ? null : rawPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                response.setStatus(ACTIVE_STATUS);
                return Optional.of(response);
            } catch (Exception ex) {
                // Try next market prefix.
            }
        }
        return Optional.empty();
    }

    private List<String> stockSecids(String code) {
        if (code.startsWith("6")) {
            return List.of("1." + code, "0." + code);
        }
        return List.of("0." + code, "1." + code);
    }

    private List<String> tencentStockSymbols(String code) {
        if (code.startsWith("6")) {
            return List.of("sh" + code, "sz" + code);
        }
        return List.of("sz" + code, "sh" + code);
    }

    private String toTencentSymbol(String code, String exchangeCode) {
        if ("SSE".equals(exchangeCode)) {
            return "sh" + code;
        }
        if ("SZSE".equals(exchangeCode)) {
            return "sz" + code;
        }
        return code.startsWith("6") ? "sh" + code : "sz" + code;
    }

    private String extractJsonpObject(String body) {
        int start = body == null ? -1 : body.indexOf('{');
        int end = body == null ? -1 : body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("外部产品数据格式错误");
        }
        return body.substring(start, end + 1);
    }

    private String extractJsArray(String body, String variableName) {
        String marker = "var " + variableName + " = ";
        int start = body == null ? -1 : body.indexOf(marker);
        if (start < 0) {
            marker = variableName + " = ";
            start = body == null ? -1 : body.indexOf(marker);
        }
        if (start < 0) {
            throw new IllegalArgumentException("外部走势数据格式错误");
        }
        int arrayStart = body.indexOf('[', start + marker.length());
        int semicolon = body.indexOf(';', arrayStart);
        if (arrayStart < 0 || semicolon <= arrayStart) {
            throw new IllegalArgumentException("外部走势数据格式错误");
        }
        return body.substring(arrayStart, semicolon);
    }

    private BigDecimal decimalText(String first, String second) {
        String value = StringUtils.hasText(first) && !"--".equals(first) ? first : second;
        if (!StringUtils.hasText(value) || "--".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }

    private BigDecimal safeDecimal(String value) {
        if (!StringUtils.hasText(value) || "--".equals(value) || "-".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate safeDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String normalized = value.trim();
            if (normalized.length() >= 10) {
                normalized = normalized.substring(0, 10);
            }
            return LocalDate.parse(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    private String field(String[] fields, int index) {
        return fields.length > index ? fields[index] : "";
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String blankToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String moneyText(BigDecimal value, int scale) {
        if (value == null) {
            return "-";
        }
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String currencyText(BigDecimal value, int scale) {
        String text = moneyText(value == null ? null : value.abs(), scale);
        if ("-".equals(text)) {
            return "-";
        }
        return (value.compareTo(BigDecimal.ZERO) < 0 ? "-¥" : "¥") + text;
    }

    private String priceText(BigDecimal value, String productType) {
        String text = moneyText(value, "stock".equals(productType) ? 2 : 4);
        if ("-".equals(text)) {
            return "-";
        }
        return "¥" + text;
    }

    private String percentText(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private String tone(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "neutral";
        }
        return value.compareTo(BigDecimal.ZERO) > 0 ? "positive" : "negative";
    }

    private String productTypeName(String productType) {
        if ("stock".equals(productType)) return "股票";
        if ("fund".equals(productType)) return "基金";
        if ("bond".equals(productType)) return "债券";
        if ("gold".equals(productType)) return "黄金";
        return "其他";
    }

    private String formatTencentTime(String raw) {
        if (!StringUtils.hasText(raw) || raw.length() != 14) {
            return null;
        }
        return raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8)
            + " " + raw.substring(8, 10) + ":" + raw.substring(10, 12) + ":" + raw.substring(12, 14);
    }

    private String formatPositionSyncTime(LocalDateTime value) {
        return value == null ? null : value.format(POSITION_SYNC_TIME_FORMAT);
    }

    private boolean hasTodayDayProfit(InvestmentPositionEntity position) {
        return position != null
            && position.getLastSyncedAt() != null
            && LocalDate.now().equals(position.getLastSyncedAt().toLocalDate());
    }

    private boolean hasTodayFundProfitData(List<InvestmentPositionEntity> positions, LocalDate latestDate) {
        return latestDate != null
            && LocalDate.now().equals(latestDate)
            && !positions.isEmpty()
            && positions.stream().allMatch(this::hasTodayDayProfit);
    }

    private InvestmentDividendResponse toDividendRecordResponse(InvestmentDividendRecordEntity entity) {
        InvestmentProductEntity product = productMapper.selectById(entity.getProductId());
        InvestmentDividendResponse response = new InvestmentDividendResponse();
        response.setId(entity.getId());
        response.setProductId(entity.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setActualAmount(entity.getNetAmount());
        response.setDividendPerUnit(entity.getDividendPerUnit());
        response.setStatus(entity.getStatus());
        response.setPaidAt(entity.getPaidAt());
        return response;
    }

    private InvestmentDividendResponse toDividendPlanResponse(InvestmentDividendPlanEntity entity) {
        InvestmentProductEntity product = productMapper.selectById(entity.getProductId());
        InvestmentDividendResponse response = new InvestmentDividendResponse();
        response.setId(entity.getId());
        response.setProductId(entity.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setDividendYear(entity.getDividendYear());
        response.setPayDate(entity.getPayDate());
        response.setDividendPerUnit(entity.getDividendPerUnit());
        response.setStatus(entity.getStatus());
        return response;
    }

    private List<InvestmentDividendResponse> buildRecentDividendRecords(
        InvestmentPositionEntity position,
        InvestmentProductEntity product
    ) {
        if (position == null || product == null) {
            return Collections.emptyList();
        }
        if (!FUND_PRODUCT_TYPE.equals(product.getProductType()) && !"stock".equals(product.getProductType())) {
            return Collections.emptyList();
        }

        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusYears(1);
        Map<LocalDate, BigDecimal> actualAmountsByDate = dividendRecordMapper.selectList(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
                .eq(InvestmentDividendRecordEntity::getPositionId, position.getId())
                .eq(InvestmentDividendRecordEntity::getStatus, NORMAL_STATUS)
                .ge(InvestmentDividendRecordEntity::getPaidAt, oneYearAgo.atStartOfDay()))
            .stream()
            .filter(record -> record.getPaidAt() != null)
            .collect(Collectors.toMap(
                record -> record.getPaidAt().toLocalDate(),
                record -> defaultZero(record.getNetAmount()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal::add,
                LinkedHashMap::new
            ));

        List<InvestmentDividendPlanEntity> plans = FUND_PRODUCT_TYPE.equals(product.getProductType())
            ? fetchFundHistoricalDividendPlans(product)
            : fetchStockHistoricalDividendPlans(product);

        return plans.stream()
            .filter(plan -> {
                LocalDate payDate = dividendPlanRecencyDate(plan);
                return payDate != null && !payDate.isBefore(oneYearAgo) && !payDate.isAfter(today);
            })
            .sorted(Comparator.comparing(this::dividendPlanRecencyDate).reversed())
            .map(plan -> toDividendRecordResponse(plan, product, position, actualAmountsByDate))
            .toList();
    }

    private InvestmentDividendResponse toDividendRecordResponse(
        InvestmentDividendPlanEntity plan,
        InvestmentProductEntity product,
        InvestmentPositionEntity position,
        Map<LocalDate, BigDecimal> actualAmountsByDate
    ) {
        InvestmentDividendResponse response = new InvestmentDividendResponse();
        LocalDate payDate = dividendPlanRecencyDate(plan);
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal perUnit = resolveNetDividendPerUnit(plan);
        response.setId(plan.getId());
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setProductSymbol(product.getSymbol());
        response.setDividendYear(plan.getDividendYear());
        response.setPayDate(payDate);
        response.setDividendPerUnit(defaultZero(plan.getDividendPerUnit()).setScale(6, RoundingMode.HALF_UP));
        response.setExpectedAmount(holdingQuantity.multiply(perUnit).setScale(2, RoundingMode.HALF_UP));
        response.setActualAmount(payDate == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : defaultZero(actualAmountsByDate.get(payDate)).setScale(2, RoundingMode.HALF_UP));
        response.setStatus(plan.getStatus());
        response.setPaidAt(payDate == null ? null : payDate.atStartOfDay());
        return response;
    }

    private InvestmentDividendIncomePageResponse emptyDividendIncomePage(Long userId) {
        InvestmentDividendIncomeSummaryResponse summary = new InvestmentDividendIncomeSummaryResponse();
        summary.setEstimatedDividendAmount(scaleMoney(BigDecimal.ZERO));
        summary.setEstimatedDividendRate(scaleRate(BigDecimal.ZERO));
        summary.setActualDividendAmount(scaleMoney(BigDecimal.ZERO));
        summary.setActualDividendRate(scaleRate(BigDecimal.ZERO));
        summary.setHoldingCount(0);

        InvestmentDividendIncomePageResponse response = new InvestmentDividendIncomePageResponse();
        response.setUserId(userId);
        response.setSummary(summary);
        response.setItems(Collections.emptyList());
        response.setUpdatedAt(null);
        return response;
    }

    private InvestmentDividendIncomeItemResponse toDividendIncomeItem(
        Long productId,
        List<InvestmentPositionEntity> positions,
        InvestmentProductEntity product,
        List<InvestmentDividendRecordEntity> records
    ) {
        if (product == null || !Boolean.TRUE.equals(product.getStableDividend())) {
            return null;
        }
        BigDecimal holdingQuantity = positions.stream()
            .map(InvestmentPositionEntity::getHoldingQuantity)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marketValue = positions.stream()
            .map(InvestmentPositionEntity::getMarketValue)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costAmount = positions.stream()
            .map(InvestmentPositionEntity::getCostAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualDividendAmount = records.stream()
            .map(InvestmentDividendRecordEntity::getNetAmount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal predictedAnnualDividendPerUnit = defaultZero(product.getPredictedAnnualDividendPerUnit())
            .setScale(6, RoundingMode.HALF_UP);
        if (predictedAnnualDividendPerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal estimatedDividendAmount = holdingQuantity.multiply(predictedAnnualDividendPerUnit)
            .setScale(2, RoundingMode.HALF_UP);

        InvestmentDividendIncomeItemResponse response = new InvestmentDividendIncomeItemResponse();
        response.setProductId(productId);
        response.setProductName(product == null ? "-" : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        response.setProductType(product == null ? null : product.getProductType());
        response.setUnitName(product == null ? DEFAULT_UNIT_NAME : blankToDefault(product.getUnitName(), DEFAULT_UNIT_NAME));
        response.setHoldingQuantity(scaleQuantity(holdingQuantity));
        response.setMarketValue(scaleMoney(marketValue));
        response.setCostAmount(scaleMoney(costAmount));
        response.setEstimatedDividendAmount(scaleMoney(estimatedDividendAmount));
        response.setEstimatedDividendRate(scaleRate(rate(estimatedDividendAmount, marketValue)));
        response.setActualDividendAmount(scaleMoney(actualDividendAmount));
        response.setActualDividendRate(scaleRate(rate(actualDividendAmount, costAmount)));
        return response;
    }

    private void ensureDividendProfile(InvestmentProductEntity product) {
        if (product == null || !supportsDividendProfile(product)) {
            return;
        }
        if (product.getDividendEvaluatedAt() != null) {
            return;
        }
        evaluateDividendProfile(product);
        if (product.getId() != null) {
            productMapper.updateById(product);
        }
    }

    private boolean supportsDividendProfile(InvestmentProductEntity product) {
        return product != null
            && StringUtils.hasText(product.getSymbol())
            && (FUND_PRODUCT_TYPE.equals(product.getProductType()) || "stock".equals(product.getProductType()));
    }

    private void evaluateDividendProfile(InvestmentProductEntity product) {
        if (!supportsDividendProfile(product)) {
            applyDividendProfile(product, null);
            return;
        }
        List<InvestmentDividendPlanEntity> historicalPlans = FUND_PRODUCT_TYPE.equals(product.getProductType())
            ? fetchFundHistoricalDividendPlans(product)
            : fetchStockHistoricalDividendPlans(product);
        String source = FUND_PRODUCT_TYPE.equals(product.getProductType())
            ? FUND_DIVIDEND_PLAN_SOURCE
            : STOCK_DIVIDEND_HISTORY_SOURCE;
        applyDividendProfile(product, buildDividendProfile(historicalPlans, source));
    }

    private void applyDividendProfile(InvestmentProductEntity product, DividendProfile profile) {
        if (product == null) {
            return;
        }
        product.setStableDividend(profile != null && profile.stable());
        product.setPredictedAnnualDividendPerUnit(profile == null
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : defaultZero(profile.annualDividendPerUnit()).setScale(6, RoundingMode.HALF_UP));
        product.setDividendStableYears(profile == null ? 0 : Math.max(profile.stableYears(), 0));
        product.setDividendLastPaidDate(profile == null ? null : profile.lastDividendDate());
        product.setDividendDataSource(profile == null ? null : profile.source());
        product.setDividendEvaluatedAt(LocalDateTime.now());
    }

    private List<InvestmentDividendPlanEntity> fetchFundHistoricalDividendPlans(InvestmentProductEntity product) {
        return fetchFundDividendPlans(product).stream()
            .filter(plan -> isHistoricalDividendPlan(plan, LocalDate.now()))
            .toList();
    }

    private List<InvestmentDividendPlanEntity> fetchStockHistoricalDividendPlans(InvestmentProductEntity product) {
        if (product == null || !StringUtils.hasText(product.getSymbol())) {
            return Collections.emptyList();
        }
        try {
            String filter = URLEncoder.encode("(SECURITY_CODE=\"" + product.getSymbol() + "\")", StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_SHAREBONUS_DET"
                        + "&columns=ALL"
                        + "&filter=" + filter
                        + "&pageNumber=1&pageSize=50&sortColumns=REPORT_DATE&sortTypes=-1&source=WEB&client=WEB"
                ))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json,text/plain,*/*")
                .GET()
                .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                return Collections.emptyList();
            }

            JsonNode rows = objectMapper.readTree(new String(httpResponse.body(), StandardCharsets.UTF_8))
                .path("result")
                .path("data");
            if (!rows.isArray()) {
                return Collections.emptyList();
            }

            List<InvestmentDividendPlanEntity> plans = new ArrayList<>();
            for (JsonNode row : rows) {
                BigDecimal pretaxBonus = safeDecimal(row.path("PRETAX_BONUS_RMB").asText(null));
                LocalDate exDividendDate = safeDate(row.path("EX_DIVIDEND_DATE").asText(null));
                if (pretaxBonus == null || pretaxBonus.compareTo(BigDecimal.ZERO) <= 0 || exDividendDate == null) {
                    continue;
                }

                InvestmentDividendPlanEntity plan = new InvestmentDividendPlanEntity();
                plan.setProductId(product.getId());
                plan.setDividendYear(resolveStockDividendYear(row, exDividendDate));
                plan.setRecordDate(safeDate(row.path("EQUITY_RECORD_DATE").asText(null)));
                plan.setExDividendDate(exDividendDate);
                plan.setPayDate(exDividendDate);
                plan.setDividendPerUnit(pretaxBonus.divide(BigDecimal.TEN, 6, RoundingMode.HALF_UP));
                plan.setTaxRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                plan.setCurrencyCode(blankToDefault(product.getCurrencyCode(), DEFAULT_CURRENCY_CODE));
                plan.setStatus(resolveDividendPlanStatus(exDividendDate));
                plan.setSource(STOCK_DIVIDEND_HISTORY_SOURCE);
                plan.setRemark(row.path("IMPL_PLAN_PROFILE").asText(null));
                plans.add(plan);
            }
            return plans;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Integer resolveStockDividendYear(JsonNode row, LocalDate fallbackDate) {
        LocalDate reportDate = safeDate(row.path("REPORT_DATE").asText(null));
        if (reportDate != null) {
            return reportDate.getYear();
        }
        return fallbackDate == null ? null : fallbackDate.getYear();
    }

    private DividendProfile buildDividendProfile(
        List<InvestmentDividendPlanEntity> historicalPlans,
        String source
    ) {
        if (historicalPlans == null || historicalPlans.isEmpty()) {
            return null;
        }

        LocalDate today = LocalDate.now();
        LocalDate recentWindowStart = today.minusYears(DIVIDEND_HISTORY_YEARS);
        List<InvestmentDividendPlanEntity> recentPlans = historicalPlans.stream()
            .filter(plan -> {
                LocalDate recencyDate = dividendPlanRecencyDate(plan);
                return recencyDate != null && !recencyDate.isBefore(recentWindowStart) && !recencyDate.isAfter(today);
            })
            .sorted(Comparator.comparing(this::dividendPlanRecencyDate).reversed())
            .toList();
        if (recentPlans.isEmpty()) {
            return null;
        }

        Set<Integer> recentYears = recentPlans.stream()
            .map(plan -> resolveDividendPlanYear(plan, today))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate latestPayDate = recentPlans.stream()
            .map(this::dividendPlanRecencyDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(null);

        boolean stable = recentYears.size() >= MIN_STABLE_DIVIDEND_YEARS
            && latestPayDate != null
            && !latestPayDate.isBefore(today.minusYears(2));

        Map<Integer, BigDecimal> yearlyDividendPerUnit = new TreeMap<>(Comparator.reverseOrder());
        for (InvestmentDividendPlanEntity plan : recentPlans) {
            Integer year = resolveDividendPlanYear(plan, today);
            if (year == null) {
                continue;
            }
            BigDecimal netDividendPerUnit = resolveNetDividendPerUnit(plan);
            yearlyDividendPerUnit.merge(year, netDividendPerUnit, BigDecimal::add);
        }

        List<BigDecimal> latestYearlyDividends = yearlyDividendPerUnit.values().stream()
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .limit(DIVIDEND_HISTORY_YEARS)
            .toList();
        if (latestYearlyDividends.size() < MIN_STABLE_DIVIDEND_YEARS) {
            stable = false;
        }

        BigDecimal total = latestYearlyDividends.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageAnnualDividendPerUnit = latestYearlyDividends.isEmpty()
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : total.divide(BigDecimal.valueOf(latestYearlyDividends.size()), 6, RoundingMode.HALF_UP);
        return new DividendProfile(
            stable,
            averageAnnualDividendPerUnit,
            recentYears.size(),
            latestPayDate,
            StringUtils.hasText(source)
                ? source
                : historicalPlans.stream()
                    .map(InvestmentDividendPlanEntity::getSource)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null)
        );
    }

    private boolean isHistoricalDividendPlan(InvestmentDividendPlanEntity plan, LocalDate today) {
        if (plan == null) {
            return false;
        }
        LocalDate payDate = plan.getPayDate();
        if (payDate == null) {
            return "paid".equals(plan.getStatus());
        }
        return !payDate.isAfter(today);
    }

    private Integer resolveDividendPlanYear(InvestmentDividendPlanEntity plan, LocalDate today) {
        if (plan == null) {
            return null;
        }
        if (plan.getDividendYear() != null && plan.getDividendYear() > 0) {
            return plan.getDividendYear();
        }
        if (plan.getPayDate() != null) {
            return plan.getPayDate().getYear();
        }
        if (plan.getRecordDate() != null) {
            return plan.getRecordDate().getYear();
        }
        if (plan.getExDividendDate() != null) {
            return plan.getExDividendDate().getYear();
        }
        return today.getYear();
    }

    private BigDecimal resolveNetDividendPerUnit(InvestmentDividendPlanEntity plan) {
        BigDecimal dividendPerUnit = defaultZero(plan.getDividendPerUnit());
        BigDecimal taxRate = defaultZero(plan.getTaxRate());
        BigDecimal netFactor = BigDecimal.ONE.subtract(taxRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return dividendPerUnit.multiply(netFactor).setScale(6, RoundingMode.HALF_UP);
    }

    private InvestmentDividendPlanEntity pickLaterDividendPlan(
        InvestmentDividendPlanEntity current,
        InvestmentDividendPlanEntity candidate
    ) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return compareDividendPlanRecency(candidate, current) >= 0 ? candidate : current;
    }

    private int compareDividendPlanRecency(InvestmentDividendPlanEntity left, InvestmentDividendPlanEntity right) {
        LocalDate leftDate = dividendPlanRecencyDate(left);
        LocalDate rightDate = dividendPlanRecencyDate(right);
        if (leftDate != null && rightDate != null) {
            int compare = leftDate.compareTo(rightDate);
            if (compare != 0) {
                return compare;
            }
        } else if (leftDate != null) {
            return 1;
        } else if (rightDate != null) {
            return -1;
        }

        LocalDateTime leftUpdatedAt = left.getUpdatedAt();
        LocalDateTime rightUpdatedAt = right.getUpdatedAt();
        if (leftUpdatedAt != null && rightUpdatedAt != null) {
            int compare = leftUpdatedAt.compareTo(rightUpdatedAt);
            if (compare != 0) {
                return compare;
            }
        } else if (leftUpdatedAt != null) {
            return 1;
        } else if (rightUpdatedAt != null) {
            return -1;
        }

        return Long.compare(defaultLong(left.getId()), defaultLong(right.getId()));
    }

    private LocalDate dividendPlanRecencyDate(InvestmentDividendPlanEntity plan) {
        if (plan == null) {
            return null;
        }
        if (plan.getPayDate() != null) {
            return plan.getPayDate();
        }
        if (plan.getRecordDate() != null) {
            return plan.getRecordDate();
        }
        return plan.getExDividendDate();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleRate(BigDecimal value) {
        return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal profit, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return profit.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<InvestmentPositionEntity> positions, Function<InvestmentPositionEntity, BigDecimal> getter) {
        return positions.stream().map(getter).filter(item -> item != null).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private List<InvestmentPositionEntity> filterPositionsByAccountType(List<InvestmentPositionEntity> positions, String accountTypeCode) {
        if (positions.isEmpty() || !StringUtils.hasText(accountTypeCode)) {
            return positions;
        }

        Set<Long> accountIds = positions.stream()
            .map(InvestmentPositionEntity::getAccountId)
            .filter(item -> item != null)
            .collect(Collectors.toSet());
        if (accountIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> accountTypeCodes = loadAccountTypeCodesByAccountId(accountIds);

        return positions.stream()
            .filter(position -> accountTypeCode.equals(accountTypeCodes.get(position.getAccountId())))
            .toList();
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

    private String generateTransactionNo() {
        return "IV" + LocalDateTime.now().format(NO_TIME_FORMAT) + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
