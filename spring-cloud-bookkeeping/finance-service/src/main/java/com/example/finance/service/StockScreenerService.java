package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.StockScreenItemResponse;
import com.example.finance.dto.StockScreenPageResponse;
import com.example.finance.dto.StockScreenRunResponse;
import com.example.finance.entity.StockScreenRunEntity;
import com.example.finance.entity.StockScreenSnapshotEntity;
import com.example.finance.mapper.StockScreenRunMapper;
import com.example.finance.mapper.StockScreenSnapshotMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class StockScreenerService {

    private static final Logger log = LoggerFactory.getLogger(StockScreenerService.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_CANCELED = "canceled";
    private static final String DATA_SOURCE = "东方财富股票列表 / 新浪日K";
    private static final String CURRENT_RULE_VERSION = "downtrend-engulf-v2";
    private static final LocalTime MARKET_DATA_READY_TIME = LocalTime.of(16, 0);
    private static final int UNIVERSE_PAGE_SIZE = 100;
    private static final int K_LINE_COUNT = 12;
    private static final BigDecimal DEFAULT_THREE_DAY_DECLINE = new BigDecimal("9");
    private static final BigDecimal DEFAULT_LAST_DAY_DECLINE = new BigDecimal("3");
    private static final BigDecimal NO_LOWER_SHADOW_TOLERANCE = new BigDecimal("0.15");

    private final StockScreenRunMapper runMapper;
    private final StockScreenSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final ExecutorService quoteFetchExecutor;
    private final ExecutorService manualScanExecutor;
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final String stockListApiUrl;
    private final String dailyKLineApiUrl;
    private final Set<LocalDate> marketClosedDates;

    public StockScreenerService(
        StockScreenRunMapper runMapper,
        StockScreenSnapshotMapper snapshotMapper,
        ObjectMapper objectMapper,
        @Value("${finance.stock-screener.stock-list-api-url:https://push2delay.eastmoney.com/api/qt/clist/get}") String stockListApiUrl,
        @Value("${finance.stock-screener.daily-kline-api-url:https://quotes.sina.cn/cn/api/jsonp_v2.php}") String dailyKLineApiUrl,
        @Value("${finance.stock-screener.fetch-concurrency:12}") Integer fetchConcurrency,
        @Value("${finance.stock-screener.connect-timeout-millis:3000}") Integer connectTimeoutMillis,
        @Value("${finance.stock-screener.read-timeout-millis:5000}") Integer readTimeoutMillis,
        @Value("${finance.investment.market-closed-dates:}") String marketClosedDates
    ) {
        this.runMapper = runMapper;
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
        this.stockListApiUrl = stockListApiUrl;
        this.dailyKLineApiUrl = dailyKLineApiUrl;
        this.marketClosedDates = Arrays.stream(marketClosedDates.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(LocalDate::parse)
            .collect(Collectors.toSet());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, connectTimeoutMillis));
        requestFactory.setReadTimeout(Math.max(1000, readTimeoutMillis));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        int concurrency = Math.max(2, Math.min(24, fetchConcurrency));
        this.quoteFetchExecutor = Executors.newFixedThreadPool(concurrency, daemonThreadFactory("stock-quote-"));
        this.manualScanExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("stock-scan-"));
    }

    public ScanSummary runScheduledScan(String triggerName) {
        StockScreenRunEntity reusableRun = findReusableRun();
        if (reusableRun != null) {
            return new ScanSummary(
                "reused",
                reusableRun.getTradeDate(),
                reusableRun.getTotalStocks(),
                reusableRun.getProcessedStocks() - reusableRun.getFailedStocks(),
                reusableRun.getMatchedStocks(),
                "同一交易日和规则版本已有成功结果，跳过重复扫描"
            );
        }
        if (!scanRunning.compareAndSet(false, true)) {
            return new ScanSummary("running", null, 0, 0, 0, "已有全市场扫描正在运行");
        }
        cancelRequested.set(false);
        try {
            return runFullMarketScan(triggerName);
        } finally {
            scanRunning.set(false);
            cancelRequested.set(false);
        }
    }

    public ScanSubmission submitManualScan(boolean force) {
        if (!force) {
            StockScreenRunEntity reusableRun = findReusableRun();
            if (reusableRun != null) {
                return new ScanSubmission(
                    "reused",
                    "已复用 " + reusableRun.getTradeDate() + " 的扫描结果，无需重复请求全市场行情",
                    reusableRun.getId(),
                    reusableRun.getTradeDate()
                );
            }
        }
        if (!scanRunning.compareAndSet(false, true)) {
            StockScreenRunEntity latestRun = latestRun();
            return new ScanSubmission(
                "running",
                "已有全市场扫描正在运行",
                latestRun == null ? null : latestRun.getId(),
                latestRun == null ? null : latestRun.getTradeDate()
            );
        }
        cancelRequested.set(false);
        manualScanExecutor.execute(() -> {
            try {
                runFullMarketScan("manual-api");
            } catch (Exception ex) {
                log.error("A股全市场手动扫描失败", ex);
            } finally {
                scanRunning.set(false);
                cancelRequested.set(false);
            }
        });
        return new ScanSubmission("accepted", "全市场扫描已提交，可稍后刷新查看进度", null, null);
    }

    public StopSubmission requestStop() {
        StockScreenRunEntity currentRun = latestRun();
        if (!scanRunning.get()) {
            if (currentRun != null && STATUS_RUNNING.equals(currentRun.getStatus())) {
                currentRun.setStatus(STATUS_CANCELED);
                currentRun.setFinishedAt(LocalDateTime.now(MARKET_ZONE));
                currentRun.setResultMessage("服务中已无对应扫描线程，运行记录已停止");
                runMapper.updateById(currentRun);
                return new StopSubmission("canceled", "已清理上次服务重启遗留的扫描状态", currentRun.getId());
            }
            return new StopSubmission(
                "idle",
                "当前没有正在运行的全市场扫描",
                currentRun == null ? null : currentRun.getId()
            );
        }
        cancelRequested.set(true);
        Long runId = currentRun != null && STATUS_RUNNING.equals(currentRun.getStatus())
            ? currentRun.getId()
            : null;
        return new StopSubmission("accepted", "停止请求已提交，正在结束当前行情请求", runId);
    }

    public StockScreenRunResponse getLatestRun() {
        return toRunResponse(latestRun());
    }

    public StockScreenPageResponse screen(
        String market,
        String keyword,
        Integer minBearishCount,
        BigDecimal minThreeDayDecline,
        BigDecimal minLastDayDecline,
        Boolean requireVolumeUp,
        Boolean requireNoLowerShadow,
        Integer page,
        Integer pageSize
    ) {
        int normalizedPage = Math.max(1, page == null ? 1 : page);
        int normalizedPageSize = Math.max(10, Math.min(100, pageSize == null ? 20 : pageSize));
        int bearishCount = Math.max(1, Math.min(6, minBearishCount == null ? 4 : minBearishCount));
        BigDecimal threeDayDecline = normalizePercent(minThreeDayDecline, DEFAULT_THREE_DAY_DECLINE);
        BigDecimal lastDayDecline = normalizePercent(minLastDayDecline, DEFAULT_LAST_DAY_DECLINE);

        StockScreenRunEntity successfulRun = latestSuccessfulRun();
        StockScreenPageResponse response = new StockScreenPageResponse();
        response.setPage(normalizedPage);
        response.setPageSize(normalizedPageSize);
        if (successfulRun == null) {
            response.setRun(getLatestRun());
            return response;
        }
        response.setRun(toRunResponse(successfulRun));

        LambdaQueryWrapper<StockScreenSnapshotEntity> countQuery = buildScreenQuery(
            successfulRun.getId(), market, keyword, bearishCount, threeDayDecline, lastDayDecline,
            Boolean.TRUE.equals(requireVolumeUp), Boolean.TRUE.equals(requireNoLowerShadow)
        );
        Long total = snapshotMapper.selectCount(countQuery);
        response.setTotal(total == null ? 0L : total);
        if (response.getTotal() == 0) {
            return response;
        }

        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        LambdaQueryWrapper<StockScreenSnapshotEntity> pageQuery = buildScreenQuery(
            successfulRun.getId(), market, keyword, bearishCount, threeDayDecline, lastDayDecline,
            Boolean.TRUE.equals(requireVolumeUp), Boolean.TRUE.equals(requireNoLowerShadow)
        )
            .orderByDesc(StockScreenSnapshotEntity::getSignalScore)
            .orderByDesc(StockScreenSnapshotEntity::getThreeDayDeclinePct)
            .orderByAsc(StockScreenSnapshotEntity::getStockCode)
            .last("LIMIT " + normalizedPageSize + " OFFSET " + offset);
        response.setItems(snapshotMapper.selectList(pageQuery).stream().map(this::toItemResponse).toList());
        return response;
    }

    private ScanSummary runFullMarketScan(String triggerName) {
        StockScreenRunEntity run = startRun(triggerName);
        try {
            List<StockInstrument> universe = fetchStockUniverse();
            if (cancelRequested.get()) {
                return finishCanceledRun(run, 0, 0, List.of());
            }
            if (universe.isEmpty()) {
                throw new IllegalStateException("股票列表为空，已停止本次扫描");
            }
            run.setTotalStocks(universe.size());
            runMapper.updateById(run);

            List<CompletableFuture<ScanOutcome>> futures = universe.stream()
                .map(stock -> CompletableFuture.supplyAsync(() -> analyzeSafely(stock), quoteFetchExecutor))
                .toList();
            List<StockScreenSnapshotEntity> calculatedSnapshots = new ArrayList<>(universe.size());
            int processed = 0;
            int failed = 0;
            for (CompletableFuture<ScanOutcome> future : futures) {
                if (cancelRequested.get()) {
                    cancelPendingFutures(futures);
                    return finishCanceledRun(run, processed, failed, List.of());
                }
                ScanOutcome outcome = future.join();
                processed++;
                if (outcome.failed()) {
                    failed++;
                }
                if (outcome.snapshot() != null) {
                    calculatedSnapshots.add(outcome.snapshot());
                }
                if (processed % 100 == 0 || processed == universe.size()) {
                    run.setProcessedStocks(processed);
                    run.setFailedStocks(failed);
                    runMapper.updateById(run);
                }
            }

            if (cancelRequested.get()) {
                cancelPendingFutures(futures);
                return finishCanceledRun(run, processed, failed, List.of());
            }

            LocalDate tradeDate = calculatedSnapshots.stream()
                .map(StockScreenSnapshotEntity::getSignalDate)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("未获取到足够的日K数据"));
            List<StockScreenSnapshotEntity> currentTradeDateSnapshots = calculatedSnapshots.stream()
                .filter(item -> tradeDate.equals(item.getSignalDate()))
                .toList();

            int matched = 0;
            for (StockScreenSnapshotEntity snapshot : currentTradeDateSnapshots) {
                if (cancelRequested.get()) {
                    return finishCanceledRun(run, processed, failed, currentTradeDateSnapshots);
                }
                snapshot.setRunId(run.getId());
                snapshot.setTradeDate(tradeDate);
                snapshotMapper.insert(snapshot);
                if (Boolean.TRUE.equals(snapshot.getDefaultMatched())) {
                    matched++;
                }
            }

            if (cancelRequested.get()) {
                return finishCanceledRun(run, processed, failed, currentTradeDateSnapshots);
            }

            run.setTradeDate(tradeDate);
            run.setStatus(STATUS_SUCCESS);
            run.setProcessedStocks(processed);
            run.setMatchedStocks(matched);
            run.setFailedStocks(failed);
            run.setFinishedAt(LocalDateTime.now(MARKET_ZONE));
            run.setResultMessage("有效股票=" + currentTradeDateSnapshots.size() + ", 默认规则命中=" + matched + ", 行情失败=" + failed);
            runMapper.updateById(run);
            cleanupOldRuns(tradeDate.minusDays(90));
            log.info("A股全市场扫描完成：runId={}, tradeDate={}, total={}, valid={}, matched={}, failed={}",
                run.getId(), tradeDate, universe.size(), currentTradeDateSnapshots.size(), matched, failed);
            return new ScanSummary(STATUS_SUCCESS, tradeDate, universe.size(), currentTradeDateSnapshots.size(), matched, run.getResultMessage());
        } catch (Exception ex) {
            run.setStatus(STATUS_FAILED);
            run.setFinishedAt(LocalDateTime.now(MARKET_ZONE));
            run.setErrorMessage(trim(ex.getMessage(), 1000));
            runMapper.updateById(run);
            throw ex instanceof RuntimeException runtimeException
                ? runtimeException
                : new IllegalStateException("A股全市场扫描失败", ex);
        }
    }

    private StockScreenRunEntity startRun(String triggerName) {
        StockScreenRunEntity run = new StockScreenRunEntity();
        run.setTriggerName(trim(triggerName, 80));
        run.setStatus(STATUS_RUNNING);
        run.setTotalStocks(0);
        run.setProcessedStocks(0);
        run.setMatchedStocks(0);
        run.setFailedStocks(0);
        run.setDataSource(DATA_SOURCE);
        run.setRuleVersion(CURRENT_RULE_VERSION);
        run.setStartedAt(LocalDateTime.now(MARKET_ZONE));
        runMapper.insert(run);
        return run;
    }

    private List<StockInstrument> fetchStockUniverse() throws Exception {
        Map<String, StockInstrument> stocks = new LinkedHashMap<>();
        int total = Integer.MAX_VALUE;
        for (int page = 1; page <= 80 && stocks.size() < total; page++) {
            if (cancelRequested.get()) {
                break;
            }
            String url = stockListApiUrl
                + "?pn=" + page
                + "&pz=" + UNIVERSE_PAGE_SIZE
                + "&po=0&np=1&fltt=2&invt=2&fid=f12"
                + "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048"
                + "&fields=f12,f14,f2";
            JsonNode root = fetchJson(url, "https://quote.eastmoney.com/");
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new IllegalStateException("股票列表接口返回异常");
            }
            total = data.path("total").asInt(0);
            JsonNode rows = data.path("diff");
            if (!rows.isArray() || rows.isEmpty()) {
                break;
            }
            for (JsonNode row : rows) {
                String code = row.path("f12").asText("").trim();
                String name = row.path("f14").asText("").trim();
                String market = resolveMarket(code);
                if (market == null || name.isBlank() || !isPositiveNumber(row.path("f2"))) {
                    continue;
                }
                stocks.putIfAbsent(code, new StockInstrument(code, name, marketPrefix(market) + code, market));
            }
            if (rows.size() < UNIVERSE_PAGE_SIZE) {
                break;
            }
        }
        return new ArrayList<>(stocks.values());
    }

    private ScanOutcome analyzeSafely(StockInstrument stock) {
        if (cancelRequested.get()) {
            return new ScanOutcome(null, false);
        }
        try {
            List<DailyBar> bars = fetchDailyBars(stock.symbol());
            if (bars.size() < 7) {
                return new ScanOutcome(null, false);
            }
            return new ScanOutcome(analyze(stock, bars), false);
        } catch (Exception ex) {
            log.debug("股票日K获取失败：code={}, reason={}", stock.code(), ex.getMessage());
            return new ScanOutcome(null, true);
        }
    }

    private List<DailyBar> fetchDailyBars(String symbol) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String variable = "_" + symbol;
                String url = dailyKLineApiUrl
                    + "/var%20" + variable + "=/CN_MarketDataService.getKLineData"
                    + "?symbol=" + symbol + "&scale=240&ma=no&datalen=" + K_LINE_COUNT;
                String body = fetchText(url, "https://finance.sina.com.cn/");
                int start = body.indexOf('[');
                int end = body.lastIndexOf(']');
                if (start < 0 || end <= start) {
                    return List.of();
                }
                JsonNode rows = objectMapper.readTree(body.substring(start, end + 1));
                List<DailyBar> bars = new ArrayList<>();
                for (JsonNode row : rows) {
                    DailyBar bar = toDailyBar(row);
                    if (bar != null) {
                        bars.add(bar);
                    }
                }
                bars.sort(Comparator.comparing(DailyBar::date));
                return bars;
            } catch (Exception ex) {
                lastError = ex;
                if (attempt < 2) {
                    Thread.sleep(120L * attempt);
                }
            }
        }
        throw lastError == null ? new IllegalStateException("日K接口无响应") : lastError;
    }

    private StockScreenSnapshotEntity analyze(StockInstrument stock, List<DailyBar> bars) {
        int size = bars.size();
        List<DailyBar> previousSix = bars.subList(size - 7, size - 1);
        List<DailyBar> lastThree = previousSix.subList(3, 6);
        DailyBar beforeLastThree = previousSix.get(2);
        DailyBar firstBearish = lastThree.get(0);
        DailyBar secondBearish = lastThree.get(1);
        DailyBar previous = lastThree.get(2);
        DailyBar signal = bars.get(size - 1);

        int bearishCount = (int) previousSix.stream().filter(this::isBearish).count();
        boolean lastThreeBearish = lastThree.stream().allMatch(this::isBearish);
        boolean lastThreeVolumeUp = lastThree.get(0).volume() < lastThree.get(1).volume()
            && lastThree.get(1).volume() < lastThree.get(2).volume();
        // 行情中的“累计跌幅/单日跌幅”统一按收盘价口径计算，而非K线实体长度。
        BigDecimal threeDayDecline = percent(
            beforeLastThree.close().subtract(previous.close()),
            beforeLastThree.close()
        );
        BigDecimal lastDayDecline = percent(
            secondBearish.close().subtract(previous.close()),
            secondBearish.close()
        );
        boolean bullishEngulfing = signal.close().compareTo(signal.open()) > 0
            && signal.open().compareTo(previous.close()) > 0
            && signal.close().compareTo(previous.open()) > 0;
        boolean volumeShrinking = signal.volume() < previous.volume();
        BigDecimal volumeRatio = previous.volume() <= 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(signal.volume()).divide(BigDecimal.valueOf(previous.volume()), 4, RoundingMode.HALF_UP);
        BigDecimal lowerShadow = percent(signal.open().subtract(signal.low()).max(BigDecimal.ZERO), signal.open());
        boolean noLowerShadow = lowerShadow.compareTo(NO_LOWER_SHADOW_TOLERANCE) <= 0;
        boolean defaultMatched = bearishCount >= 4
            && lastThreeBearish
            && threeDayDecline.compareTo(DEFAULT_THREE_DAY_DECLINE) > 0
            && lastDayDecline.compareTo(DEFAULT_LAST_DAY_DECLINE) > 0
            && bullishEngulfing
            && volumeShrinking;

        int score = 0;
        if (bearishCount >= 4) score += 8;
        if (lastThreeBearish) score += 12;
        if (threeDayDecline.compareTo(DEFAULT_THREE_DAY_DECLINE) > 0) score += 12;
        if (lastDayDecline.compareTo(DEFAULT_LAST_DAY_DECLINE) > 0) score += 8;
        if (bullishEngulfing) score += 18;
        if (volumeShrinking) score += 12;
        // 跌幅越大，排序权重越高；连续放量和近似无下影线作为优选加分。
        score += bonusScore(threeDayDecline, DEFAULT_THREE_DAY_DECLINE, new BigDecimal("2"), 12);
        score += bonusScore(lastDayDecline, DEFAULT_LAST_DAY_DECLINE, new BigDecimal("2"), 6);
        if (lastThreeVolumeUp) score += 7;
        if (noLowerShadow) score += 5;
        score = Math.min(100, score);

        StockScreenSnapshotEntity snapshot = new StockScreenSnapshotEntity();
        snapshot.setStockCode(stock.code());
        snapshot.setStockName(stock.name());
        snapshot.setMarket(stock.market());
        snapshot.setBearishCount6(bearishCount);
        snapshot.setLastThreeBearish(lastThreeBearish);
        snapshot.setLastThreeVolumeUp(lastThreeVolumeUp);
        snapshot.setThreeDayDeclinePct(threeDayDecline);
        snapshot.setLastDayDeclinePct(lastDayDecline);
        snapshot.setBullishEngulfing(bullishEngulfing);
        snapshot.setNoLowerShadow(noLowerShadow);
        snapshot.setVolumeShrinking(volumeShrinking);
        snapshot.setVolumeRatio(volumeRatio);
        snapshot.setLowerShadowPct(lowerShadow);
        snapshot.setSignalScore(score);
        snapshot.setDefaultMatched(defaultMatched);
        snapshot.setBearishStartDate(firstBearish.date());
        snapshot.setPreviousDate(previous.date());
        snapshot.setSignalDate(signal.date());
        snapshot.setPreviousOpen(previous.open());
        snapshot.setPreviousClose(previous.close());
        snapshot.setSignalOpen(signal.open());
        snapshot.setSignalClose(signal.close());
        snapshot.setSignalLow(signal.low());
        snapshot.setPreviousVolume(previous.volume());
        snapshot.setSignalVolume(signal.volume());
        return snapshot;
    }

    private LambdaQueryWrapper<StockScreenSnapshotEntity> buildScreenQuery(
        Long runId,
        String market,
        String keyword,
        int minBearishCount,
        BigDecimal minThreeDayDecline,
        BigDecimal minLastDayDecline,
        boolean requireVolumeUp,
        boolean requireNoLowerShadow
    ) {
        LambdaQueryWrapper<StockScreenSnapshotEntity> query = new LambdaQueryWrapper<StockScreenSnapshotEntity>()
            .eq(StockScreenSnapshotEntity::getRunId, runId)
            .ge(StockScreenSnapshotEntity::getBearishCount6, minBearishCount)
            .eq(StockScreenSnapshotEntity::getLastThreeBearish, true)
            .gt(StockScreenSnapshotEntity::getThreeDayDeclinePct, minThreeDayDecline)
            .gt(StockScreenSnapshotEntity::getLastDayDeclinePct, minLastDayDecline)
            .eq(StockScreenSnapshotEntity::getBullishEngulfing, true)
            .eq(StockScreenSnapshotEntity::getVolumeShrinking, true);
        String normalizedMarket = market == null ? "ALL" : market.trim().toUpperCase(Locale.ROOT);
        if (List.of("SH", "SZ", "BJ").contains(normalizedMarket)) {
            query.eq(StockScreenSnapshotEntity::getMarket, normalizedMarket);
        }
        String normalizedKeyword = trim(keyword, 40);
        if (normalizedKeyword != null && !normalizedKeyword.isBlank()) {
            query.and(nested -> nested
                .like(StockScreenSnapshotEntity::getStockCode, normalizedKeyword)
                .or()
                .like(StockScreenSnapshotEntity::getStockName, normalizedKeyword));
        }
        if (requireVolumeUp) {
            query.eq(StockScreenSnapshotEntity::getLastThreeVolumeUp, true);
        }
        if (requireNoLowerShadow) {
            query.eq(StockScreenSnapshotEntity::getNoLowerShadow, true);
        }
        return query;
    }

    private StockScreenRunEntity latestSuccessfulRun() {
        return runMapper.selectOne(new LambdaQueryWrapper<StockScreenRunEntity>()
            .eq(StockScreenRunEntity::getStatus, STATUS_SUCCESS)
            .orderByDesc(StockScreenRunEntity::getTradeDate)
            .orderByDesc(StockScreenRunEntity::getFinishedAt)
            .last("LIMIT 1"));
    }

    private StockScreenRunEntity latestRun() {
        return runMapper.selectOne(new LambdaQueryWrapper<StockScreenRunEntity>()
            .orderByDesc(StockScreenRunEntity::getStartedAt)
            .last("LIMIT 1"));
    }

    private StockScreenRunEntity findReusableRun() {
        LocalDate expectedTradeDate = resolveExpectedTradeDate();
        return runMapper.selectOne(new LambdaQueryWrapper<StockScreenRunEntity>()
            .eq(StockScreenRunEntity::getStatus, STATUS_SUCCESS)
            .eq(StockScreenRunEntity::getTradeDate, expectedTradeDate)
            .eq(StockScreenRunEntity::getRuleVersion, CURRENT_RULE_VERSION)
            .orderByDesc(StockScreenRunEntity::getFinishedAt)
            .last("LIMIT 1"));
    }

    private LocalDate resolveExpectedTradeDate() {
        LocalDateTime now = LocalDateTime.now(MARKET_ZONE);
        LocalDate candidate = now.toLocalTime().isBefore(MARKET_DATA_READY_TIME)
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate();
        while (candidate.getDayOfWeek().getValue() >= 6 || marketClosedDates.contains(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private void cleanupOldRuns(LocalDate cutoff) {
        try {
            runMapper.delete(new LambdaQueryWrapper<StockScreenRunEntity>()
                .ne(StockScreenRunEntity::getStatus, STATUS_RUNNING)
                .isNotNull(StockScreenRunEntity::getTradeDate)
                .lt(StockScreenRunEntity::getTradeDate, cutoff));
        } catch (Exception ex) {
            log.warn("清理历史选股扫描失败：cutoff={}, reason={}", cutoff, ex.getMessage());
        }
    }

    private ScanSummary finishCanceledRun(
        StockScreenRunEntity run,
        int processed,
        int failed,
        List<StockScreenSnapshotEntity> snapshots
    ) {
        cancelPendingSnapshots(run.getId(), snapshots);
        run.setStatus(STATUS_CANCELED);
        run.setProcessedStocks(processed);
        run.setFailedStocks(failed);
        run.setMatchedStocks(0);
        run.setFinishedAt(LocalDateTime.now(MARKET_ZONE));
        run.setResultMessage("用户停止扫描，已处理=" + processed + ", 行情失败=" + failed);
        runMapper.updateById(run);
        log.info("A股全市场扫描已停止：runId={}, processed={}, failed={}", run.getId(), processed, failed);
        return new ScanSummary(STATUS_CANCELED, null, run.getTotalStocks(), processed - failed, 0, run.getResultMessage());
    }

    private void cancelPendingFutures(List<CompletableFuture<ScanOutcome>> futures) {
        for (CompletableFuture<ScanOutcome> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private void cancelPendingSnapshots(Long runId, List<StockScreenSnapshotEntity> snapshots) {
        if (runId == null || snapshots.isEmpty()) {
            return;
        }
        snapshotMapper.delete(new LambdaQueryWrapper<StockScreenSnapshotEntity>()
            .eq(StockScreenSnapshotEntity::getRunId, runId));
    }

    private JsonNode fetchJson(String url, String referer) throws Exception {
        return objectMapper.readTree(fetchText(url, referer));
    }

    private String fetchText(String url, String referer) {
        return restClient.get()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", referer)
            .retrieve()
            .body(String.class);
    }

    private DailyBar toDailyBar(JsonNode row) {
        try {
            LocalDate date = LocalDate.parse(row.path("day").asText());
            BigDecimal open = new BigDecimal(row.path("open").asText());
            BigDecimal high = new BigDecimal(row.path("high").asText());
            BigDecimal low = new BigDecimal(row.path("low").asText());
            BigDecimal close = new BigDecimal(row.path("close").asText());
            long volume = new BigDecimal(row.path("volume").asText("0")).longValue();
            if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0 || volume <= 0) {
                return null;
            }
            return new DailyBar(date, open, high, low, close, volume);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBearish(DailyBar bar) {
        return bar.close().compareTo(bar.open()) < 0;
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePercent(BigDecimal value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(50)).setScale(2, RoundingMode.HALF_UP);
    }

    private int bonusScore(BigDecimal value, BigDecimal threshold, BigDecimal multiplier, int maximum) {
        if (value == null || value.compareTo(threshold) <= 0) {
            return 0;
        }
        return Math.min(maximum, value.subtract(threshold).multiply(multiplier).intValue());
    }

    private boolean isPositiveNumber(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return false;
        }
        try {
            return new BigDecimal(value.asText()).signum() > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String resolveMarket(String code) {
        if (code.matches("6\\d{5}")) return "SH";
        if (code.matches("[03]\\d{5}")) return "SZ";
        if (code.matches("92\\d{4}")) return "BJ";
        return null;
    }

    private String marketPrefix(String market) {
        return market.toLowerCase(Locale.ROOT);
    }

    private StockScreenRunResponse toRunResponse(StockScreenRunEntity entity) {
        if (entity == null) {
            return null;
        }
        StockScreenRunResponse response = new StockScreenRunResponse();
        response.setId(entity.getId());
        response.setTradeDate(entity.getTradeDate());
        response.setTriggerName(entity.getTriggerName());
        response.setStatus(entity.getStatus());
        response.setTotalStocks(entity.getTotalStocks());
        response.setProcessedStocks(entity.getProcessedStocks());
        response.setMatchedStocks(entity.getMatchedStocks());
        response.setFailedStocks(entity.getFailedStocks());
        response.setDataSource(entity.getDataSource());
        response.setRuleVersion(entity.getRuleVersion());
        response.setResultMessage(entity.getResultMessage());
        response.setErrorMessage(entity.getErrorMessage());
        response.setStartedAt(entity.getStartedAt());
        response.setFinishedAt(entity.getFinishedAt());
        return response;
    }

    private StockScreenItemResponse toItemResponse(StockScreenSnapshotEntity entity) {
        StockScreenItemResponse response = new StockScreenItemResponse();
        response.setStockCode(entity.getStockCode());
        response.setStockName(entity.getStockName());
        response.setMarket(entity.getMarket());
        response.setBearishCount6(entity.getBearishCount6());
        response.setLastThreeBearish(entity.getLastThreeBearish());
        response.setLastThreeVolumeUp(entity.getLastThreeVolumeUp());
        response.setThreeDayDeclinePct(entity.getThreeDayDeclinePct());
        response.setLastDayDeclinePct(entity.getLastDayDeclinePct());
        response.setBullishEngulfing(entity.getBullishEngulfing());
        response.setNoLowerShadow(entity.getNoLowerShadow());
        response.setVolumeShrinking(entity.getVolumeShrinking());
        response.setVolumeRatio(entity.getVolumeRatio());
        response.setLowerShadowPct(entity.getLowerShadowPct());
        response.setSignalScore(entity.getSignalScore());
        response.setBearishStartDate(entity.getBearishStartDate());
        response.setPreviousDate(entity.getPreviousDate());
        response.setSignalDate(entity.getSignalDate());
        response.setPreviousOpen(entity.getPreviousOpen());
        response.setPreviousClose(entity.getPreviousClose());
        response.setSignalOpen(entity.getSignalOpen());
        response.setSignalClose(entity.getSignalClose());
        response.setPreviousVolume(entity.getPreviousVolume());
        response.setSignalVolume(entity.getSignalVolume());
        return response;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private ThreadFactory daemonThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdownExecutors() {
        quoteFetchExecutor.shutdownNow();
        manualScanExecutor.shutdownNow();
    }

    public record ScanSummary(
        String status,
        LocalDate tradeDate,
        int totalStocks,
        int validStocks,
        int matchedStocks,
        String message
    ) {
    }

    public record ScanSubmission(
        String status,
        String message,
        Long runId,
        LocalDate tradeDate
    ) {
    }

    public record StopSubmission(
        String status,
        String message,
        Long runId
    ) {
    }

    private record StockInstrument(String code, String name, String symbol, String market) {
    }

    private record DailyBar(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
    ) {
    }

    private record ScanOutcome(StockScreenSnapshotEntity snapshot, boolean failed) {
    }
}
