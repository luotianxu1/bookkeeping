package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.client.MarketQuoteClient;
import com.example.app.entity.InvestmentDividendPlanEntity;
import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentProductEntity;
import com.example.app.mapper.InvestmentDividendPlanMapper;
import com.example.app.mapper.InvestmentPositionMapper;
import com.example.app.mapper.InvestmentProductMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.app.service.InvestmentCalcUtils.*;

/**
 * 投资分红域服务：基金/股票分红计划同步、历史抓取与分红画像评估。
 * 自 InvestmentService 拆出；纯比较/状态方法为 static，供主服务经 static import 复用。
 */
@Service
public class InvestmentDividendService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentDividendService.class);

    // 与 InvestmentService 中同名常量保持一致（分红域专用副本）
    private static final String FUND_PRODUCT_TYPE = "fund";
    private static final String STOCK_PRODUCT_TYPE = "stock";
    private static final String ACTIVE_STATUS = "active";
    private static final String DEFAULT_CURRENCY_CODE = "CNY";
    public static final String FUND_DIVIDEND_PLAN_SOURCE = "EASTMONEY_FUND_FHSP";
    public static final String STOCK_DIVIDEND_HISTORY_SOURCE = "EASTMONEY_STOCK_BONUS";
    private static final int DIVIDEND_HISTORY_YEARS = 3;
    private static final int MIN_STABLE_DIVIDEND_YEARS = 2;

    private final InvestmentPositionMapper positionMapper;
    private final InvestmentProductMapper productMapper;
    private final InvestmentDividendPlanMapper dividendPlanMapper;
    private final MarketQuoteClient marketQuoteClient;
    private final ExecutorService dividendSyncExecutor;

    public InvestmentDividendService(
        InvestmentPositionMapper positionMapper,
        InvestmentProductMapper productMapper,
        InvestmentDividendPlanMapper dividendPlanMapper,
        MarketQuoteClient marketQuoteClient
    ) {
        this.positionMapper = positionMapper;
        this.productMapper = productMapper;
        this.dividendPlanMapper = dividendPlanMapper;
        this.marketQuoteClient = marketQuoteClient;
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "dividend-sync-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.dividendSyncExecutor = Executors.newFixedThreadPool(8, factory);
    }

    @PreDestroy
    public void shutdown() {
        dividendSyncExecutor.shutdownNow();
    }

        private record DividendProfile(
        boolean stable,
        BigDecimal annualDividendPerUnit,
        int stableYears,
        LocalDate lastDividendDate,
        String source
    ) {
    }

    private record DividendHistoryFetchResult(
        boolean success,
        List<InvestmentDividendPlanEntity> plans
    ) {
    }

    // ---------- static 纯方法 ----------

        public static String dividendPlanKey(Integer dividendYear, LocalDate payDate) {
        return String.valueOf(dividendYear) + '|' + String.valueOf(payDate);
    }

    public static String resolveDividendPlanStatus(LocalDate payDate) {
        if (payDate == null) {
            return "confirmed";
        }
        return payDate.isBefore(LocalDate.now()) ? "paid" : "confirmed";
    }

    public static boolean mergeFundDividendPlan(InvestmentDividendPlanEntity target, InvestmentDividendPlanEntity source) {
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

    public static boolean supportsDividendProfile(InvestmentProductEntity product) {
        return product != null
            && StringUtils.hasText(product.getSymbol())
            && (FUND_PRODUCT_TYPE.equals(product.getProductType()) || "stock".equals(product.getProductType()));
    }

    public static Integer resolveStockDividendYear(LocalDate exDividendDate) {
        return exDividendDate == null ? null : exDividendDate.getYear();
    }

    public static boolean isHistoricalDividendPlan(InvestmentDividendPlanEntity plan, LocalDate today) {
        if (plan == null) {
            return false;
        }
        LocalDate payDate = plan.getPayDate();
        if (payDate == null) {
            return "paid".equals(plan.getStatus());
        }
        return !payDate.isAfter(today);
    }

    public static Integer resolveDividendPlanYear(InvestmentDividendPlanEntity plan, LocalDate today) {
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

    public static BigDecimal resolveNetDividendPerUnit(InvestmentDividendPlanEntity plan) {
        BigDecimal dividendPerUnit = defaultZero(plan.getDividendPerUnit());
        BigDecimal taxRate = defaultZero(plan.getTaxRate());
        BigDecimal netFactor = BigDecimal.ONE.subtract(taxRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return dividendPerUnit.multiply(netFactor).setScale(6, RoundingMode.HALF_UP);
    }

    public static InvestmentDividendPlanEntity pickLaterDividendPlan(
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

    public static int compareDividendPlanRecency(InvestmentDividendPlanEntity left, InvestmentDividendPlanEntity right) {
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

    public static LocalDate dividendPlanRecencyDate(InvestmentDividendPlanEntity plan) {
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

    // ---------- 同步入口 ----------

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

        // 按产品并发抓取分红计划（外部 HTTP 为主，串行会显著拉长夜间窗口）
        AtomicInteger syncedProducts = new AtomicInteger();
        AtomicInteger syncedPlans = new AtomicInteger();
        List<CompletableFuture<Void>> futures = products.values().stream()
            .map(product -> CompletableFuture.runAsync(() -> {
                try {
                    int updatedCount = syncFundDividendPlansForProduct(product);
                    if (updatedCount > 0) {
                        syncedProducts.incrementAndGet();
                        syncedPlans.addAndGet(updatedCount);
                    }
                } catch (Exception ex) {
                    log.warn("基金分红计划同步失败，productId={}, symbol={}, reason={}",
                        product.getId(), product.getSymbol(), ex.getMessage());
                }
            }, dividendSyncExecutor))
            .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("基金分红计划同步完成：{} 个基金产品，{} 条计划已写入", syncedProducts.get(), syncedPlans.get());
        return syncedPlans.get();
    }

    public int syncStockDividendProfiles() {
        List<InvestmentPositionEntity> positions = listActiveStockPositions();
        if (positions.isEmpty()) {
            log.info("股票分红画像同步跳过：当前没有股票持仓");
            return 0;
        }

        Map<Long, InvestmentProductEntity> products = loadStockProducts(positions);
        if (products.isEmpty()) {
            log.info("股票分红画像同步跳过：当前没有可同步的股票产品");
            return 0;
        }

        // 按产品并发评估股票分红画像（外部 HTTP 为主）
        AtomicInteger syncedProducts = new AtomicInteger();
        List<CompletableFuture<Void>> futures = products.values().stream()
            .map(product -> CompletableFuture.runAsync(() -> {
                try {
                    if (!evaluateDividendProfile(product)) {
                        return;
                    }
                    productMapper.updateById(product);
                    syncedProducts.incrementAndGet();
                } catch (Exception ex) {
                    log.warn("股票分红画像同步失败，productId={}, symbol={}, reason={}",
                        product.getId(), product.getSymbol(), ex.getMessage());
                }
            }, dividendSyncExecutor))
            .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("股票分红画像同步完成：{} 个股票产品已重新评估", syncedProducts.get());
        return syncedProducts.get();
    }

    public int syncFundDividendPlansForProduct(InvestmentProductEntity product) {
        DividendHistoryFetchResult fetchResult = fetchFundDividendPlansResult(product);
        if (!fetchResult.success()) {
            throw new IllegalStateException("基金分红历史获取失败");
        }
        List<InvestmentDividendPlanEntity> remotePlans = fetchResult.plans();
        int changedCount = 0;
        // 远端获取成功后，本地不存在于远端结果中的同源计划一律删除；
        // 远端为空表示该基金当前无分红记录，需要清掉历史上可能串入的脏数据。
        Set<String> remotePlanKeys = remotePlans.stream()
            .map(plan -> dividendPlanKey(plan.getDividendYear(), plan.getPayDate()))
            .collect(Collectors.toSet());
        List<InvestmentDividendPlanEntity> stalePlans = dividendPlanMapper.selectList(new LambdaQueryWrapper<InvestmentDividendPlanEntity>()
                .eq(InvestmentDividendPlanEntity::getProductId, product.getId())
                .eq(InvestmentDividendPlanEntity::getSource, FUND_DIVIDEND_PLAN_SOURCE))
            .stream()
            .filter(existing -> !remotePlanKeys.contains(dividendPlanKey(existing.getDividendYear(), existing.getPayDate())))
            .toList();
        for (InvestmentDividendPlanEntity stalePlan : stalePlans) {
            dividendPlanMapper.deleteById(stalePlan.getId());
            changedCount++;
        }
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
        List<InvestmentDividendPlanEntity> historicalPlans = remotePlans.stream()
            .filter(plan -> isHistoricalDividendPlan(plan, LocalDate.now()))
            .toList();
        applyDividendProfile(product, buildDividendProfile(historicalPlans, FUND_DIVIDEND_PLAN_SOURCE));
        if (product.getId() != null) {
            productMapper.updateById(product);
        }
        return changedCount;
    }

    public boolean evaluateDividendProfile(InvestmentProductEntity product) {
        if (!supportsDividendProfile(product)) {
            applyDividendProfile(product, null);
            return true;
        }
        DividendHistoryFetchResult fetchResult = FUND_PRODUCT_TYPE.equals(product.getProductType())
            ? fetchFundHistoricalDividendPlansResult(product)
            : fetchStockHistoricalDividendPlansResult(product);
        if (!fetchResult.success()) {
            return false;
        }
        String source = FUND_PRODUCT_TYPE.equals(product.getProductType())
            ? FUND_DIVIDEND_PLAN_SOURCE
            : STOCK_DIVIDEND_HISTORY_SOURCE;
        applyDividendProfile(product, buildDividendProfile(fetchResult.plans(), source));
        return true;
    }

    public List<InvestmentDividendPlanEntity> fetchFundHistoricalDividendPlans(InvestmentProductEntity product) {
        return fetchFundHistoricalDividendPlansResult(product).plans();
    }

    public List<InvestmentDividendPlanEntity> fetchStockHistoricalDividendPlans(InvestmentProductEntity product) {
        return fetchStockHistoricalDividendPlansResult(product).plans();
    }

    // ---------- 内部实现 ----------

        private DividendHistoryFetchResult fetchFundDividendPlansResult(InvestmentProductEntity product) {
        if (product == null || !StringUtils.hasText(product.getSymbol())) {
            return new DividendHistoryFetchResult(false, Collections.emptyList());
        }
        try {
            JsonNode payload = marketQuoteClient.fundDividends(product.getSymbol());
            if (payload == null || !payload.path("matched").asBoolean(false)) {
                log.warn("基金分红历史获取失败，productId={}, symbol={}, reason=页面基金代码不匹配",
                    product.getId(), product.getSymbol());
                return new DividendHistoryFetchResult(false, Collections.emptyList());
            }
            JsonNode items = payload.path("items");
            if (!items.isArray()) {
                return new DividendHistoryFetchResult(false, Collections.emptyList());
            }
            List<InvestmentDividendPlanEntity> plans = new ArrayList<>();
            for (JsonNode row : items) {
                int dividendYear = row.path("dividendYear").asInt(0);
                LocalDate recordDate = safeDate(row.path("recordDate").asText(null));
                LocalDate exDividendDate = safeDate(row.path("exDividendDate").asText(null));
                BigDecimal dividendPerUnit = safeDecimal(row.path("dividendPerUnit").asText(null));
                LocalDate payDate = safeDate(row.path("payDate").asText(null));
                if (dividendYear <= 0 || dividendPerUnit == null || payDate == null) {
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
            return new DividendHistoryFetchResult(true, plans);
        } catch (Exception ex) {
            log.warn("基金分红历史获取失败，productId={}, symbol={}, reason={}",
                product.getId(), product.getSymbol(), ex.getMessage());
            return new DividendHistoryFetchResult(false, Collections.emptyList());
        }
    }

    private DividendHistoryFetchResult fetchFundHistoricalDividendPlansResult(InvestmentProductEntity product) {
        DividendHistoryFetchResult result = fetchFundDividendPlansResult(product);
        if (!result.success()) {
            return result;
        }
        return new DividendHistoryFetchResult(true, result.plans().stream()
            .filter(plan -> isHistoricalDividendPlan(plan, LocalDate.now()))
            .toList());
    }

    private DividendHistoryFetchResult fetchStockHistoricalDividendPlansResult(InvestmentProductEntity product) {
        if (product == null || !StringUtils.hasText(product.getSymbol())) {
            return new DividendHistoryFetchResult(false, Collections.emptyList());
        }
        try {
            JsonNode payload = marketQuoteClient.stockDividends(product.getSymbol());
            if (payload == null || !payload.path("matched").asBoolean(false)) {
                log.warn("股票分红历史获取失败，productId={}, symbol={}, reason=页面不匹配",
                    product.getId(), product.getSymbol());
                return new DividendHistoryFetchResult(false, Collections.emptyList());
            }
            JsonNode rows = payload.path("items");
            if (!rows.isArray()) {
                log.warn("股票分红历史解析失败，productId={}, symbol={}, reason=响应中没有数据数组",
                    product.getId(), product.getSymbol());
                return new DividendHistoryFetchResult(false, Collections.emptyList());
            }
            List<InvestmentDividendPlanEntity> plans = new ArrayList<>();
            for (JsonNode row : rows) {
                BigDecimal pretaxBonus = safeDecimal(row.path("pretaxPer10").asText(null));
                LocalDate exDividendDate = safeDate(row.path("exDividendDate").asText(null));
                if (pretaxBonus == null || pretaxBonus.compareTo(BigDecimal.ZERO) <= 0 || exDividendDate == null) {
                    continue;
                }
                LocalDate recordDate = safeDate(row.path("recordDate").asText(null));
                String progress = row.path("progress").asText(null);
                InvestmentDividendPlanEntity plan = new InvestmentDividendPlanEntity();
                plan.setProductId(product.getId());
                plan.setDividendYear(resolveStockDividendYear(exDividendDate));
                plan.setRecordDate(recordDate);
                plan.setExDividendDate(exDividendDate);
                plan.setPayDate(exDividendDate);
                plan.setDividendPerUnit(pretaxBonus.divide(BigDecimal.TEN, 6, RoundingMode.HALF_UP));
                plan.setTaxRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                plan.setCurrencyCode(blankToDefault(product.getCurrencyCode(), DEFAULT_CURRENCY_CODE));
                plan.setStatus(resolveDividendPlanStatus(exDividendDate));
                plan.setSource(STOCK_DIVIDEND_HISTORY_SOURCE);
                plan.setRemark(progress);
                plans.add(plan);
            }
            return new DividendHistoryFetchResult(true, plans);
        } catch (Exception ex) {
            log.warn("股票分红历史获取失败，productId={}, symbol={}, reason={}",
                product.getId(), product.getSymbol(), ex.getMessage());
            return new DividendHistoryFetchResult(false, Collections.emptyList());
        }
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
            .sorted(Comparator.comparing(InvestmentDividendService::dividendPlanRecencyDate).reversed())
            .toList();
        if (recentPlans.isEmpty()) {
            return null;
        }

        Set<Integer> recentYears = recentPlans.stream()
            .map(plan -> resolveDividendPlanYear(plan, today))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate latestPayDate = recentPlans.stream()
            .map(InvestmentDividendService::dividendPlanRecencyDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(null);

        boolean stable = recentYears.size() >= MIN_STABLE_DIVIDEND_YEARS
            && latestPayDate != null
            && !latestPayDate.isBefore(today.minusYears(2));

        long positivePlanCount = recentPlans.stream()
            .map(InvestmentDividendService::resolveNetDividendPerUnit)
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .count();
        if (positivePlanCount < MIN_STABLE_DIVIDEND_YEARS) {
            stable = false;
        }

        LocalDate trailingYearStart = today.minusYears(1);
        long trailingYearDividendCount = recentPlans.stream()
            .filter(plan -> {
                LocalDate recencyDate = dividendPlanRecencyDate(plan);
                return recencyDate != null && !recencyDate.isBefore(trailingYearStart) && !recencyDate.isAfter(today);
            })
            .map(InvestmentDividendService::resolveNetDividendPerUnit)
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .count();

        BigDecimal latestDividendPerUnit = recentPlans.stream()
            .map(InvestmentDividendService::resolveNetDividendPerUnit)
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .orElse(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        BigDecimal predictedAnnualDividendPerUnit = latestDividendPerUnit == null
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : defaultZero(latestDividendPerUnit)
                .multiply(BigDecimal.valueOf(Math.max(trailingYearDividendCount, 1)))
                .setScale(6, RoundingMode.HALF_UP);
        return new DividendProfile(
            stable,
            predictedAnnualDividendPerUnit,
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

    // ---------- 持仓/产品查询（与主服务同口径的小查询副本） ----------

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
}
