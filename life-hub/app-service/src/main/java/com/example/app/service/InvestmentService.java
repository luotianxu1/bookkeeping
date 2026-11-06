package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.app.client.MarketQuoteClient;
import com.example.app.dto.InvestmentDividendResponse;
import com.example.app.dto.InvestmentDividendIncomeItemResponse;
import com.example.app.dto.InvestmentDividendIncomePageResponse;
import com.example.app.dto.InvestmentDividendIncomeSummaryResponse;
import com.example.app.dto.InvestmentDividendForecastRequest;
import com.example.app.dto.InvestmentDividendForecastResponse;
import com.example.app.dto.InvestmentProductQuoteResponse;
import com.example.app.dto.InvestmentAssetDetailResponse;
import com.example.app.dto.InvestmentAutoInvestPlanRequest;
import com.example.app.dto.InvestmentAutoInvestPlanResponse;
import com.example.app.dto.InvestmentChartPointResponse;
import com.example.app.dto.InvestmentDetailStatResponse;
import com.example.app.dto.InvestmentFundRedeemFeeOptionResponse;
import com.example.app.dto.InvestmentFixedExpenseRequest;
import com.example.app.dto.InvestmentFixedExpenseResponse;
import com.example.app.dto.InvestmentPositionRequest;
import com.example.app.dto.InvestmentPositionResponse;
import com.example.app.dto.InvestmentProductRequest;
import com.example.app.dto.InvestmentProductResponse;
import com.example.app.dto.InvestmentSummaryResponse;
import com.example.app.dto.InvestmentTrendAllocationResponse;
import com.example.app.dto.InvestmentTrendContributorResponse;
import com.example.app.dto.InvestmentTrendPointResponse;
import com.example.app.dto.InvestmentTrendResponse;
import com.example.app.dto.InvestmentTransactionRequest;
import com.example.app.dto.InvestmentTransactionPageResponse;
import com.example.app.dto.InvestmentTransactionResponse;
import com.example.app.entity.AccountEntity;
import com.example.app.entity.AccountTypeEntity;
import com.example.app.entity.InvestmentAutoInvestPlanEntity;
import com.example.app.entity.InvestmentDividendPlanEntity;
import com.example.app.entity.InvestmentDividendRecordEntity;
import com.example.app.entity.InvestmentFixedExpenseEntity;
import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentPriceQuoteEntity;
import com.example.app.entity.InvestmentProductEntity;
import com.example.app.entity.InvestmentTransactionEntity;
import com.example.app.mapper.AccountMapper;
import com.example.app.mapper.AccountTypeMapper;
import com.example.app.mapper.InvestmentAutoInvestPlanMapper;
import com.example.app.mapper.InvestmentDividendPlanMapper;
import com.example.app.mapper.InvestmentDividendRecordMapper;
import com.example.app.mapper.InvestmentFixedExpenseMapper;
import com.example.app.mapper.InvestmentPositionMapper;
import com.example.app.mapper.InvestmentPriceQuoteMapper;
import com.example.app.mapper.InvestmentProductMapper;
import com.example.app.mapper.InvestmentTransactionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import static com.example.app.service.InvestmentCalcUtils.*;
import static com.example.app.service.InvestmentPositionAccounting.*;
import com.example.app.service.InvestmentFundFeeService.FundRedeemFeeRule;
import static com.example.app.service.InvestmentDividendService.*;
import static com.example.app.service.InvestmentQuoteSyncService.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class InvestmentService {

    private record ForecastPriceSnapshot(
        LocalDate quoteDate,
        BigDecimal price
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

    private static final Logger log = LoggerFactory.getLogger(InvestmentService.class);
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    private static final String DEFAULT_UNIT_NAME = "份";
    private static final String ACTIVE_STATUS = "active";
    private static final String DELETED_STATUS = "deleted";
    private static final String INVESTMENT_ACCOUNT_TYPE_CODE = "investment";
    private static final String NORMAL_STATUS = "normal";
    private static final String VOIDED_STATUS = "voided";
    private static final String CASH_ACCOUNT_TYPE_CODE = "cash";
    private static final String FUND_PRODUCT_TYPE = "fund";
    private static final String STOCK_PRODUCT_TYPE = "stock";
    private static final String DIVIDEND_TYPE_CASH = "cash";
    private static final String DIVIDEND_TYPE_REINVEST = "reinvest";
    private static final String SUBSCRIPTION_STATUS_CONFIRMED = "confirmed";
    private static final String SUBSCRIPTION_STATUS_PENDING = "pending";
    private static final String SETTLEMENT_STATUS_CONFIRMED = "confirmed";
    private static final String SETTLEMENT_STATUS_PENDING = "pending";
    private static final String INITIAL_FUND_SUBSCRIPTION_REMARK = "基金首次申购";
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
    private static final BigDecimal MIN_DIVIDEND_INCOME_RATE = new BigDecimal("3.00");
    private static final int RECENT_DIVIDEND_PLAN_WINDOW_DAYS = 30;
    private static final LocalTime FUND_SUBSCRIPTION_CUTOFF_TIME = LocalTime.of(15, 0);
    private static final LocalTime FUND_PROFIT_SYNC_READY_TIME = LocalTime.of(21, 30);
    private static final Set<String> POSITION_ACCOUNT_TYPE_CODES = Set.of("investment", "gold");
    private static final Set<String> FUND_SUBSCRIPTION_OPEN_STATUS_KEYWORDS = Set.of(
        "开放申购",
        "限制大额申购",
        "限大额申购",
        "限大额",
        "限额申购",
        "暂停大额申购"
    );
    private static final Set<String> FUND_SUBSCRIPTION_CLOSED_STATUS_KEYWORDS = Set.of(
        "暂停申购",
        "停止申购",
        "封闭",
        "不可申购",
        "不开放申购",
        "认购期",
        "清盘",
        "终止"
    );
    private static final DateTimeFormatter NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter POSITION_SYNC_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InvestmentProductMapper productMapper;
    private final InvestmentPositionMapper positionMapper;
    private final InvestmentTransactionMapper transactionMapper;
    private final InvestmentAutoInvestPlanMapper autoInvestPlanMapper;
    private final InvestmentDividendPlanMapper dividendPlanMapper;
    private final InvestmentDividendRecordMapper dividendRecordMapper;
    private final InvestmentFixedExpenseMapper fixedExpenseMapper;
    private final AccountMapper accountMapper;
    private final AccountTypeMapper accountTypeMapper;
    private final InvestmentPriceQuoteMapper priceQuoteMapper;
    private final GoldPriceService goldPriceService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final MarketCalendarService marketCalendarService;
    private final MarketQuoteClient marketQuoteClient;
    private final InvestmentDividendService dividendService;
    private final InvestmentQuoteSyncService quoteSyncService;
    /** 共享价格历史助手：持仓成交明细、历史价格加载与任意日期回溯 */
    private final InvestmentPriceHistoryService priceHistoryService;
    /** 基金收益域服务：收益预测与收益页 */
    private final InvestmentFundProfitService fundProfitService;
    private final InvestmentFundFeeService fundFeeService;
    /** 夜间同步的行情抓取线程池：净值/分红同步按产品并发化，缩短任务窗口 */
    private final ExecutorService fundSyncExecutor;

    public InvestmentService(
        InvestmentProductMapper productMapper,
        InvestmentPositionMapper positionMapper,
        InvestmentTransactionMapper transactionMapper,
        InvestmentAutoInvestPlanMapper autoInvestPlanMapper,
        InvestmentDividendPlanMapper dividendPlanMapper,
        InvestmentDividendRecordMapper dividendRecordMapper,
        InvestmentFixedExpenseMapper fixedExpenseMapper,
        AccountMapper accountMapper,
        AccountTypeMapper accountTypeMapper,
        InvestmentPriceQuoteMapper priceQuoteMapper,
        GoldPriceService goldPriceService,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        MarketCalendarService marketCalendarService,
        @Value("${finance.investment.sync-concurrency:8}") Integer syncConcurrency,
        MarketQuoteClient marketQuoteClient,
        InvestmentDividendService dividendService,
        InvestmentQuoteSyncService quoteSyncService,
        InvestmentPriceHistoryService priceHistoryService,
        InvestmentFundProfitService fundProfitService,
        InvestmentFundFeeService fundFeeService
    ) {
        this.productMapper = productMapper;
        this.positionMapper = positionMapper;
        this.transactionMapper = transactionMapper;
        this.autoInvestPlanMapper = autoInvestPlanMapper;
        this.dividendPlanMapper = dividendPlanMapper;
        this.dividendRecordMapper = dividendRecordMapper;
        this.fixedExpenseMapper = fixedExpenseMapper;
        this.accountMapper = accountMapper;
        this.accountTypeMapper = accountTypeMapper;
        this.priceQuoteMapper = priceQuoteMapper;
        this.goldPriceService = goldPriceService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.marketCalendarService = marketCalendarService;
        this.marketQuoteClient = marketQuoteClient;
        this.dividendService = dividendService;
        this.quoteSyncService = quoteSyncService;
        this.priceHistoryService = priceHistoryService;
        this.fundProfitService = fundProfitService;
        this.fundFeeService = fundFeeService;
        this.fundSyncExecutor = Executors.newFixedThreadPool(
            syncConcurrency == null || syncConcurrency <= 0 ? 8 : syncConcurrency,
            daemonThreadFactory("invest-sync-"));
    }

    @PreDestroy
    public void shutdownExecutor() {
        fundSyncExecutor.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public List<InvestmentProductResponse> listProducts(String productType, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return fetchExternalProducts(keyword.trim(), productType);
    }

    public InvestmentProductResponse createProduct(InvestmentProductRequest request) {
        InvestmentProductEntity entity = fillProduct(new InvestmentProductEntity(), request);
        productMapper.insert(entity);
        return toProductResponse(productMapper.selectById(entity.getId()));
    }

    public InvestmentDividendForecastResponse dividendForecast(InvestmentDividendForecastRequest request) {
        String productType = normalizeForecastProductType(request.getProductType());
        String symbol = normalizeForecastSymbol(request.getSymbol());
        BigDecimal requestedQuantity = request.getHoldingQuantity() == null
            ? null
            : request.getHoldingQuantity().setScale(6, RoundingMode.HALF_UP);
        BigDecimal requestedAmount = request.getHoldingAmount() == null
            ? null
            : request.getHoldingAmount().setScale(2, RoundingMode.HALF_UP);

        if ((requestedQuantity == null || requestedQuantity.compareTo(BigDecimal.ZERO) <= 0)
            && (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("请输入持仓数量或持仓金额");
        }

        InvestmentProductEntity product = resolveForecastProduct(request, productType, symbol);
        ForecastPriceSnapshot forecastPrice = FUND_PRODUCT_TYPE.equals(productType)
            ? fetchFundForecastPrice(symbol)
            : fetchStockForecastPrice(symbol, request.getExchangeCode());
        LocalDate calculationDate = request.getQuoteDate() != null
            ? request.getQuoteDate()
            : forecastPrice != null && forecastPrice.quoteDate() != null ? forecastPrice.quoteDate() : LocalDate.now();
        LocalDate trailingWindowStart = calculationDate.minusMonths(12);
        int basisYear = calculationDate.getYear();
        List<InvestmentDividendPlanEntity> historicalPlans = FUND_PRODUCT_TYPE.equals(productType)
            ? fetchFundHistoricalDividendPlans(product)
            : fetchStockHistoricalDividendPlans(product);
        List<InvestmentDividendPlanEntity> lastYearPlans = new ArrayList<>(historicalPlans.stream()
            .filter(plan -> {
                LocalDate dividendDate = dividendPlanRecencyDate(plan);
                return dividendDate != null
                    && !dividendDate.isBefore(trailingWindowStart)
                    && !dividendDate.isAfter(calculationDate);
            })
            .collect(Collectors.toMap(
                plan -> dividendPlanRecencyDate(plan) + "|" + resolveNetDividendPerUnit(plan).stripTrailingZeros().toPlainString(),
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ))
            .values());
        lastYearPlans = lastYearPlans.stream()
            .sorted(Comparator.comparing(InvestmentDividendService::dividendPlanRecencyDate))
            .toList();

        long positivePlanCount = lastYearPlans.stream()
            .map(InvestmentDividendService::resolveNetDividendPerUnit)
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .count();
        if (positivePlanCount <= 0) {
            throw new IllegalArgumentException("该资产近12个月暂无可用分红记录");
        }

        BigDecimal lastYearDividendPerUnit = lastYearPlans.stream()
            .map(InvestmentDividendService::resolveNetDividendPerUnit)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(6, RoundingMode.HALF_UP);
        if (lastYearDividendPerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("该资产近12个月暂无可用分红金额");
        }

        BigDecimal currentPrice = forecastPrice != null && forecastPrice.price() != null
            && forecastPrice.price().compareTo(BigDecimal.ZERO) > 0
            ? forecastPrice.price().setScale(6, RoundingMode.HALF_UP)
            : resolveForecastCurrentPrice(productType, symbol, request.getExchangeCode(), request.getLatestPrice());
        BigDecimal estimatedHoldingQuantity = requestedQuantity;
        if ((estimatedHoldingQuantity == null || estimatedHoldingQuantity.compareTo(BigDecimal.ZERO) <= 0)
            && requestedAmount != null && requestedAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("未获取到当前价格，无法按持仓金额估算份额");
            }
            estimatedHoldingQuantity = requestedAmount.divide(currentPrice, 6, RoundingMode.HALF_UP);
        }
        if (estimatedHoldingQuantity == null || estimatedHoldingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("持仓数量不正确");
        }

        BigDecimal estimatedHoldingAmount = requestedAmount != null && requestedAmount.compareTo(BigDecimal.ZERO) > 0
            ? requestedAmount
            : estimatedHoldingQuantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unscaledDividendAmount = estimatedHoldingQuantity.multiply(lastYearDividendPerUnit);
        BigDecimal estimatedDividendAmount = unscaledDividendAmount.setScale(2, RoundingMode.HALF_UP);

        InvestmentDividendForecastResponse response = new InvestmentDividendForecastResponse();
        response.setProductType(productType);
        response.setProductTypeLabel(resolveProductTypeLabel(productType));
        response.setSymbol(product.getSymbol());
        response.setName(product.getName());
        response.setMarket(product.getMarket());
        response.setUnitName(blankToDefault(product.getUnitName(), FUND_PRODUCT_TYPE.equals(productType) ? DEFAULT_UNIT_NAME : "股"));
        response.setCurrentPrice(currentPrice);
        response.setBasisYear(basisYear);
        response.setLastYearDividendCount((int) positivePlanCount);
        response.setLastYearDividendPerUnit(lastYearDividendPerUnit);
        response.setEstimatedHoldingQuantity(scaleQuantity(estimatedHoldingQuantity));
        response.setEstimatedHoldingAmount(scaleMoney(estimatedHoldingAmount));
        response.setEstimatedDividendAmount(scaleMoney(estimatedDividendAmount));
        response.setEstimatedDividendRate(scaleRate(rate(lastYearDividendPerUnit, currentPrice)));
        response.setCalculationNote(String.format(
            "按近12个月 %d 次分红、每%s合计 %s 元估算",
            positivePlanCount,
            blankToDefault(product.getUnitName(), FUND_PRODUCT_TYPE.equals(productType) ? DEFAULT_UNIT_NAME : "股"),
            lastYearDividendPerUnit.setScale(4, RoundingMode.HALF_UP).toPlainString()
        ));
        response.setSource(lastYearPlans.stream()
            .map(InvestmentDividendPlanEntity::getSource)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null));
        return response;
    }

    public InvestmentProductQuoteResponse productQuote(String productType, String symbol, String exchangeCode, String name) {
        String normalizedType = normalizeForecastProductType(productType);
        String normalizedSymbol = normalizeForecastSymbol(symbol);
        InvestmentProductQuoteResponse response = new InvestmentProductQuoteResponse();
        response.setProductType(normalizedType);
        response.setProductTypeLabel(resolveProductTypeLabel(normalizedType));
        response.setSymbol(normalizedSymbol);
        response.setName(blankToDefault(name, normalizedSymbol));
        response.setUnitName(FUND_PRODUCT_TYPE.equals(normalizedType) ? DEFAULT_UNIT_NAME : "股");

        if (FUND_PRODUCT_TYPE.equals(normalizedType)) {
            JsonNode baseInfo = fetchFundBaseInfo(normalizedSymbol).path("Datas");
            ForecastPriceSnapshot price = fetchFundForecastPrice(normalizedSymbol);
            response.setName(blankToDefault(baseInfo.path("SHORTNAME").asText(null), response.getName()));
            response.setSymbol(blankToDefault(baseInfo.path("FCODE").asText(null), normalizedSymbol));
            response.setMarket("FUND");
            if (price == null) {
                BigDecimal navPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
                LocalDate navDate = safeDate(baseInfo.path("FSRQ").asText(null));
                price = navPrice != null && navDate != null ? new ForecastPriceSnapshot(navDate, navPrice) : null;
            }
            response.setLatestPrice(price == null ? null : price.price());
            response.setQuoteDate(price == null ? null : price.quoteDate());
            response.setChangePercent(safeDecimal(baseInfo.path("RZDF").asText(null)));
            response.setSource("新浪财经");
            return response;
        }

        JsonNode quote = fetchTencentQuoteFields(toTencentSymbol(normalizedSymbol, exchangeCode));
        LocalDate quoteDate = resolveStockQuoteDate(quote.path("timeRaw").asText(null));
        BigDecimal latestPrice = safeDecimal(quote.path("price").asText(null));
        if ((latestPrice == null || latestPrice.compareTo(BigDecimal.ZERO) <= 0)
            && (quoteDate == null || !quoteDate.equals(LocalDate.now()))) {
            BigDecimal previousClose = safeDecimal(quote.path("prevClose").asText(null));
            if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) {
                latestPrice = previousClose;
                quoteDate = quoteDate == null ? LocalDate.now().minusDays(1) : quoteDate.minusDays(1);
            }
        }
        response.setName(blankToDefault(quote.path("name").asText(null), response.getName()));
        response.setMarket("CN");
        response.setLatestPrice(latestPrice);
        response.setChange(safeDecimal(quote.path("change").asText(null)));
        response.setChangePercent(safeDecimal(quote.path("changePercent").asText(null)));
        response.setQuoteDate(quoteDate);
        response.setSource("腾讯行情");
        return response;
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
        // 次级费率数据独立加载，详情首屏仅依赖本地持仓。
        response.setFundRedeemFeeOptions(Collections.emptyList());
        response.setSource("本地持仓");
        response.setDescription(isPendingFundSubscription(position)
            ? "该基金按场外申购规则处理中，份额将在确认后生成。"
            : "页面先展示本地持仓数据，行情和走势单独加载。");
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

    public InvestmentPositionResponse createPosition(InvestmentPositionRequest request) {
        AccountEntity account = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        AccountEntity fundingAccount = resolveFundingAccountForCreate(request, account);
        InvestmentProductEntity product = request.getProductId() != null
            ? requireProduct(request.getProductId())
            : createOrLoadProduct(request.getProduct());

        // 行情 HTTP 调用放在事务外，避免最长 20s 的远端调用占用 DB 连接与行锁
        final JsonNode baseInfo;
        if (isFundSubscriptionProduct(product)) {
            baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        } else {
            baseInfo = null;
        }
        return transactionTemplate.execute(status ->
            doCreatePosition(request, account, fundingAccount, product, baseInfo));
    }

    private InvestmentPositionResponse doCreatePosition(
        InvestmentPositionRequest request,
        AccountEntity account,
        AccountEntity fundingAccount,
        InvestmentProductEntity product,
        JsonNode baseInfo
    ) {
        accountMapper.lockUserForWrite(request.getUserId());
        account = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        fundingAccount = resolveFundingAccountForCreate(request, account);
        if (isFundSubscriptionProduct(product)) {
            return createFundSubscriptionPosition(request, account, fundingAccount, product, baseInfo);
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
        accountMapper.lockUserForWrite(request.getUserId());
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
            if (isFundSubscriptionProduct(product)
                && (StringUtils.hasText(request.getDividendType()) || request.getFundingAccountId() != null)) {
                applyDividendPreference(entity, request.getDividendType(), request.getFundingAccountId());
            }
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
        accountMapper.lockUserForWrite(userId);
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
        return buildSummaryResponse(userId, accountId, loadActiveInvestmentPositions(userId, accountId));
    }

    public InvestmentTrendResponse trend(Long userId, Long accountId, String range) {
        List<InvestmentPositionEntity> positions = loadActiveInvestmentPositions(userId, accountId);
        InvestmentSummaryResponse summary = buildSummaryResponse(userId, accountId, positions);
        TrendRangeMeta rangeMeta = resolveTrendRange(range, userId, accountId, positions);

        Map<Long, InvestmentProductEntity> products = positions.isEmpty()
            ? Collections.emptyMap()
            : productMapper.selectByIds(positions.stream()
                .map(InvestmentPositionEntity::getProductId)
                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
        Map<Long, List<InvestmentTransactionEntity>> transactionsByPositionId = priceHistoryService.loadConfirmedTransactionsByPosition(userId, accountId, positions);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceHistoryByProductId = priceHistoryService.loadPriceHistoryByProduct(products.keySet());
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

    private InvestmentSummaryResponse buildSummaryResponse(Long userId, Long accountId, List<InvestmentPositionEntity> positions) {
        BigDecimal totalMarketValue = positions.stream()
            .map(this::resolvePositionBalanceForAccount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(sumPendingBuyTransactionAmount(userId, accountId))
            .setScale(2, RoundingMode.HALF_UP);
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
            BigDecimal quantity = priceHistoryService.resolvePositionQuantityAtDate(position, targetDate, transactionsByPositionId.get(position.getId()));
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal price = priceHistoryService.resolvePositionPriceAtDate(position, targetDate, priceHistoryByProductId.get(position.getProductId()));
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
            BigDecimal startQuantity = priceHistoryService.resolvePositionQuantityAtDate(position, rangeMeta.startDate(), transactions);
            BigDecimal endQuantity = priceHistoryService.resolvePositionQuantityAtDate(position, rangeMeta.endDate(), transactions);
            BigDecimal startPrice = priceHistoryService.resolvePositionPriceAtDate(position, rangeMeta.startDate(), priceHistoryByProductId.get(position.getProductId()));
            BigDecimal endPrice = priceHistoryService.resolvePositionPriceAtDate(position, rangeMeta.endDate(), priceHistoryByProductId.get(position.getProductId()));
            BigDecimal startValue = startQuantity.multiply(startPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal endValue = endQuantity.multiply(endPrice).setScale(2, RoundingMode.HALF_UP);

            BigDecimal cashIn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal cashOut = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            for (InvestmentTransactionEntity transaction : transactions) {
                LocalDate effectiveDate = priceHistoryService.resolveTransactionEffectiveDate(transaction);
                if (effectiveDate == null || effectiveDate.isBefore(rangeMeta.startDate()) || effectiveDate.isAfter(rangeMeta.endDate())) {
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

    public int syncDailyFundProfits() {
        return syncDailyFundProfits(null, null);
    }

    public int syncDailyFundProfits(Long userId, Long accountId) {
        return syncDailyFundProfits(userId, accountId, null);
    }

    private int syncDailyFundProfits(Long userId, Long accountId, Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
        List<InvestmentPositionEntity> positions = loadActiveInvestmentPositions(userId, accountId).stream()
            .filter(position -> !isPendingFundSubscription(position) || position.getProductId() != null)
            .toList();
        return syncDailyFundProfits(positions, userId, accountId, sharedFundQuotes);
    }

    private int syncDailyFundProfits(List<InvestmentPositionEntity> positions, Long userId, Long accountId,
        Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
        if (positions.isEmpty()) {
            log.info("基金收益同步跳过：当前没有基金持仓，userId={}, accountId={}", userId, accountId);
            return 0;
        }

        Map<Long, InvestmentProductEntity> products = loadFundProducts(positions);
        if (products.isEmpty()) {
            log.info("基金收益同步跳过：当前没有可同步的基金产品，userId={}, accountId={}", userId, accountId);
            return 0;
        }

        // 按产品并发同步：每个产品的行情抓取+持仓更新互不依赖，DB 写入经 Hikari 池化
        Map<Long, Long> accountUsers = new ConcurrentHashMap<>();
        AtomicInteger syncedProducts = new AtomicInteger();
        AtomicInteger syncedPositions = new AtomicInteger();
        List<CompletableFuture<Void>> futures = groupPositionsByProduct(positions, products).entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                InvestmentProductEntity product = products.get(entry.getKey());
                if (product == null) {
                    return;
                }
                try {
                    int updatedCount = syncFundProfitForProduct(product, entry.getValue(), accountUsers, sharedFundQuotes);
                    if (updatedCount > 0) {
                        syncedProducts.incrementAndGet();
                        syncedPositions.addAndGet(updatedCount);
                    }
                } catch (Exception ex) {
                    log.warn("基金收益同步失败，productId={}, symbol={}, reason={}",
                        product.getId(), product.getSymbol(), ex.getMessage());
                }
            }, fundSyncExecutor))
            .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        accountUsers.forEach((targetAccountId, targetUserId) -> syncInvestmentAccountBalance(targetUserId, targetAccountId));
        log.info("基金收益同步完成：{} 个基金产品，{} 条持仓已更新，userId={}, accountId={}",
            syncedProducts.get(), syncedPositions.get(), userId, accountId);
        return syncedPositions.get();
    }

    public int syncFundDividendPlans() {
        return dividendService.syncFundDividendPlans();
    }

    public int syncStockDividendProfiles() {
        return dividendService.syncStockDividendProfiles();
    }

    private int syncFundDividendPlansForProduct(InvestmentProductEntity product) {
        return dividendService.syncFundDividendPlansForProduct(product);
    }

    private List<InvestmentDividendPlanEntity> fetchFundHistoricalDividendPlans(InvestmentProductEntity product) {
        return dividendService.fetchFundHistoricalDividendPlans(product);
    }

    private List<InvestmentDividendPlanEntity> fetchStockHistoricalDividendPlans(InvestmentProductEntity product) {
        return dividendService.fetchStockHistoricalDividendPlans(product);
    }

    private FundQuoteSnapshot fetchAndSaveLatestFundQuote(InvestmentProductEntity product,
        Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
        return quoteSyncService.fetchAndSaveLatestFundQuote(product, sharedFundQuotes);
    }

    private StockQuoteSnapshot fetchAndSaveLatestStockQuote(InvestmentProductEntity product) {
        return quoteSyncService.fetchAndSaveLatestStockQuote(product);
    }

    private void applyFundQuoteToPosition(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
        quoteSyncService.applyFundQuoteToPosition(position, latestPrice, preClosePrice, syncedAt);
    }

    private void applyStockQuoteToPosition(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
        quoteSyncService.applyStockQuoteToPosition(position, latestPrice, preClosePrice, syncedAt);
    }

    private void updatePendingFundPositionSnapshot(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        LocalDateTime syncedAt
    ) {
        quoteSyncService.updatePendingFundPositionSnapshot(position, latestPrice, syncedAt);
    }

    private InvestmentPriceQuoteEntity findSettlementQuoteByProductAndDate(Long productId, LocalDate quoteDate) {
        return quoteSyncService.findSettlementQuoteByProductAndDate(productId, quoteDate);
    }

    private BigDecimal resolveConfirmedFundPrice(InvestmentPriceQuoteEntity appliedQuote) {
        return quoteSyncService.resolveConfirmedFundPrice(appliedQuote);
    }

    private InvestmentPriceQuoteEntity resolveImmediateSettlementQuote(
        InvestmentProductEntity product,
        JsonNode baseInfo,
        LocalDate appliedDate,
        LocalDate expectedConfirmDate,
        LocalDateTime syncedAt
    ) {
        return quoteSyncService.resolveImmediateSettlementQuote(product, baseInfo, appliedDate, expectedConfirmDate, syncedAt);
    }

    private JsonNode fetchFundBaseInfo(String code) {
        return quoteSyncService.fetchFundBaseInfo(code);
    }

    private JsonNode fetchFundEstimateInfo(String code) {
        return quoteSyncService.fetchFundEstimateInfo(code);
    }

    private JsonNode fetchTencentQuoteFields(String symbol) {
        return quoteSyncService.fetchTencentQuoteFields(symbol);
    }

    public Map<String, Object> runNightlyInvestmentSyncCycle(String trigger) {
        return runInvestmentSyncCycle(trigger, null, null, true);
    }

    public Map<String, Object> runInvestmentSyncCycle(String trigger, Long userId, Long accountId) {
        return runInvestmentSyncCycle(trigger, userId, accountId, false);
    }

    private Map<String, Object> runInvestmentSyncCycle(
        String trigger,
        Long userId,
        Long accountId,
        boolean evaluateDividendIncomeEligibility
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        // 同步周期内共享的基金净值快照：净值同步与结算对同一基金只抓一次行情
        Map<Long, FundQuoteSnapshot> sharedFundQuotes = new ConcurrentHashMap<>();
        int syncedFundPositions = syncDailyFundProfits(userId, accountId, sharedFundQuotes);
        int syncedStockPositions = syncDailyStockQuotes(userId, accountId);
        int syncedDividendPlans = evaluateDividendIncomeEligibility ? syncFundDividendPlans() : 0;
        int syncedStockDividendProfiles = evaluateDividendIncomeEligibility ? syncStockDividendProfiles() : 0;
        int settledCount = (userId == null && accountId == null) ? settlePendingFundTrades(sharedFundQuotes) : 0;
        LocalDateTime finishedAt = LocalDateTime.now();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trigger", StringUtils.hasText(trigger) ? trigger : "unknown");
        summary.put("userId", userId);
        summary.put("accountId", accountId);
        summary.put("startedAt", startedAt);
        summary.put("finishedAt", finishedAt);
        summary.put("durationMs", Duration.between(startedAt, finishedAt).toMillis());
        summary.put("syncedPositions", syncedFundPositions + syncedStockPositions);
        summary.put("syncedFundPositions", syncedFundPositions);
        summary.put("syncedStockPositions", syncedStockPositions);
        summary.put("syncedDividendPlans", syncedDividendPlans);
        summary.put("syncedStockDividendProfiles", syncedStockDividendProfiles);
        summary.put("settledCount", settledCount);
        return summary;
    }

    @Async
    public void runInvestmentSyncCycleAsync(String trigger, Long userId, Long accountId) {
        try {
            Map<String, Object> summary = runInvestmentSyncCycle(trigger, userId, accountId);
            log.info("投资行情异步同步完成：summary={}", summary);
        } catch (Exception ex) {
            log.error("投资行情异步同步失败：trigger={}, userId={}, accountId={}", trigger, userId, accountId, ex);
        }
    }

    public int settlePendingFundTrades() {
        return settlePendingFundTradesAt(LocalDateTime.now());
    }

    private int settlePendingFundTrades(Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
        return settlePendingFundTradesAt(LocalDateTime.now(), sharedFundQuotes);
    }

    public int syncDailyStockQuotes() {
        return syncDailyStockQuotes(null, null);
    }

    public int syncDailyStockQuotes(Long userId, Long accountId) {
        List<InvestmentPositionEntity> positions = loadActiveInvestmentPositions(userId, accountId);
        return syncDailyStockQuotes(positions, userId, accountId);
    }

    private int syncDailyStockQuotes(List<InvestmentPositionEntity> positions, Long userId, Long accountId) {
        if (positions.isEmpty()) {
            log.info("股票行情同步跳过：当前没有股票持仓，userId={}, accountId={}", userId, accountId);
            return 0;
        }

        Map<Long, InvestmentProductEntity> products = loadStockProducts(positions);
        if (products.isEmpty()) {
            log.info("股票行情同步跳过：当前没有可同步的股票产品，userId={}, accountId={}", userId, accountId);
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
                int updatedCount = syncStockQuoteForProduct(product, entry.getValue(), accountUsers);
                if (updatedCount > 0) {
                    syncedProducts++;
                    syncedPositions += updatedCount;
                }
            } catch (Exception ex) {
                log.warn("股票行情同步失败，productId={}, symbol={}, reason={}",
                    product.getId(), product.getSymbol(), ex.getMessage());
            }
        }

        accountUsers.forEach((targetAccountId, targetUserId) -> syncInvestmentAccountBalance(targetUserId, targetAccountId));
        log.info("股票行情同步完成：{} 个股票产品，{} 条持仓已更新，userId={}, accountId={}",
            syncedProducts, syncedPositions, userId, accountId);
        return syncedPositions;
    }

    public int settlePendingFundTradesAt(LocalDateTime settlementDateTime) {
        return settlePendingFundTradesAt(settlementDateTime, null);
    }

    private int settlePendingFundTradesAt(LocalDateTime settlementDateTime, Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
        int settledPositions = settlePendingFundPositions(settlementDateTime, sharedFundQuotes);
        int settledTransactions = settlePendingFundTransactions(settlementDateTime, sharedFundQuotes);
        return settledPositions + settledTransactions;
    }

    private int settlePendingFundPositions(LocalDateTime settlementDateTime, Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
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
        int settledProducts = 0;
        int settledPositions = 0;
        for (Map.Entry<Long, List<InvestmentPositionEntity>> entry : groupPositionsByProduct(positions, products).entrySet()) {
            InvestmentProductEntity product = products.get(entry.getKey());
            if (product == null) {
                continue;
            }
            try {
                int updatedCount = settlePendingFundTradesForProduct(product, entry.getValue(), accountUsers, settlementDateTime, sharedFundQuotes);
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

    private int settlePendingFundTransactions(LocalDateTime settlementDateTime, Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
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
                int updatedCount = settlePendingFundTransactionsForProduct(product, entry.getValue(), accountUsers, settlementDateTime, sharedFundQuotes);
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
        return listTransactions(userId, accountId, positionId, null);
    }

    public List<InvestmentTransactionResponse> listTransactions(
        Long userId,
        Long accountId,
        Long positionId,
        String settlementStatus
    ) {
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(
            buildTransactionQuery(userId, accountId, positionId, settlementStatus)
                .orderByDesc(InvestmentTransactionEntity::getTradeAt)
                .orderByDesc(InvestmentTransactionEntity::getId));
        if (transactions.isEmpty() && positionId != null && !StringUtils.hasText(settlementStatus)) {
            return inferInitialTransaction(userId, accountId, positionId);
        }
        return mergeMissingPendingInitialTransaction(
            toTransactionResponses(transactions),
            userId,
            accountId,
            positionId
        );
    }

    public InvestmentTransactionPageResponse pageTransactions(Long userId, Long accountId, Long positionId, Integer page, Integer pageSize) {
        int resolvedPageSize = sanitizePageSize(pageSize);
        int resolvedPage = Math.max(1, page == null ? 1 : page);
        if (positionId != null) {
            // 单持仓分支同样下推到 SQL 分页（持仓交易可能跨多年，避免全量捞取内存截取）
            Long positionTotalCount = transactionMapper.selectCount(buildTransactionQuery(userId, accountId, positionId));
            long positionTotal = positionTotalCount == null ? 0 : positionTotalCount;
            if (positionTotal == 0) {
                List<InvestmentTransactionResponse> inferredTransactions = inferInitialTransaction(userId, accountId, positionId);
                return buildTransactionPageResponse(inferredTransactions, inferredTransactions.size(), resolvedPage, resolvedPageSize);
            }
            int positionTotalPages = Math.max(1, (int) Math.ceil(positionTotal / (double) resolvedPageSize));
            int positionPage = Math.min(resolvedPage, positionTotalPages);
            long positionOffset = (long) (positionPage - 1) * resolvedPageSize;
            List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(buildTransactionQuery(userId, accountId, positionId)
                .orderByDesc(InvestmentTransactionEntity::getTradeAt)
                .orderByDesc(InvestmentTransactionEntity::getId)
                .last("LIMIT " + positionOffset + ", " + resolvedPageSize));
            List<InvestmentTransactionResponse> items = mergeMissingPendingInitialTransaction(
                toTransactionResponses(transactions),
                userId,
                accountId,
                positionId
            );
            return buildTransactionPageResponse(items, positionTotal, resolvedPage, resolvedPageSize);
        }
        Long totalCount = transactionMapper.selectCount(buildTransactionQuery(userId, accountId, positionId));
        long total = totalCount == null ? 0 : totalCount;

        if (total == 0 && positionId != null) {
            List<InvestmentTransactionResponse> inferredTransactions = inferInitialTransaction(userId, accountId, positionId);
            return buildTransactionPageResponse(inferredTransactions, inferredTransactions.size(), resolvedPage, resolvedPageSize);
        }

        int totalPages = Math.max(1, (int) Math.ceil(total / (double) resolvedPageSize));
        resolvedPage = Math.min(resolvedPage, totalPages);
        long offset = (long) (resolvedPage - 1) * resolvedPageSize;
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(buildTransactionQuery(userId, accountId, positionId)
            .orderByDesc(InvestmentTransactionEntity::getTradeAt)
            .orderByDesc(InvestmentTransactionEntity::getId)
            .last("LIMIT " + offset + ", " + resolvedPageSize));
        return buildTransactionPageResponse(toTransactionResponses(transactions), total, resolvedPage, resolvedPageSize);
    }

    private LambdaQueryWrapper<InvestmentTransactionEntity> buildTransactionQuery(Long userId, Long accountId, Long positionId) {
        return buildTransactionQuery(userId, accountId, positionId, null);
    }

    private LambdaQueryWrapper<InvestmentTransactionEntity> buildTransactionQuery(
        Long userId,
        Long accountId,
        Long positionId,
        String settlementStatus
    ) {
        return new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(userId != null, InvestmentTransactionEntity::getUserId, userId)
            .eq(accountId != null, InvestmentTransactionEntity::getAccountId, accountId)
            .eq(positionId != null, InvestmentTransactionEntity::getPositionId, positionId)
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            // 结算状态过滤：列表页只关心 pending 金额时下推到 SQL，避免全量捞取
            .eq(StringUtils.hasText(settlementStatus), InvestmentTransactionEntity::getSettlementStatus, settlementStatus);
    }

    private int sanitizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private InvestmentTransactionPageResponse buildTransactionPageResponse(
        List<InvestmentTransactionResponse> items,
        long total,
        int page,
        int pageSize
    ) {
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int resolvedPage = Math.min(Math.max(1, page), totalPages);
        int fromIndex = total == 0 ? 0 : Math.min((resolvedPage - 1) * pageSize, items.size());
        int toIndex = total == 0 ? 0 : Math.min(fromIndex + pageSize, items.size());
        InvestmentTransactionPageResponse response = new InvestmentTransactionPageResponse();
        response.setItems(items.size() > pageSize ? items.subList(fromIndex, toIndex) : items);
        response.setTotal(total);
        response.setPage(resolvedPage);
        response.setPageSize(pageSize);
        response.setTotalPages(totalPages);
        return response;
    }

    public InvestmentTransactionResponse createTransaction(InvestmentTransactionRequest request) {
        AccountEntity investmentAccount = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        InvestmentProductEntity product = requireProduct(request.getProductId());
        requirePosition(request);
        // 行情 HTTP 调用放在事务外，避免最长 20s 的远端调用占用 DB 连接与行锁
        final JsonNode baseInfo;
        if (isFundSubscriptionProduct(product)) {
            baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        } else {
            baseInfo = null;
        }
        List<FundRedeemFeeRule> feeRules = isFundSubscriptionProduct(product)
            && "sell".equals(request.getTradeType()) && request.getFeeAmount() == null
            ? fundFeeService.fetchFundRedeemFeeRules(product.getSymbol()) : List.of();
        return transactionTemplate.execute(status ->
            doCreateTransaction(request, investmentAccount, product, baseInfo, feeRules));
    }

    private InvestmentTransactionResponse doCreateTransaction(
        InvestmentTransactionRequest request,
        AccountEntity investmentAccount,
        InvestmentProductEntity product,
        JsonNode baseInfo,
        List<FundRedeemFeeRule> feeRules
    ) {
        accountMapper.lockUserForWrite(request.getUserId());
        InvestmentPositionEntity position = requirePosition(request);
        investmentAccount = requireInvestmentAccount(request.getUserId(), request.getAccountId());
        if (isFundSubscriptionProduct(product)) {
            if ("buy".equals(request.getTradeType())) {
                applyDividendPreference(position, request.getDividendType(), request.getFundingAccountId());
                positionMapper.updateById(position);
            }
            return createPendingFundTransaction(request, investmentAccount, position, product, baseInfo, feeRules);
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

    @Transactional
    public boolean deleteTransaction(Long id, Long userId) {
        accountMapper.lockUserForWrite(userId);
        InvestmentTransactionEntity entity = transactionMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId()) || !NORMAL_STATUS.equals(entity.getStatus())
            || !SETTLEMENT_STATUS_PENDING.equals(entity.getSettlementStatus())) {
            return false;
        }
        rollbackPendingTransaction(entity);
        entity.setStatus(VOIDED_STATUS);
        transactionMapper.updateById(entity);
        syncInvestmentAccountBalance(entity.getUserId(), entity.getAccountId());
        return true;
    }

    public Optional<InvestmentTransactionResponse> updatePendingTransaction(Long id, InvestmentTransactionRequest request) {
        InvestmentTransactionEntity entity = transactionMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())
            || !NORMAL_STATUS.equals(entity.getStatus())
            || !SETTLEMENT_STATUS_PENDING.equals(entity.getSettlementStatus())) {
            return Optional.empty();
        }
        if (!Objects.equals(entity.getAccountId(), request.getAccountId())
            || !Objects.equals(entity.getPositionId(), request.getPositionId())
            || !Objects.equals(entity.getProductId(), request.getProductId())
            || !Objects.equals(entity.getTradeType(), request.getTradeType())) {
            throw new IllegalArgumentException("待确认交易的账户、持仓、产品和类型不可修改");
        }
        InvestmentProductEntity product = requireProduct(entity.getProductId());
        // 行情 HTTP 调用放在事务外，避免最长 20s 的远端调用占用 DB 连接与行锁
        int confirmDelayDays = resolveFundConfirmDelayDays(fetchFundBaseInfo(product.getSymbol()), product);
        return transactionTemplate.execute(status -> doUpdatePendingTransaction(id, request, product, confirmDelayDays));
    }

    private Optional<InvestmentTransactionResponse> doUpdatePendingTransaction(Long id, InvestmentTransactionRequest request,
        InvestmentProductEntity product, int confirmDelayDays) {
        accountMapper.lockUserForWrite(request.getUserId());
        InvestmentTransactionEntity entity = transactionMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())
            || !NORMAL_STATUS.equals(entity.getStatus())
            || !SETTLEMENT_STATUS_PENDING.equals(entity.getSettlementStatus())) {
            return Optional.empty();
        }
        requirePosition(request);
        rollbackPendingTransaction(entity);
        InvestmentPositionEntity position = requirePosition(request);
        if (isFundSubscriptionProduct(product) && "buy".equals(entity.getTradeType())) {
            applyDividendPreference(position, request.getDividendType(), request.getFundingAccountId());
        }

        BigDecimal amount = defaultZero(request.getAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeAmount = (request.getFeeAmount() == null ? defaultZero(entity.getFeeAmount()) : request.getFeeAmount())
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = (request.getTaxAmount() == null ? defaultZero(entity.getTaxAmount()) : request.getTaxAmount())
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal quantity = defaultZero(request.getQuantity()).setScale(6, RoundingMode.HALF_UP);
        if ("buy".equals(entity.getTradeType())) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("申购金额必须大于0");
            deductFundingAccount(requireCashFundingAccount(request.getUserId(), request.getFundingAccountId()), amount.add(feeAmount).add(taxAmount));
        } else if ("sell".equals(entity.getTradeType())) {
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("赎回份额必须大于0");
            freezePendingFundSellQuantity(position, quantity);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("回款金额必须大于0");
        } else {
            throw new IllegalArgumentException("仅支持修改待确认买入或卖出");
        }
        entity.setQuantity(quantity);
        entity.setPrice(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        entity.setAmount(amount);
        entity.setFeeAmount(feeAmount);
        entity.setTaxAmount(taxAmount);
        entity.setFundingAccountId(request.getFundingAccountId());
        entity.setTradeAt(request.getTradeAt());
        entity.setSettlementAppliedDate(resolveFundSubscriptionTradeDate(request.getSubscriptionTimeSlot(), request.getTradeAt()));
        entity.setSettlementExpectedDate(resolveFundExpectedConfirmDate(entity.getSettlementAppliedDate(), confirmDelayDays));
        entity.setRemark(request.getRemark());
        transactionMapper.updateById(entity);
        positionMapper.updateById(position);
        syncInvestmentAccountBalance(entity.getUserId(), entity.getAccountId());
        return Optional.of(toTransactionResponse(entity, product, accountMapper.selectById(entity.getAccountId())));
    }

    private void rollbackPendingTransaction(InvestmentTransactionEntity entity) {
        if ("buy".equals(entity.getTradeType())) {
            if (entity.getFundingAccountId() != null) {
                creditFundingAccount(requireCashFundingAccount(entity.getUserId(), entity.getFundingAccountId()),
                    defaultZero(entity.getAmount()).add(defaultZero(entity.getFeeAmount())).add(defaultZero(entity.getTaxAmount())));
            }
        } else if ("sell".equals(entity.getTradeType()) && entity.getPositionId() != null) {
            InvestmentPositionEntity position = positionMapper.selectById(entity.getPositionId());
            if (position != null) {
                BigDecimal quantity = defaultZero(entity.getQuantity()).setScale(6, RoundingMode.HALF_UP);
                position.setAvailableQuantity(defaultZero(position.getAvailableQuantity()).add(quantity).setScale(6, RoundingMode.HALF_UP));
                position.setFrozenQuantity(defaultZero(position.getFrozenQuantity()).subtract(quantity).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP));
                positionMapper.updateById(position);
            }
        }
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
        accountMapper.lockUserForWrite(request.getUserId());
        InvestmentPositionEntity position = requireAutoInvestPosition(request.getUserId(), request.getPositionId());
        validateAutoInvestPlanRequest(request, position, null);

        InvestmentAutoInvestPlanEntity entity = new InvestmentAutoInvestPlanEntity();
        fillAutoInvestPlan(entity, request, position);
        autoInvestPlanMapper.insert(entity);
        return toAutoInvestPlanResponse(autoInvestPlanMapper.selectById(entity.getId()));
    }

    @Transactional
    public Optional<InvestmentAutoInvestPlanResponse> updateAutoInvestPlan(Long id, InvestmentAutoInvestPlanRequest request) {
        accountMapper.lockUserForWrite(request.getUserId());
        InvestmentAutoInvestPlanEntity entity = autoInvestPlanMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }
        InvestmentPositionEntity position = requireAutoInvestPosition(request.getUserId(), request.getPositionId());
        validateAutoInvestPlanRequest(request, position, id);

        // 恢复定投时，若下次执行日期已过期，则按定投周期从原锚点顺延到今天之后的下一个执行日，
        // 避免恢复后立即补执行暂停期间错过的期次
        boolean resumingFromPause = AUTO_INVEST_STATUS_PAUSED.equals(entity.getStatus())
            && AUTO_INVEST_STATUS_ACTIVE.equals(request.getStatus());
        if (resumingFromPause && request.getNextExecuteDate().isBefore(LocalDate.now())) {
            LocalDate nextExecuteDate = resolveNextAutoInvestExecuteDate(
                request.getFrequency(), request.getNextExecuteDate(), LocalDate.now());
            log.info("基金定投恢复：planId={}, 原下次执行日期={}, 顺延后={}", id, request.getNextExecuteDate(), nextExecuteDate);
            request.setNextExecuteDate(nextExecuteDate);
        }

        fillAutoInvestPlan(entity, request, position);
        autoInvestPlanMapper.updateById(entity);
        return Optional.of(toAutoInvestPlanResponse(autoInvestPlanMapper.selectById(id)));
    }

    @Transactional
    public boolean deleteAutoInvestPlan(Long id, Long userId) {
        accountMapper.lockUserForWrite(userId);
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
                executedCount += executeAutoInvestPlan(plan.getId(), today);
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

    public List<InvestmentFixedExpenseResponse> listFixedExpenses(Long userId) {
        return fixedExpenseMapper.selectList(new LambdaQueryWrapper<InvestmentFixedExpenseEntity>()
                .eq(InvestmentFixedExpenseEntity::getUserId, userId)
                .eq(InvestmentFixedExpenseEntity::getStatus, ACTIVE_STATUS)
                .orderByAsc(InvestmentFixedExpenseEntity::getSortOrder)
                .orderByAsc(InvestmentFixedExpenseEntity::getId))
            .stream()
            .map(this::toFixedExpenseResponse)
            .toList();
    }

    @Transactional
    public InvestmentFixedExpenseResponse createFixedExpense(InvestmentFixedExpenseRequest request) {
        InvestmentFixedExpenseEntity entity = fillFixedExpenseEntity(new InvestmentFixedExpenseEntity(), request);
        entity.setStatus(ACTIVE_STATUS);
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : resolveNextFixedExpenseSortOrder(request.getUserId()));
        fixedExpenseMapper.insert(entity);
        return toFixedExpenseResponse(fixedExpenseMapper.selectById(entity.getId()));
    }

    @Transactional
    public Optional<InvestmentFixedExpenseResponse> updateFixedExpense(Long id, InvestmentFixedExpenseRequest request) {
        InvestmentFixedExpenseEntity entity = fixedExpenseMapper.selectOne(new LambdaQueryWrapper<InvestmentFixedExpenseEntity>()
            .eq(InvestmentFixedExpenseEntity::getId, id)
            .eq(InvestmentFixedExpenseEntity::getUserId, request.getUserId())
            .eq(InvestmentFixedExpenseEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1"));
        if (entity == null) {
            return Optional.empty();
        }
        fillFixedExpenseEntity(entity, request);
        fixedExpenseMapper.updateById(entity);
        return Optional.of(toFixedExpenseResponse(fixedExpenseMapper.selectById(entity.getId())));
    }

    @Transactional
    public boolean deleteFixedExpense(Long id, Long userId) {
        return fixedExpenseMapper.update(null, new LambdaUpdateWrapper<InvestmentFixedExpenseEntity>()
            .set(InvestmentFixedExpenseEntity::getStatus, DELETED_STATUS)
            .eq(InvestmentFixedExpenseEntity::getId, id)
            .eq(InvestmentFixedExpenseEntity::getUserId, userId)
            .eq(InvestmentFixedExpenseEntity::getStatus, ACTIVE_STATUS)) > 0;
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
            .filter(item -> defaultZero(item.getEstimatedDividendRate()).compareTo(MIN_DIVIDEND_INCOME_RATE) >= 0)
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
        response.setFixedExpenses(listFixedExpenses(userId));
        response.setUpdatedAt(updatedAt);
        return response;
    }

    public InvestmentDividendIncomePageResponse refreshDividendIncome(Long userId) {
        List<InvestmentPositionEntity> sourcePositions = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getUserId, userId)
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS));
        List<InvestmentPositionEntity> positions = filterPositionsByAccountType(sourcePositions, INVESTMENT_ACCOUNT_TYPE_CODE);
        if (!positions.isEmpty()) {
            Map<Long, InvestmentProductEntity> products = productMapper.selectByIds(positions.stream()
                    .map(InvestmentPositionEntity::getProductId)
                    .collect(Collectors.toSet()))
                .stream()
                .filter(InvestmentDividendService::supportsDividendProfile)
                .collect(Collectors.toMap(InvestmentProductEntity::getId, Function.identity()));
            // 东方财富对外部抓取有频率限制，并发或高频请求会返回 514 反爬状态码，
            // 因此按产品串行逐个刷新并在请求间留出间隔（与夜间定时同步的节奏一致）。
            List<InvestmentProductEntity> evaluatedProducts = new ArrayList<>();
            for (InvestmentProductEntity product : products.values()) {
                try {
                    if (FUND_PRODUCT_TYPE.equals(product.getProductType())) {
                        syncFundDividendPlansForProduct(product);
                        Thread.sleep(400);
                        continue;
                    }
                    if (dividendService.evaluateDividendProfile(product)) {
                        evaluatedProducts.add(product);
                    }
                } catch (Exception ex) {
                    log.warn("攒股收息资格刷新失败，productId={}, symbol={}, reason={}",
                        product.getId(), product.getSymbol(), ex.getMessage());
                }
            }
            evaluatedProducts.forEach(productMapper::updateById);
        }
        return dividendIncome(userId);
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
            return exists;
        }
        InvestmentProductEntity entity = fillProduct(new InvestmentProductEntity(), request);
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
        InvestmentProductEntity product = productMapper.selectById(productId);
        if (isFundSubscriptionProduct(product)) {
            boolean hasDividendPreference = StringUtils.hasText(request.getDividendType())
                || request.getFundingAccountId() != null
                || !StringUtils.hasText(entity.getDividendType());
            if (hasDividendPreference) {
                String dividendType = resolveDividendType(request.getDividendType());
                Long dividendFundingAccountId = resolveDividendFundingAccountId(
                    request.getUserId(),
                    request.getDividendType(),
                    request.getFundingAccountId()
                );
                if (DIVIDEND_TYPE_CASH.equals(dividendType) && dividendFundingAccountId == null) {
                    dividendFundingAccountId = entity.getDividendFundingAccountId();
                }
                entity.setDividendType(dividendType);
                entity.setDividendFundingAccountId(dividendFundingAccountId);
            }
        } else {
            entity.setDividendType(DIVIDEND_TYPE_CASH);
            entity.setDividendFundingAccountId(null);
        }
        entity.setLastSyncedAt(LocalDateTime.now());
        entity.setRemark(request.getRemark());
    }

    private String resolveDividendType(String dividendType) {
        if (DIVIDEND_TYPE_REINVEST.equalsIgnoreCase(dividendType)) {
            return DIVIDEND_TYPE_REINVEST;
        }
        return DIVIDEND_TYPE_CASH;
    }

    private Long resolveDividendFundingAccountId(Long userId, String dividendType, Long fundingAccountId) {
        if (!DIVIDEND_TYPE_CASH.equals(resolveDividendType(dividendType))) {
            return null;
        }
        if (fundingAccountId == null) {
            return null;
        }
        requireCashFundingAccount(userId, fundingAccountId);
        return fundingAccountId;
    }

    private void applyDividendPreference(
        InvestmentPositionEntity position,
        String dividendType,
        Long fundingAccountId
    ) {
        String resolvedType = resolveDividendType(dividendType);
        Long resolvedFundingAccountId = resolveDividendFundingAccountId(position.getUserId(), resolvedType, fundingAccountId);
        if (DIVIDEND_TYPE_CASH.equals(resolvedType) && resolvedFundingAccountId == null) {
            resolvedFundingAccountId = position.getDividendFundingAccountId();
        }
        if (DIVIDEND_TYPE_CASH.equals(resolvedType) && resolvedFundingAccountId == null) {
            throw new IllegalArgumentException("现金分红必须选择资金账户");
        }
        position.setDividendType(resolvedType);
        position.setDividendFundingAccountId(resolvedFundingAccountId);
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

    private int executeAutoInvestPlan(Long planId, LocalDate today) {
        int executedCount = 0;
        while (true) {
            // 事务边界在 executeSingleAutoInvestPlan 内部：行情 HTTP 在事务外，DB 写入在事务内
            Integer executed = executeSingleAutoInvestPlan(planId, today);
            if (executed == null || executed <= 0) {
                return executedCount;
            }
            executedCount += executed;
        }
    }

    private int executeSingleAutoInvestPlan(Long planId, LocalDate today) {
        InvestmentAutoInvestPlanEntity plan = autoInvestPlanMapper.selectById(planId);
        if (plan == null) {
            log.info("基金定投执行跳过：计划不存在，planId={}", planId);
            return 0;
        }
        if (!AUTO_INVEST_STATUS_ACTIVE.equals(plan.getStatus())) {
            log.info("基金定投执行跳过：计划不是启用状态，planId={}, status={}", planId, plan.getStatus());
            return 0;
        }
        if (plan.getNextExecuteDate() == null || plan.getNextExecuteDate().isAfter(today)) {
            log.info("基金定投执行跳过：计划未到执行日，planId={}, nextExecuteDate={}", planId, plan.getNextExecuteDate());
            return 0;
        }

        LocalDate currentExecuteDate = plan.getNextExecuteDate();
        LocalDate effectiveExecuteDate = nextTradingDay(currentExecuteDate);
        if (effectiveExecuteDate.isAfter(today)) {
            log.info("基金定投执行跳过：计划顺延至下一个交易日，planId={}, nextExecuteDate={}, effectiveExecuteDate={}",
                planId, currentExecuteDate, effectiveExecuteDate);
            return 0;
        }

        requireInvestmentAccount(plan.getUserId(), plan.getAccountId());
        requireAutoInvestPosition(plan.getUserId(), plan.getPositionId());
        InvestmentProductEntity product = requireProduct(plan.getProductId());
        if (!isFundSubscriptionProduct(product)) {
            throw new IllegalArgumentException("当前仅支持基金定投");
        }

        LocalDateTime executedAt = LocalDateTime.now();
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
        // 行情 HTTP 调用放在事务外，避免最长 20s 的远端调用占用 DB 连接与行锁
        JsonNode baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        Integer executed = transactionTemplate.execute(status -> {
            accountMapper.lockUserForWrite(plan.getUserId());
            InvestmentAutoInvestPlanEntity currentPlan = autoInvestPlanMapper.selectById(planId);
            if (currentPlan == null || !AUTO_INVEST_STATUS_ACTIVE.equals(currentPlan.getStatus())
                || !Objects.equals(currentPlan.getNextExecuteDate(), currentExecuteDate)) return 0;
            // 时间戳可能只有秒精度，必须同时比较业务字段，避免同秒编辑使用旧计划。
            if (!Objects.equals(currentPlan.getUpdatedAt(), plan.getUpdatedAt())
                || !Objects.equals(currentPlan.getAmount(), plan.getAmount())
                || !Objects.equals(currentPlan.getFundingAccountId(), plan.getFundingAccountId())
                || !Objects.equals(currentPlan.getAccountId(), plan.getAccountId())
                || !Objects.equals(currentPlan.getPositionId(), plan.getPositionId())
                || !Objects.equals(currentPlan.getProductId(), plan.getProductId())
                || !Objects.equals(currentPlan.getCurrencyCode(), plan.getCurrencyCode())
                || !Objects.equals(currentPlan.getFrequency(), plan.getFrequency())
                || !Objects.equals(currentPlan.getRemark(), plan.getRemark())) return 0;
            AccountEntity currentAccount = requireInvestmentAccount(plan.getUserId(), plan.getAccountId());
            InvestmentPositionEntity currentPosition = requireAutoInvestPosition(plan.getUserId(), plan.getPositionId());
            createPendingFundTransaction(request, currentAccount, currentPosition, product, baseInfo, List.of());
            LocalDate nextExecuteDate = resolveNextAutoInvestExecuteDate(plan.getFrequency(), currentExecuteDate, effectiveExecuteDate);
            int updatedRows = autoInvestPlanMapper.update(null, new LambdaUpdateWrapper<InvestmentAutoInvestPlanEntity>()
                .set(InvestmentAutoInvestPlanEntity::getLastExecutedAt, executedAt)
                .set(InvestmentAutoInvestPlanEntity::getNextExecuteDate, nextExecuteDate)
                .eq(InvestmentAutoInvestPlanEntity::getId, planId)
                .eq(InvestmentAutoInvestPlanEntity::getStatus, AUTO_INVEST_STATUS_ACTIVE)
                .eq(InvestmentAutoInvestPlanEntity::getNextExecuteDate, currentExecuteDate));
            if (updatedRows == 0) {
                throw new IllegalStateException("基金定投计划状态已变化，请稍后重试");
            }
            log.info("基金定投执行成功：planId={}, executeDate={}, effectiveExecuteDate={}, nextExecuteDate={}",
                planId, currentExecuteDate, effectiveExecuteDate, nextExecuteDate);
            return 1;
        });
        return executed == null ? 0 : executed;
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

    private LocalDate resolveNextAutoInvestExecuteDate(String frequency, LocalDate currentDate, LocalDate effectiveExecuteDate) {
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
        } while (!next.isAfter(effectiveExecuteDate));
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
        InvestmentProductEntity product,
        JsonNode baseInfo
    ) {
        requireFundSubscribable(product, baseInfo);
        BigDecimal officialPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        LocalDateTime tradeAt = request.getTradeAt() == null ? LocalDateTime.now() : request.getTradeAt();
        LocalDate appliedDate = resolveFundSubscriptionTradeDate(request.getSubscriptionTimeSlot(), tradeAt);
        LocalDate expectedConfirmDate = resolveFundExpectedConfirmDate(appliedDate, baseInfo, product);
        BigDecimal costAmount = defaultZero(request.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        if (costAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("申购金额必须大于0");
        }
        String dividendType = resolveDividendType(request.getDividendType());
        Long dividendFundingAccountId = resolveDividendFundingAccountId(request.getUserId(), request.getDividendType(), request.getFundingAccountId());
        if (DIVIDEND_TYPE_CASH.equals(dividendType) && dividendFundingAccountId == null) {
            throw new IllegalArgumentException("现金分红必须选择资金账户");
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
        entity.setDividendType(dividendType);
        entity.setDividendFundingAccountId(dividendFundingAccountId);
        entity.setLastSyncedAt(tradeAt);
        entity.setRemark(request.getRemark());

        if (fundingAccount != null) {
            deductFundingAccount(fundingAccount, costAmount);
        }
        positionMapper.insert(entity);
        transactionMapper.insert(createInitialPendingFundTransaction(
            entity,
            fundingAccount == null ? null : fundingAccount.getId(),
            costAmount,
            tradeAt,
            appliedDate,
            expectedConfirmDate
        ));
        InvestmentPriceQuoteEntity immediateQuote = resolveImmediateSettlementQuote(
            product,
            baseInfo,
            appliedDate,
            expectedConfirmDate,
            LocalDateTime.now()
        );
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
        InvestmentProductEntity product,
        JsonNode baseInfo,
        List<FundRedeemFeeRule> feeRules
    ) {
        LocalDateTime tradeAt = request.getTradeAt() == null ? LocalDateTime.now() : request.getTradeAt();
        LocalDate appliedDate = resolveFundSubscriptionTradeDate(request.getSubscriptionTimeSlot(), tradeAt);
        LocalDate expectedSettlementDate = resolveFundExpectedConfirmDate(appliedDate, baseInfo, product);

        if ("buy".equals(request.getTradeType())) {
            requireFundSubscribable(product, baseInfo);
            BigDecimal amount = defaultZero(request.getAmount()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("申购金额必须大于0");
            }
            BigDecimal feeAmount = defaultZero(request.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = defaultZero(request.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);
            AccountEntity fundingAccount = requireCashFundingAccount(request.getUserId(), request.getFundingAccountId());
            deductFundingAccount(fundingAccount, amount.add(feeAmount).add(taxAmount));

            InvestmentPriceQuoteEntity immediateQuote = resolveImmediateSettlementQuote(
                product,
                baseInfo,
                appliedDate,
                expectedSettlementDate,
                LocalDateTime.now()
            );
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
            syncInvestmentAccountBalance(request.getUserId(), request.getAccountId());
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
                ? fundFeeService.calculateFundSellFeeAmount(position, feeRules, quantity, appliedDate, estimatedPrice)
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

    private void requireFundSubscribable(InvestmentProductEntity product, JsonNode baseInfo) {
        String subscriptionStatus = normalizeFundSubscriptionStatus(baseInfo.path("SGZT").asText(null));
        if (!StringUtils.hasText(subscriptionStatus)) {
            throw new IllegalArgumentException("暂无法确认基金申购状态，请稍后重试："
                + formatFundProductName(product));
        }
        if (FUND_SUBSCRIPTION_CLOSED_STATUS_KEYWORDS.stream().anyMatch(subscriptionStatus::contains)) {
            throw new IllegalArgumentException("基金当前不可申购："
                + formatFundProductName(product)
                + "（申购状态：" + subscriptionStatus + "）");
        }
        boolean open = FUND_SUBSCRIPTION_OPEN_STATUS_KEYWORDS.stream().anyMatch(subscriptionStatus::contains);
        if (!open) {
            throw new IllegalArgumentException("基金当前申购状态暂不支持下单："
                + formatFundProductName(product)
                + "（申购状态：" + subscriptionStatus + "）");
        }
    }

    private String normalizeFundSubscriptionStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }
        return status.replaceAll("\\s+", "");
    }

    private String formatFundProductName(InvestmentProductEntity product) {
        if (product == null) {
            return "未知基金";
        }
        String name = StringUtils.hasText(product.getName()) ? product.getName() : "未知基金";
        return StringUtils.hasText(product.getSymbol()) ? name + "（" + product.getSymbol() + "）" : name;
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

    private InvestmentTransactionEntity createInitialPendingFundTransaction(
        InvestmentPositionEntity position,
        Long fundingAccountId,
        BigDecimal amount,
        LocalDateTime tradeAt,
        LocalDate appliedDate,
        LocalDate expectedSettlementDate
    ) {
        InvestmentTransactionEntity transaction = new InvestmentTransactionEntity();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setUserId(position.getUserId());
        transaction.setAccountId(position.getAccountId());
        transaction.setPositionId(position.getId());
        transaction.setProductId(position.getProductId());
        transaction.setTradeType("buy");
        transaction.setQuantity(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        transaction.setPrice(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        transaction.setAmount(defaultZero(amount).setScale(2, RoundingMode.HALF_UP));
        transaction.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        transaction.setFundingAccountId(fundingAccountId);
        transaction.setTradeAt(tradeAt);
        transaction.setStatus(NORMAL_STATUS);
        transaction.setSettlementStatus(SETTLEMENT_STATUS_PENDING);
        transaction.setSettlementAppliedDate(appliedDate);
        transaction.setSettlementExpectedDate(expectedSettlementDate);
        transaction.setSettlementConfirmedAt(null);
        transaction.setRemark(INITIAL_FUND_SUBSCRIPTION_REMARK);
        return transaction;
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
        return addTradingDays(appliedDate, resolveFundConfirmDelayDays(baseInfo, product));
    }

    private LocalDate resolveFundExpectedConfirmDate(LocalDate appliedDate, int confirmDelayDays) {
        return addTradingDays(appliedDate, confirmDelayDays);
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
        // 市场日历统一判定：周末 + 节假日 API + yml 静态兜底（见 MarketCalendarService）
        return marketCalendarService.isClosed(date);
    }

    private void deductFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal netAmount = defaultZero(amount).setScale(2, RoundingMode.HALF_UP);
        // 原子更新，避免并发下丢失更新/超扣
        int updated = accountMapper.subtractBalanceIfSufficient(account.getId(), netAmount);
        if (updated == 0) {
            throw new IllegalArgumentException("资金账户余额不足");
        }
        account.setCurrentBalance(currentBalanceOf(account).subtract(netAmount));
    }

    private void creditFundingAccount(AccountEntity account, BigDecimal amount) {
        BigDecimal netAmount = defaultZero(amount).setScale(2, RoundingMode.HALF_UP);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("到账金额不能小于0");
        }
        accountMapper.addBalance(account.getId(), netAmount);
        account.setCurrentBalance(currentBalanceOf(account).add(netAmount).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal currentBalanceOf(AccountEntity account) {
        return account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
    }

    private InvestmentPositionEntity requirePosition(InvestmentTransactionRequest request) {
        if (request.getPositionId() == null) {
            throw new IllegalArgumentException("请选择投资持仓");
        }
        InvestmentPositionEntity position = positionMapper.selectById(request.getPositionId());
        if (position == null || !request.getUserId().equals(position.getUserId())) {
            throw new IllegalArgumentException("投资持仓不存在");
        }
        if (isPendingFundSubscription(position) && !"buy".equals(request.getTradeType())) {
            throw new IllegalArgumentException("基金申购待确认，暂不支持减仓");
        }
        if (!request.getAccountId().equals(position.getAccountId())) {
            throw new IllegalArgumentException("投资账户不匹配");
        }
        if (!request.getProductId().equals(position.getProductId())) {
            throw new IllegalArgumentException("投资产品不匹配");
        }
        return position;
    }

    private void syncInvestmentAccountBalance(Long userId, Long accountId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            transactionTemplate.executeWithoutResult(status -> {
                accountMapper.lockUserForWrite(userId);
                syncInvestmentAccountBalance(userId, accountId);
            });
            return;
        }
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
        List<InvestmentPositionEntity> positions = positionMapper.selectList(new LambdaQueryWrapper<InvestmentPositionEntity>()
            .eq(InvestmentPositionEntity::getUserId, userId)
            .eq(InvestmentPositionEntity::getAccountId, accountId)
            .eq(InvestmentPositionEntity::getStatus, ACTIVE_STATUS));
        BigDecimal marketValue = "gold".equals(accountType.getCode())
            ? resolveRealtimeGoldAccountBalance(positions)
            : positions.stream()
                .map(this::resolvePositionBalanceForAccount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 待确认的买入申购已从现金账户扣款，但份额尚未进入持仓市值，
        // 需在确认前将这笔在途资金计入投资账户余额，否则总资产会短暂少算。
        BigDecimal pendingBuyAmount = sumPendingBuyTransactionAmount(userId, accountId);
        BigDecimal balance = marketValue.add(pendingBuyAmount).setScale(2, RoundingMode.HALF_UP);
        account.setCurrentBalance(balance);
        accountMapper.updateById(account);
    }

    private BigDecimal resolveRealtimeGoldAccountBalance(List<InvestmentPositionEntity> positions) {
        if (positions == null || positions.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal realtimePrice = goldPriceService.getCachedSpotPrice();
        if (realtimePrice != null && realtimePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalWeight = positions.stream()
                .map(InvestmentPositionEntity::getHoldingQuantity)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalWeight.multiply(realtimePrice).setScale(2, RoundingMode.HALF_UP);
        }

        return positions.stream()
            .map(this::resolvePositionBalanceForAccount)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPendingBuyTransactionAmount(Long userId, Long accountId) {
        return transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
                .eq(InvestmentTransactionEntity::getUserId, userId)
                .eq(accountId != null, InvestmentTransactionEntity::getAccountId, accountId)
                .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
                .eq(InvestmentTransactionEntity::getSettlementStatus, SETTLEMENT_STATUS_PENDING)
                .eq(InvestmentTransactionEntity::getTradeType, "buy")
                .and(wrapper -> wrapper.isNull(InvestmentTransactionEntity::getRemark)
                    .or()
                    .ne(InvestmentTransactionEntity::getRemark, INITIAL_FUND_SUBSCRIPTION_REMARK)))
            .stream()
            .map(transaction -> defaultZero(transaction.getAmount())
                .add(defaultZero(transaction.getFeeAmount()))
                .add(defaultZero(transaction.getTaxAmount())))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePositionBalanceForAccount(InvestmentPositionEntity position) {
        if (isPendingFundSubscription(position)) {
            return defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        }
        return defaultZero(position.getMarketValue()).setScale(2, RoundingMode.HALF_UP);
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
        Map<Long, InvestmentDividendPlanEntity> recentDividendPlans = loadRecentDividendPlans(products.values());
        return positions.stream()
            .map(item -> toPositionResponse(
                item,
                products.get(item.getProductId()),
                accounts.get(item.getAccountId()),
                recentDividendPlans.get(item.getProductId())
            ))
            .toList();
    }

    private InvestmentPositionResponse toPositionResponse(InvestmentPositionEntity entity, InvestmentProductEntity product, AccountEntity account) {
        return toPositionResponse(entity, product, account, resolveRecentDividendPlan(product));
    }

    private InvestmentPositionResponse toPositionResponse(
        InvestmentPositionEntity entity,
        InvestmentProductEntity product,
        AccountEntity account,
        InvestmentDividendPlanEntity recentDividendPlan
    ) {
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
        applyRecentDividendPlanSummary(response, recentDividendPlan);
        response.setSubscriptionStatus(entity.getSubscriptionStatus());
        response.setSubscriptionAppliedDate(entity.getSubscriptionAppliedDate());
        response.setSubscriptionExpectedConfirmDate(entity.getSubscriptionExpectedConfirmDate());
        response.setSubscriptionConfirmedAt(entity.getSubscriptionConfirmedAt());
        response.setDividendType(resolveDividendType(entity.getDividendType()));
        response.setDividendFundingAccountId(entity.getDividendFundingAccountId());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private Map<Long, InvestmentDividendPlanEntity> loadRecentDividendPlans(Collection<InvestmentProductEntity> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> fundProductIds = products.stream()
            .filter(Objects::nonNull)
            .filter(product -> FUND_PRODUCT_TYPE.equals(product.getProductType()))
            .map(InvestmentProductEntity::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (fundProductIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(RECENT_DIVIDEND_PLAN_WINDOW_DAYS);
        LocalDate windowEnd = today.plusDays(RECENT_DIVIDEND_PLAN_WINDOW_DAYS);
        Map<Long, InvestmentDividendPlanEntity> recentPlans = new HashMap<>();

        dividendPlanMapper.selectList(new LambdaQueryWrapper<InvestmentDividendPlanEntity>()
                .in(InvestmentDividendPlanEntity::getProductId, fundProductIds)
                .orderByDesc(InvestmentDividendPlanEntity::getPayDate)
                .orderByDesc(InvestmentDividendPlanEntity::getRecordDate)
                .orderByDesc(InvestmentDividendPlanEntity::getExDividendDate)
                .orderByDesc(InvestmentDividendPlanEntity::getUpdatedAt))
            .stream()
            .filter(plan -> isRecentDividendPlan(plan, windowStart, windowEnd))
            .forEach(plan -> recentPlans.merge(plan.getProductId(), plan, InvestmentDividendService::pickLaterDividendPlan));
        return recentPlans;
    }

    private InvestmentDividendPlanEntity resolveRecentDividendPlan(InvestmentProductEntity product) {
        if (product == null || !FUND_PRODUCT_TYPE.equals(product.getProductType()) || product.getId() == null) {
            return null;
        }
        return loadRecentDividendPlans(List.of(product)).get(product.getId());
    }

    private boolean isRecentDividendPlan(
        InvestmentDividendPlanEntity plan,
        LocalDate windowStart,
        LocalDate windowEnd
    ) {
        if (plan == null || plan.getProductId() == null || "cancelled".equals(plan.getStatus())) {
            return false;
        }
        if (resolveNetDividendPerUnit(plan).compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        LocalDate recencyDate = dividendPlanRecencyDate(plan);
        return recencyDate != null && !recencyDate.isBefore(windowStart) && !recencyDate.isAfter(windowEnd);
    }

    private void applyRecentDividendPlanSummary(
        InvestmentPositionResponse response,
        InvestmentDividendPlanEntity recentDividendPlan
    ) {
        boolean hasRecentPlan = recentDividendPlan != null;
        response.setHasRecentDividendPlan(hasRecentPlan);
        if (!hasRecentPlan) {
            response.setRecentDividendStatus(null);
            response.setRecentDividendDate(null);
            response.setRecentDividendPerUnit(null);
            return;
        }

        LocalDate recencyDate = dividendPlanRecencyDate(recentDividendPlan);
        String status = StringUtils.hasText(recentDividendPlan.getStatus())
            ? recentDividendPlan.getStatus()
            : resolveDividendPlanStatus(recencyDate);
        response.setRecentDividendStatus(status);
        response.setRecentDividendDate(recencyDate);
        response.setRecentDividendPerUnit(resolveNetDividendPerUnit(recentDividendPlan));
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
        boolean pendingSubscription = isPendingFundSubscription(position);
        response.setQuantity(pendingSubscription ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP) : position.getHoldingQuantity());
        response.setPrice(pendingSubscription ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP) : position.getAvgCostPrice());
        response.setAmount(position.getCostAmount());
        response.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        response.setFundingAccountId(null);
        response.setTradeAt(position.getCreatedAt());
        response.setStatus(NORMAL_STATUS);
        response.setSettlementStatus(pendingSubscription ? SETTLEMENT_STATUS_PENDING : SETTLEMENT_STATUS_CONFIRMED);
        response.setSettlementAppliedDate(pendingSubscription ? position.getSubscriptionAppliedDate() : null);
        response.setSettlementExpectedDate(pendingSubscription ? position.getSubscriptionExpectedConfirmDate() : null);
        response.setSettlementConfirmedAt(pendingSubscription ? null : position.getCreatedAt());
        response.setRemark(pendingSubscription ? INITIAL_FUND_SUBSCRIPTION_REMARK : "初始买入");
        response.setCreatedAt(position.getCreatedAt());
        response.setUpdatedAt(position.getUpdatedAt());
        return List.of(response);
    }

    private List<InvestmentTransactionResponse> mergeMissingPendingInitialTransaction(
        List<InvestmentTransactionResponse> transactions,
        Long userId,
        Long accountId,
        Long positionId
    ) {
        if (positionId == null) {
            return transactions;
        }
        InvestmentPositionEntity position = positionMapper.selectById(positionId);
        if (position == null
            || !isPendingFundSubscription(position)
            || (userId != null && !userId.equals(position.getUserId()))
            || (accountId != null && !accountId.equals(position.getAccountId()))) {
            return transactions;
        }
        Long initialTransactionCount = transactionMapper.selectCount(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getPositionId, positionId)
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .eq(InvestmentTransactionEntity::getTradeType, "buy")
            .eq(InvestmentTransactionEntity::getRemark, INITIAL_FUND_SUBSCRIPTION_REMARK));
        if (initialTransactionCount != null && initialTransactionCount > 0) {
            return transactions;
        }

        List<InvestmentTransactionResponse> initialTransactions = inferInitialTransaction(userId, accountId, positionId);
        if (initialTransactions.isEmpty()) {
            return transactions;
        }
        List<InvestmentTransactionResponse> merged = new ArrayList<>(transactions);
        merged.addAll(initialTransactions);
        merged.sort(Comparator
            .comparing(InvestmentTransactionResponse::getTradeAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(InvestmentTransactionResponse::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return merged;
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

    private List<InvestmentPositionEntity> listActiveStockPositions() {
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

    private Map<Long, InvestmentProductEntity> loadStockProducts(List<InvestmentPositionEntity> positions) {
        return productMapper.selectByIds(
                positions.stream().map(InvestmentPositionEntity::getProductId).collect(Collectors.toSet())
            ).stream()
            .filter(product -> product != null
                && STOCK_PRODUCT_TYPE.equals(product.getProductType())
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

    /** 行情在事务外获取；所有持仓写入在用户锁内重读，避免覆盖手动交易。 */
    private <T> T withLockedPosition(InvestmentPositionEntity original, Function<InvestmentPositionEntity, T> action) {
        return transactionTemplate.execute(status -> {
            accountMapper.lockUserForWrite(original.getUserId());
            InvestmentPositionEntity current = positionMapper.selectById(original.getId());
            if (current == null || !Objects.equals(current.getProductId(), original.getProductId())
                || !Objects.equals(current.getStatus(), original.getStatus())) return null;
            return action.apply(current);
        });
    }

    private int syncFundProfitForProduct(
        InvestmentProductEntity product,
        List<InvestmentPositionEntity> positions,
        Map<Long, Long> accountUsers,
        Map<Long, FundQuoteSnapshot> sharedFundQuotes
    ) {
        // 先刷新计划，确保当天派息计划已经可用于结算；计划源失败不应阻断净值同步。
        try {
            syncFundDividendPlansForProduct(product);
        } catch (Exception ex) {
            log.warn("基金分红计划刷新失败，继续同步净值：productId={}, symbol={}, reason={}",
                product.getId(), product.getSymbol(), ex.getMessage());
        }
        FundQuoteSnapshot snapshot = fetchAndSaveLatestFundQuote(product, sharedFundQuotes);
        if (snapshot == null) {
            return 0;
        }

        int updatedCount = 0;
        for (InvestmentPositionEntity original : positions) {
            Integer updated = withLockedPosition(original, position -> {
                processFundDividendForPosition(position, product, snapshot);
                if (isPendingFundSubscription(position)) {
                    updatePendingFundPositionSnapshot(position, snapshot.latestPrice(), snapshot.syncedAt());
                } else {
                    applyFundQuoteToPosition(position, snapshot.latestPrice(), snapshot.preClosePrice(), snapshot.syncedAt());
                }
                positionMapper.updateById(position);
                syncInvestmentAccountBalance(position.getUserId(), position.getAccountId());
                accountUsers.put(position.getAccountId(), position.getUserId());
                return 1;
            });
            updatedCount += updated == null ? 0 : updated;
        }
        return updatedCount;
    }

    private void processFundDividendForPosition(
        InvestmentPositionEntity position,
        InvestmentProductEntity product,
        FundQuoteSnapshot snapshot
    ) {
        if (position == null || product == null || snapshot == null
            || isPendingFundSubscription(position)
            || position.getId() == null
            || snapshot.quoteDate() == null
            || defaultZero(position.getHoldingQuantity()).compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<InvestmentDividendPlanEntity> plans = dividendPlanMapper.selectList(new LambdaQueryWrapper<InvestmentDividendPlanEntity>()
            .eq(InvestmentDividendPlanEntity::getProductId, product.getId())
            .ne(InvestmentDividendPlanEntity::getStatus, "cancelled")
            .eq(InvestmentDividendPlanEntity::getPayDate, snapshot.quoteDate()));
        if (plans.isEmpty()) {
            return;
        }
        for (InvestmentDividendPlanEntity plan : plans) {
            boolean alreadyProcessed = dividendRecordMapper.selectCount(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
                .eq(InvestmentDividendRecordEntity::getPositionId, position.getId())
                .eq(InvestmentDividendRecordEntity::getPlanId, plan.getId())
                .eq(InvestmentDividendRecordEntity::getStatus, NORMAL_STATUS)) > 0;
            if (alreadyProcessed) {
                continue;
            }
            settleFundDividend(position, product, plan, snapshot);
        }
    }

    private void settleFundDividend(
        InvestmentPositionEntity position,
        InvestmentProductEntity product,
        InvestmentDividendPlanEntity plan,
        FundQuoteSnapshot snapshot
    ) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal dividendPerUnit = defaultZero(plan.getDividendPerUnit()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal grossAmount = holdingQuantity.multiply(dividendPerUnit).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = grossAmount.multiply(defaultZero(plan.getTaxRate())
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(taxAmount).setScale(2, RoundingMode.HALF_UP);
        String dividendType = resolveDividendType(position.getDividendType());
        BigDecimal reinvestQuantity = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        if (DIVIDEND_TYPE_REINVEST.equals(dividendType)) {
            BigDecimal price = defaultZero(snapshot.latestPrice());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            reinvestQuantity = netAmount.divide(price, 6, RoundingMode.HALF_UP);
            position.setHoldingQuantity(holdingQuantity.add(reinvestQuantity).setScale(6, RoundingMode.HALF_UP));
            position.setAvailableQuantity(defaultZero(position.getAvailableQuantity()).add(reinvestQuantity).setScale(6, RoundingMode.HALF_UP));
            position.setMarketValue(defaultZero(position.getHoldingQuantity()).multiply(price).setScale(2, RoundingMode.HALF_UP));
            position.setCurrentPrice(price.setScale(6, RoundingMode.HALF_UP));
            position.setCumulativeProfit(defaultZero(position.getCumulativeProfit()).add(netAmount).setScale(2, RoundingMode.HALF_UP));
            recalculatePositionMetrics(position, ACTIVE_STATUS);
            createDividendReinvestTransaction(position, plan, reinvestQuantity, price, netAmount, snapshot.syncedAt());
        } else {
            Long fundingAccountId = position.getDividendFundingAccountId();
            if (fundingAccountId == null) {
                return;
            }
            AccountEntity fundingAccount = requireCashFundingAccount(position.getUserId(), fundingAccountId);
            creditFundingAccount(fundingAccount, netAmount);
            position.setCumulativeProfit(defaultZero(position.getCumulativeProfit()).add(netAmount).setScale(2, RoundingMode.HALF_UP));
            position.setCumulativeProfitRate(rate(position.getCumulativeProfit(), position.getCostAmount()));
        }
        InvestmentDividendRecordEntity record = new InvestmentDividendRecordEntity();
        record.setUserId(position.getUserId());
        record.setAccountId(position.getAccountId());
        record.setPositionId(position.getId());
        record.setProductId(product.getId());
        record.setPlanId(plan.getId());
        record.setDividendType(dividendType);
        record.setHoldingQuantity(holdingQuantity);
        record.setDividendPerUnit(dividendPerUnit);
        record.setGrossAmount(grossAmount);
        record.setTaxAmount(taxAmount);
        record.setNetAmount(netAmount);
        record.setReinvestQuantity(reinvestQuantity);
        record.setCurrencyCode(blankToDefault(product.getCurrencyCode(), DEFAULT_CURRENCY_CODE));
        record.setPaidAt(snapshot.quoteDate().atStartOfDay());
        record.setStatus(NORMAL_STATUS);
        record.setRemark("基金分红自动结算");
        dividendRecordMapper.insert(record);
    }

    private void createDividendReinvestTransaction(
        InvestmentPositionEntity position,
        InvestmentDividendPlanEntity plan,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        LocalDateTime tradeAt
    ) {
        InvestmentTransactionEntity transaction = new InvestmentTransactionEntity();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setUserId(position.getUserId());
        transaction.setAccountId(position.getAccountId());
        transaction.setPositionId(position.getId());
        transaction.setProductId(position.getProductId());
        transaction.setTradeType("dividend_reinvest");
        transaction.setQuantity(quantity);
        transaction.setPrice(price);
        transaction.setAmount(amount);
        transaction.setFeeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transaction.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        transaction.setTradeAt(tradeAt);
        transaction.setStatus(NORMAL_STATUS);
        transaction.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
        transaction.setSettlementConfirmedAt(tradeAt);
        transaction.setRemark("基金红利再投资");
        transactionMapper.insert(transaction);
    }

    private int syncStockQuoteForProduct(
        InvestmentProductEntity product,
        List<InvestmentPositionEntity> positions,
        Map<Long, Long> accountUsers
    ) {
        StockQuoteSnapshot snapshot = fetchAndSaveLatestStockQuote(product);
        if (snapshot == null) {
            return 0;
        }

        int updatedCount = 0;
        for (InvestmentPositionEntity original : positions) {
            Integer updated = withLockedPosition(original, position -> {
                applyStockQuoteToPosition(position, snapshot.latestPrice(), snapshot.preClosePrice(), snapshot.syncedAt());
                positionMapper.updateById(position);
                syncInvestmentAccountBalance(position.getUserId(), position.getAccountId());
                accountUsers.put(position.getAccountId(), position.getUserId());
                return 1;
            });
            updatedCount += updated == null ? 0 : updated;
        }
        return updatedCount;
    }

    private int settlePendingFundTradesForProduct(
        InvestmentProductEntity product,
        List<InvestmentPositionEntity> positions,
        Map<Long, Long> accountUsers,
        LocalDateTime settlementDateTime,
        Map<Long, FundQuoteSnapshot> sharedFundQuotes
    ) {
        FundQuoteSnapshot snapshot = fetchAndSaveLatestFundQuote(product, sharedFundQuotes);
        if (snapshot == null) {
            return 0;
        }

        // 不同持仓的申购确认日大量重合，按日期记忆化避免循环内重复报价查询
        Map<LocalDate, InvestmentPriceQuoteEntity> quoteByAppliedDate = new HashMap<>();
        int updatedCount = 0;
        for (InvestmentPositionEntity original : positions) {
            Integer updated = withLockedPosition(original, position -> {
                LocalDate appliedDate = position.getSubscriptionAppliedDate();
                InvestmentPriceQuoteEntity appliedQuote;
                if (appliedDate == null) {
                    appliedQuote = null;
                } else if (quoteByAppliedDate.containsKey(appliedDate)) {
                    appliedQuote = quoteByAppliedDate.get(appliedDate);
                } else {
                    appliedQuote = findSettlementQuoteByProductAndDate(product.getId(), appliedDate);
                    quoteByAppliedDate.put(appliedDate, appliedQuote);
                }
                if (shouldConfirmFundSubscription(position, settlementDateTime) && appliedQuote != null) {
                    confirmPendingFundSubscription(position, appliedQuote, snapshot.syncedAt());
                    applyFundQuoteToPosition(position, snapshot.latestPrice(), snapshot.preClosePrice(), snapshot.syncedAt());
                    positionMapper.updateById(position);
                    accountUsers.put(position.getAccountId(), position.getUserId());
                    syncInvestmentAccountBalance(position.getUserId(), position.getAccountId());
                    return 1;
                }
                return 0;
            });
            updatedCount += updated == null ? 0 : updated;
        }
        return updatedCount;
    }

    private int settlePendingFundTransactionsForProduct(
        InvestmentProductEntity product,
        List<InvestmentTransactionEntity> transactions,
        Map<Long, Long> accountUsers,
        LocalDateTime settlementDateTime,
        Map<Long, FundQuoteSnapshot> sharedFundQuotes
    ) {
        FundQuoteSnapshot snapshot = fetchAndSaveLatestFundQuote(product, sharedFundQuotes);
        if (snapshot == null) {
            return 0;
        }

        // 同一产品内不同交易的 settlementAppliedDate 大量重合，按日期记忆化避免重复报价查询
        Map<LocalDate, InvestmentPriceQuoteEntity> quoteByAppliedDate = new HashMap<>();

        int updatedCount = 0;
        for (InvestmentTransactionEntity original : transactions) {
            Integer updated = transactionTemplate.execute(status -> {
                accountMapper.lockUserForWrite(original.getUserId());
                InvestmentTransactionEntity transaction = transactionMapper.selectById(original.getId());
                if (transaction == null) {
                    return 0;
                }
                LocalDate appliedDate = transaction.getSettlementAppliedDate();
                InvestmentPriceQuoteEntity appliedQuote;
                if (appliedDate == null) {
                    appliedQuote = null;
                } else if (quoteByAppliedDate.containsKey(appliedDate)) {
                    appliedQuote = quoteByAppliedDate.get(appliedDate);
                } else {
                    appliedQuote = findSettlementQuoteByProductAndDate(product.getId(), appliedDate);
                    quoteByAppliedDate.put(appliedDate, appliedQuote);
                }
                if (!shouldSettlePendingFundTransaction(transaction, settlementDateTime) || appliedQuote == null) {
                    return 0;
                }
                InvestmentPositionEntity position = transaction.getPositionId() == null
                    ? null
                    : positionMapper.selectById(transaction.getPositionId());
                if (position == null) {
                    return 0;
                }

                BigDecimal confirmedPrice = resolveConfirmedFundPrice(appliedQuote);
                if (confirmedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    return 0;
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
                    return 0;
                }

                transaction.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
                transaction.setSettlementConfirmedAt(snapshot.syncedAt());
                applyFundQuoteToPosition(position, snapshot.latestPrice(), snapshot.preClosePrice(), snapshot.syncedAt());
                positionMapper.updateById(position);
                transactionMapper.updateById(transaction);
                syncInvestmentAccountBalance(position.getUserId(), position.getAccountId());
                accountUsers.put(position.getAccountId(), position.getUserId());
                return 1;
            });
            updatedCount += updated == null ? 0 : updated;
        }
        return updatedCount;
    }

    /**
     * 抓取并落库基金最新净值。sharedFundQuotes 非空时（同步周期内）优先复用同周期快照，
     * 避免净值同步与多段结算对同一基金重复发起行情 HTTP；抓取成功后写回共享缓存。
     */
    private boolean shouldConfirmFundSubscription(InvestmentPositionEntity position, LocalDateTime settlementDateTime) {
        if (!isPendingFundSubscription(position)) {
            return false;
        }
        LocalDate expectedConfirmDate = position.getSubscriptionExpectedConfirmDate() == null
            ? position.getSubscriptionAppliedDate()
            : position.getSubscriptionExpectedConfirmDate();
        return isFundConfirmationReady(expectedConfirmDate, settlementDateTime);
    }

    private boolean shouldSettlePendingFundTransaction(InvestmentTransactionEntity transaction, LocalDateTime settlementDateTime) {
        if (transaction == null || !NORMAL_STATUS.equals(transaction.getStatus())
            || !SETTLEMENT_STATUS_PENDING.equals(transaction.getSettlementStatus())) {
            return false;
        }
        LocalDate expectedSettlementDate = transaction.getSettlementExpectedDate() == null
            ? transaction.getSettlementAppliedDate()
            : transaction.getSettlementExpectedDate();
        return isFundConfirmationReady(expectedSettlementDate, settlementDateTime);
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

        InvestmentTransactionEntity initialTransaction = transactionMapper.selectOne(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getPositionId, position.getId())
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .eq(InvestmentTransactionEntity::getSettlementStatus, SETTLEMENT_STATUS_PENDING)
            .eq(InvestmentTransactionEntity::getTradeType, "buy")
            .eq(InvestmentTransactionEntity::getRemark, INITIAL_FUND_SUBSCRIPTION_REMARK)
            .orderByAsc(InvestmentTransactionEntity::getId)
            .last("LIMIT 1"));
        if (initialTransaction != null) {
            initialTransaction.setQuantity(quantity);
            initialTransaction.setPrice(confirmedPrice);
            initialTransaction.setSettlementStatus(SETTLEMENT_STATUS_CONFIRMED);
            initialTransaction.setSettlementConfirmedAt(syncedAt);
            initialTransaction.setRemark("基金申购确认");
            transactionMapper.updateById(initialTransaction);
            return;
        }

        LocalDateTime tradeAt = position.getCreatedAt() != null ? position.getCreatedAt() : syncedAt;
        createBuyTransaction(position, quantity, confirmedPrice, costAmount, "基金申购确认", tradeAt);
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



    /**
     * 基金基础信息：经行情中心 /api/internal/quote/fund/base（新浪基金频道源），
     * 字段名与原东财 FundBaseTypeInformation 保持一致（SHORTNAME/DWJZ/FSRQ/RZDF/LJJZ/SGZT/SHZT/FTYPE/JJGS）。
     */

    /**
     * 基金盘中估值：经行情中心 /api/internal/quote/fund/estimate（新浪 fu_ 源），
     * 字段名与原东财 funddataforgznew 一致（fundcode/name/jzrq/dwjz/gsz/gztime）。
     */


    private InvestmentDetailStatResponse stat(String label, String value, String tone) {
        return new InvestmentDetailStatResponse(label, value, tone);
    }

    private List<InvestmentProductResponse> fetchExternalProducts(String keyword, String productType) {
        List<InvestmentProductResponse> mergedProducts = new ArrayList<>();

        if (keyword.matches("\\d{6}") && !StringUtils.hasText(productType)) {
            Optional<InvestmentProductResponse> fund = fetchFundProduct(keyword);
            if (fund.isPresent()) {
                mergedProducts.add(fund.get());
            }
            Optional<InvestmentProductResponse> stock = fetchStockProduct(keyword)
                .or(() -> fetchTencentStockProduct(keyword));
            if (stock.isPresent()) {
                mergedProducts.add(stock.get());
            }
        }

        if (keyword.matches("\\d{6}") && "fund".equals(productType)) {
            Optional<InvestmentProductResponse> fund = fetchFundProduct(keyword);
            if (fund.isPresent()) {
                mergedProducts.add(fund.get());
            }
        }

        if (keyword.matches("\\d{6}") && "stock".equals(productType)) {
            Optional<InvestmentProductResponse> stock = fetchStockProduct(keyword)
                .or(() -> fetchTencentStockProduct(keyword));
            if (stock.isPresent()) {
                mergedProducts.add(stock.get());
            }
        }

        mergedProducts.addAll(fetchSearchProducts(keyword, productType));
        return deduplicateProducts(mergedProducts);
    }

    private String normalizeForecastProductType(String productType) {
        if (!StringUtils.hasText(productType)) {
            throw new IllegalArgumentException("产品类型不能为空");
        }
        String normalized = productType.trim().toLowerCase(Locale.ROOT);
        if (!FUND_PRODUCT_TYPE.equals(normalized) && !"stock".equals(normalized)) {
            throw new IllegalArgumentException("当前仅支持基金或股票");
        }
        return normalized;
    }

    private String normalizeForecastSymbol(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            throw new IllegalArgumentException("产品代码不能为空");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private InvestmentProductEntity resolveForecastProduct(
        InvestmentDividendForecastRequest request,
        String productType,
        String symbol
    ) {
        InvestmentProductEntity product = new InvestmentProductEntity();
        product.setProductType(productType);
        product.setSymbol(symbol);
        product.setName(blankToDefault(request.getName(), symbol));
        product.setShortName(product.getName());
        product.setMarket(request.getMarket());
        product.setExchangeCode(request.getExchangeCode());
        product.setCurrencyCode(blankToDefault(request.getCurrencyCode(), DEFAULT_CURRENCY_CODE));
        product.setUnitName(blankToDefault(request.getUnitName(), FUND_PRODUCT_TYPE.equals(productType) ? DEFAULT_UNIT_NAME : "股"));
        product.setPricePrecision(FUND_PRODUCT_TYPE.equals(productType) ? 4 : 2);
        product.setStatus(ACTIVE_STATUS);
        return product;
    }


    private BigDecimal resolveForecastCurrentPrice(
        String productType,
        String symbol,
        String exchangeCode,
        BigDecimal fallbackLatestPrice
    ) {
        ForecastPriceSnapshot externalPrice = FUND_PRODUCT_TYPE.equals(productType)
            ? fetchFundForecastPrice(symbol)
            : fetchStockForecastPrice(symbol, exchangeCode);
        if (externalPrice != null && externalPrice.price() != null
            && externalPrice.price().compareTo(BigDecimal.ZERO) > 0) {
            return externalPrice.price().setScale(6, RoundingMode.HALF_UP);
        }
        if (fallbackLatestPrice != null && fallbackLatestPrice.compareTo(BigDecimal.ZERO) > 0) {
            return fallbackLatestPrice.setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    }

    private ForecastPriceSnapshot fetchFundForecastPrice(String code) {
        try {
            JsonNode node = fetchFundEstimateInfo(code);
            LocalDate today = LocalDate.now();
            BigDecimal navPrice = safeDecimal(node.path("dwjz").asText(null));
            LocalDate navDate = safeDate(node.path("jzrq").asText(null));
            if (navDate != null && !navDate.isAfter(today) && navPrice != null
                && navPrice.compareTo(BigDecimal.ZERO) > 0) {
                return new ForecastPriceSnapshot(navDate, navPrice);
            }
            JsonNode baseInfo = fetchFundBaseInfo(code).path("Datas");
            BigDecimal basePrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
            LocalDate baseDate = safeDate(baseInfo.path("FSRQ").asText(null));
            return basePrice != null && baseDate != null && !baseDate.isAfter(today)
                ? new ForecastPriceSnapshot(baseDate, basePrice)
                : null;
        } catch (Exception ex) {
            try {
                JsonNode baseInfo = fetchFundBaseInfo(code).path("Datas");
                BigDecimal basePrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
                LocalDate baseDate = safeDate(baseInfo.path("FSRQ").asText(null));
                return basePrice != null && baseDate != null && !baseDate.isAfter(LocalDate.now())
                    ? new ForecastPriceSnapshot(baseDate, basePrice)
                    : null;
            } catch (Exception fallbackEx) {
                log.warn("基金预测价兜底获取失败，code={}，预测收益将按 0 处理", code, fallbackEx);
                return null;
            }
        }
    }

    private ForecastPriceSnapshot fetchStockForecastPrice(String code, String exchangeCode) {
        JsonNode quote = fetchTencentQuoteFields(toTencentSymbol(code, exchangeCode));
        LocalDate quoteDate = resolveStockQuoteDate(quote.path("timeRaw").asText(null));
        LocalDate today = LocalDate.now();
        BigDecimal latestPrice = safeDecimal(quote.path("price").asText(null));
        if (quoteDate != null && quoteDate.equals(today) && latestPrice != null
            && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
            return new ForecastPriceSnapshot(quoteDate, latestPrice);
        }
        if (quoteDate != null && !quoteDate.isAfter(today) && latestPrice != null
            && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
            return new ForecastPriceSnapshot(quoteDate, latestPrice);
        }
        BigDecimal previousClose = safeDecimal(quote.path("prevClose").asText(null));
        if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) {
            return new ForecastPriceSnapshot(quoteDate == null ? today.minusDays(1) : quoteDate.minusDays(1), previousClose);
        }
        return latestPrice != null && latestPrice.compareTo(BigDecimal.ZERO) > 0
            ? new ForecastPriceSnapshot(quoteDate, latestPrice)
            : null;
    }

    private String resolveProductTypeLabel(String productType) {
        if (FUND_PRODUCT_TYPE.equals(productType)) {
            return "基金";
        }
        if ("stock".equals(productType)) {
            return "股票";
        }
        return "投资资产";
    }

    private List<InvestmentProductResponse> deduplicateProducts(List<InvestmentProductResponse> products) {
        return new ArrayList<>(products.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                this::productDedupKey,
                Function.identity(),
                this::pickPreferredProduct,
                LinkedHashMap::new
            ))
            .values());
    }

    private String productDedupKey(InvestmentProductResponse product) {
        return String.join("|",
            normalizeDedupPart(product == null ? null : product.getProductType()).toLowerCase(Locale.ROOT),
            normalizeDedupPart(product == null ? null : product.getSymbol()).toUpperCase(Locale.ROOT),
            normalizeDedupName(product)
        );
    }

    private InvestmentProductResponse pickPreferredProduct(InvestmentProductResponse left, InvestmentProductResponse right) {
        return productCompletenessScore(right) > productCompletenessScore(left) ? right : left;
    }

    private int productCompletenessScore(InvestmentProductResponse product) {
        if (product == null) {
            return 0;
        }
        int score = 0;
        if (product.getLatestPrice() != null && product.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            score += 16;
        }
        if (StringUtils.hasText(product.getExchangeCode())) {
            score += 8;
        }
        if (StringUtils.hasText(product.getMarket())) {
            score += 4;
        }
        if (StringUtils.hasText(product.getCurrencyCode())) {
            score += 2;
        }
        if (StringUtils.hasText(product.getUnitName())) {
            score += 1;
        }
        if (StringUtils.hasText(product.getName())) {
            score += Math.min(product.getName().trim().length(), 20);
        }
        return score;
    }

    private String normalizeDedupName(InvestmentProductResponse product) {
        String name = product == null ? null : blankToDefault(product.getName(), product.getShortName());
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.trim()
            .replaceAll("[\\s()（）\\-_.]+", "")
            .toUpperCase(Locale.ROOT);
    }

    private String normalizeDedupPart(String value) {
        return value == null ? "" : value.trim();
    }


    private Optional<InvestmentProductResponse> fetchFundProduct(String code) {
        try {
            JsonNode node = fetchFundEstimateInfo(code);
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

    /**
     * 产品搜索：经行情中心 /api/internal/quote/search（腾讯 smartbox 联想，
     * LOF/ETF/名称含"基金"的场内份额归为基金，指数剔除，与原东财 suggest type=14 口径一致）。
     */
    private List<InvestmentProductResponse> fetchSearchProducts(String keyword, String productType) {
        try {
            JsonNode items = marketQuoteClient.searchProducts(keyword).path("items");
            if (!items.isArray()) {
                return Collections.emptyList();
            }
            return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(row -> toExternalProductFromRow(row, productType))
                .flatMap(Optional::stream)
                .toList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }


    private Optional<InvestmentProductResponse> toExternalProductFromRow(JsonNode row, String productType) {
        String code = row.path("code").asText("");
        String resolvedType = row.path("type").asText("");
        String exchange = row.path("exchange").asText("");
        if (!StringUtils.hasText(code) || !StringUtils.hasText(resolvedType)) {
            return Optional.empty();
        }
        if (StringUtils.hasText(productType) && !productType.equals(resolvedType)) {
            return Optional.empty();
        }
        String name = row.path("name").asText("");
        if ("fund".equals(resolvedType)) {
            return fetchFundProduct(code).or(() -> Optional.of(toBasicExternalProduct(code, name, "fund", "FUND", null, "份", 4)));
        }
        if ("stock".equals(resolvedType)) {
            return fetchStockProduct(code)
                .or(() -> fetchTencentStockProduct(code))
                .or(() -> Optional.of(toBasicExternalProduct(code, name, "stock", "CN", exchange, "股", 2)));
        }
        if ("bond".equals(resolvedType)) {
            return Optional.of(toBasicExternalProduct(code, name, "bond", "CN", exchange, "张", 4));
        }
        return fetchTencentStockProduct(code);
    }


    private Optional<InvestmentProductResponse> fetchTencentStockProduct(String code) {
        // 腾讯快照经 market-service 获取（client 内部已尝试沪深前缀）
        try {
            JsonNode snapshot = marketQuoteClient.stockSnapshot(code);
            if (snapshot == null || !snapshot.path("matched").asBoolean(false)) {
                return Optional.empty();
            }
            String name = snapshot.path("name").asText("");
            if (!StringUtils.hasText(name)) {
                return Optional.empty();
            }
            String price = snapshot.path("price").asText(null);
            if (!StringUtils.hasText(price)) {
                return Optional.empty();
            }

            InvestmentProductResponse response = new InvestmentProductResponse();
            response.setProductType("stock");
            response.setMarket("CN");
            response.setExchangeCode(snapshot.path("exchangeCode").asText(null));
            response.setSymbol(code);
            response.setName(name);
            response.setShortName(name);
            response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
            response.setUnitName("股");
            response.setPricePrecision(2);
            response.setLatestPrice(decimalText(price, null));
            response.setStatus(ACTIVE_STATUS);
            return Optional.of(response);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }


    private InvestmentProductResponse toBasicExternalProduct(String code, String name, String productType, String market, String exchangeCode, String unitName, int pricePrecision) {
        InvestmentProductResponse response = new InvestmentProductResponse();
        response.setProductType(productType);
        response.setMarket(market);
        response.setExchangeCode(exchangeCode);
        response.setSymbol(code);
        response.setName(name);
        response.setShortName(name);
        response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
        response.setUnitName(unitName);
        response.setPricePrecision(pricePrecision);
        response.setStatus(ACTIVE_STATUS);
        return response;
    }



    /**
     * 股票产品快照：经行情中心 /api/internal/quote/stock/snapshot（腾讯 qt.gtimg.cn，
     * 名称/现价，替代原东财 push2 f57/f58/f43）。
     */
    private Optional<InvestmentProductResponse> fetchStockProduct(String code) {
        try {
            JsonNode data = marketQuoteClient.stockSnapshot(code);
            if (data == null || !data.path("matched").asBoolean(false)) {
                return Optional.empty();
            }
            String name = data.path("name").asText("");
            if (!StringUtils.hasText(name)) {
                return Optional.empty();
            }
            InvestmentProductResponse response = new InvestmentProductResponse();
            response.setProductType("stock");
            response.setMarket("CN");
            response.setExchangeCode(data.path("exchangeCode").asText(null));
            response.setSymbol(data.path("code").asText(code));
            response.setName(name);
            response.setShortName(name);
            response.setCurrencyCode(DEFAULT_CURRENCY_CODE);
            response.setUnitName("股");
            response.setPricePrecision(2);
            response.setLatestPrice(safeDecimal(data.path("price").asText(null)));
            response.setStatus(ACTIVE_STATUS);
            return Optional.of(response);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }






    private String formatPositionSyncTime(LocalDateTime value) {
        return value == null ? null : value.format(POSITION_SYNC_TIME_FORMAT);
    }

    private boolean hasTodayDayProfit(InvestmentPositionEntity position) {
        return position != null
            && position.getLastSyncedAt() != null
            && LocalDate.now().equals(position.getLastSyncedAt().toLocalDate())
            && !position.getLastSyncedAt().toLocalTime().isBefore(FUND_PROFIT_SYNC_READY_TIME);
    }

    private InvestmentDividendResponse toDividendRecordResponse(InvestmentDividendRecordEntity entity) {
        InvestmentProductEntity product = productMapper.selectById(entity.getProductId());
        InvestmentDividendResponse response = new InvestmentDividendResponse();
        response.setId(entity.getId());
        response.setProductId(entity.getProductId());
        response.setProductName(product == null ? null : product.getName());
        response.setProductSymbol(product == null ? null : product.getSymbol());
        LocalDate paidDate = entity.getPaidAt() == null ? null : entity.getPaidAt().toLocalDate();
        response.setDividendYear(paidDate == null ? null : paidDate.getYear());
        response.setPayDate(paidDate);
        response.setExpectedAmount(entity.getGrossAmount());
        response.setActualAmount(entity.getNetAmount());
        response.setDividendPerUnit(entity.getDividendPerUnit());
        response.setStatus("paid");
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
        List<InvestmentDividendRecordEntity> actualRecords = dividendRecordMapper.selectList(new LambdaQueryWrapper<InvestmentDividendRecordEntity>()
                .eq(InvestmentDividendRecordEntity::getPositionId, position.getId())
                .eq(InvestmentDividendRecordEntity::getStatus, NORMAL_STATUS)
                .ge(InvestmentDividendRecordEntity::getPaidAt, oneYearAgo.atStartOfDay())
                .orderByDesc(InvestmentDividendRecordEntity::getPaidAt));
        List<InvestmentTransactionEntity> transactions = transactionMapper.selectList(new LambdaQueryWrapper<InvestmentTransactionEntity>()
            .eq(InvestmentTransactionEntity::getPositionId, position.getId())
            .eq(InvestmentTransactionEntity::getStatus, NORMAL_STATUS)
            .orderByAsc(InvestmentTransactionEntity::getTradeAt)
            .orderByAsc(InvestmentTransactionEntity::getId));
        Map<LocalDate, BigDecimal> actualAmountsByDate = actualRecords.stream()
            .filter(record -> record.getPaidAt() != null)
            .collect(Collectors.toMap(
                record -> record.getPaidAt().toLocalDate(),
                record -> defaultZero(record.getNetAmount()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal::add,
                LinkedHashMap::new
            ));

        List<InvestmentDividendPlanEntity> plans = dividendPlanMapper.selectList(new LambdaQueryWrapper<InvestmentDividendPlanEntity>()
            .eq(InvestmentDividendPlanEntity::getProductId, product.getId())
            .ne(InvestmentDividendPlanEntity::getStatus, "cancelled")
            .orderByDesc(InvestmentDividendPlanEntity::getPayDate));

        List<InvestmentDividendResponse> planResponses = plans.stream()
            .filter(plan -> {
                LocalDate payDate = dividendPlanRecencyDate(plan);
                return payDate != null && !payDate.isBefore(oneYearAgo) && !payDate.isAfter(today);
            })
            .sorted(Comparator.comparing(InvestmentDividendService::dividendPlanRecencyDate).reversed())
            .map(plan -> toDividendRecordResponse(plan, product, position, transactions, actualAmountsByDate))
            .toList();

        Set<LocalDate> planDates = planResponses.stream()
            .map(InvestmentDividendResponse::getPayDate)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<InvestmentDividendResponse> unmatchedActualResponses = actualRecords.stream()
            .filter(record -> record.getPaidAt() != null && !planDates.contains(record.getPaidAt().toLocalDate()))
            .map(this::toDividendRecordResponse)
            .toList();

        return Stream.concat(planResponses.stream(), unmatchedActualResponses.stream())
            .sorted(Comparator.comparing(InvestmentDividendResponse::getPayDate, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    private InvestmentDividendResponse toDividendRecordResponse(
        InvestmentDividendPlanEntity plan,
        InvestmentProductEntity product,
        InvestmentPositionEntity position,
        List<InvestmentTransactionEntity> transactions,
        Map<LocalDate, BigDecimal> actualAmountsByDate
    ) {
        InvestmentDividendResponse response = new InvestmentDividendResponse();
        LocalDate payDate = dividendPlanRecencyDate(plan);
        BigDecimal holdingQuantity = resolveDividendHoldingQuantity(position, plan, transactions);
        BigDecimal perUnit = resolveNetDividendPerUnit(plan);
        response.setId(plan.getId());
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setProductSymbol(product.getSymbol());
        response.setDividendYear(plan.getDividendYear());
        response.setPayDate(payDate);
        response.setDividendPerUnit(defaultZero(plan.getDividendPerUnit()).setScale(6, RoundingMode.HALF_UP));
        response.setExpectedAmount(holdingQuantity.multiply(perUnit).setScale(2, RoundingMode.HALF_UP));
        BigDecimal actualAmount = payDate == null ? null : actualAmountsByDate.get(payDate);
        response.setActualAmount(actualAmount == null ? null : actualAmount.setScale(2, RoundingMode.HALF_UP));
        response.setStatus(plan.getStatus());
        response.setPaidAt(payDate == null ? null : payDate.atStartOfDay());
        return response;
    }

    private BigDecimal resolveDividendHoldingQuantity(
        InvestmentPositionEntity position,
        InvestmentDividendPlanEntity plan,
        List<InvestmentTransactionEntity> transactions
    ) {
        BigDecimal fallback = defaultZero(position == null ? null : position.getHoldingQuantity());
        LocalDate recordDate = plan == null ? null : plan.getRecordDate();
        if (recordDate == null || transactions == null || transactions.isEmpty()) {
            return fallback.setScale(6, RoundingMode.HALF_UP);
        }
        LocalDateTime cutoff = recordDate.plusDays(1).atStartOfDay();
        BigDecimal quantity = transactions.stream()
            .filter(transaction -> transaction.getTradeAt() != null && transaction.getTradeAt().isBefore(cutoff))
            .map(transaction -> {
                BigDecimal value = defaultZero(transaction.getQuantity());
                return priceHistoryService.isNegativeTradeType(transaction.getTradeType()) ? value.negate() : value;
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return quantity.compareTo(BigDecimal.ZERO) >= 0
            ? quantity.setScale(6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
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
        response.setFixedExpenses(listFixedExpenses(userId));
        response.setUpdatedAt(null);
        return response;
    }

    private InvestmentFixedExpenseEntity fillFixedExpenseEntity(InvestmentFixedExpenseEntity entity, InvestmentFixedExpenseRequest request) {
        entity.setUserId(request.getUserId());
        entity.setName(request.getName() == null ? null : request.getName().trim());
        entity.setAmount(defaultZero(request.getAmount()).setScale(2, RoundingMode.HALF_UP));
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode().trim() : DEFAULT_CURRENCY_CODE);
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        } else if (entity.getSortOrder() == null) {
            entity.setSortOrder(resolveNextFixedExpenseSortOrder(request.getUserId()));
        }
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(ACTIVE_STATUS);
        }
        return entity;
    }

    private int resolveNextFixedExpenseSortOrder(Long userId) {
        return fixedExpenseMapper.selectList(new LambdaQueryWrapper<InvestmentFixedExpenseEntity>()
                .eq(InvestmentFixedExpenseEntity::getUserId, userId)
                .eq(InvestmentFixedExpenseEntity::getStatus, ACTIVE_STATUS)
                .orderByDesc(InvestmentFixedExpenseEntity::getSortOrder)
                .last("LIMIT 1"))
            .stream()
            .map(InvestmentFixedExpenseEntity::getSortOrder)
            .filter(Objects::nonNull)
            .findFirst()
            .map(value -> value + 1)
            .orElse(1);
    }

    private InvestmentFixedExpenseResponse toFixedExpenseResponse(InvestmentFixedExpenseEntity entity) {
        if (entity == null) {
            return null;
        }
        InvestmentFixedExpenseResponse response = new InvestmentFixedExpenseResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setName(entity.getName());
        response.setAmount(entity.getAmount());
        response.setCurrencyCode(entity.getCurrencyCode());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
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
        Long targetPositionId = positions.stream()
            .filter(position -> position.getId() != null)
            .max(Comparator
                .comparing(this::resolvePositionMarketValue)
                .thenComparing(InvestmentPositionEntity::getUpdatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
                .thenComparing(InvestmentPositionEntity::getId))
            .map(InvestmentPositionEntity::getId)
            .orElse(null);

        InvestmentDividendIncomeItemResponse response = new InvestmentDividendIncomeItemResponse();
        response.setPositionId(targetPositionId);
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

    private BigDecimal resolvePositionMarketValue(InvestmentPositionEntity position) {
        return defaultZero(position == null ? null : position.getMarketValue());
    }

    /**
     * 股票分红历史：经行情中心 /api/internal/quote/stock/dividends（新浪分红送配页，每10股口径）。
     * 新浪无"所属年度"列，分红年度取除息日年份（原东财 REPORT_DATE 为空时同为该回退口径）。
     */







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


    private String generateTransactionNo() {
        return "IV" + LocalDateTime.now().format(NO_TIME_FORMAT) + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
