package com.example.app.service;

import com.example.app.dto.MarketStatusResponse;
import com.example.app.market.ActivityService;
import com.example.app.market.SinaClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 大盘状态：数据源与行情中心（global 页）统一 ——
 * <ul>
 *   <li>指数行情：新浪批量行情（{@link SinaClient#batchQuotes}，hq.sinajs.cn）</li>
 *   <li>涨跌家数：全市场口径统计（{@link ActivityService#marketActivity}，新浪 hs_a 分页，自带 60s 缓存）</li>
 * </ul>
 * 不再依赖东方财富 ulist 接口。30s 内存缓存，失败回退旧缓存。
 */
@Service
public class MarketStatusService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 30_000L;
    /** 参与情绪评分的指数（平均涨跌幅），口径与东财版一致 */
    private static final Set<String> SCORE_CODES = Set.of("000001", "399001", "399006", "000300");
    /** 成交额合计口径：沪 + 深 + 北（对齐原东财 f6 合计） */
    private static final Set<String> TURNOVER_CODES = Set.of("000001", "399001", "899050");
    /**
     * 指数清单（新浪代码 → 默认名）。大盘状态口径需要深证成指，
     * 独立于行情中心 SINA_INDEX_CODES（那里已按需求移除深证成指）。
     */
    private static final List<String[]> INDEX_CODES = List.of(
        new String[]{"sh000001", "上证指数"},
        new String[]{"sz399001", "深证成指"},
        new String[]{"sz399006", "创业板指"},
        new String[]{"sh000688", "科创50"},
        new String[]{"bj899050", "北证50"},
        new String[]{"sh000300", "沪深300"}
    );

    private final AtomicReference<CachedStatus> cache = new AtomicReference<>();

    public MarketStatusResponse getMarketStatus() {
        long now = System.currentTimeMillis();
        CachedStatus cached = cache.get();
        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return cached.response();
        }
        try {
            MarketStatusResponse response = fetchMarketStatus();
            cache.set(new CachedStatus(response, now));
            return response;
        } catch (Exception ex) {
            if (cached != null) {
                return cached.response();
            }
            throw new IllegalStateException("大盘状态加载失败", ex);
        }
    }

    private MarketStatusResponse fetchMarketStatus() {
        // 指数行情：新浪批量行情（行情中心同款通道，一次请求取全 6 个指数）
        List<String> codes = INDEX_CODES.stream().map(c -> c[0]).toList();
        Map<String, List<String>> fieldsByCode = SinaClient.batchQuotes(codes);

        List<MarketStatusResponse.IndexQuote> indices = new ArrayList<>();
        Map<String, MarketStatusResponse.IndexQuote> quoteByCode = new LinkedHashMap<>();
        BigDecimal turnover = BigDecimal.ZERO;
        for (String[] def : INDEX_CODES) {
            List<String> fields = fieldsByCode.get(def[0]);
            SinaClient.QuoteRow row = fields == null ? null : SinaClient.quoteRow(def[0], fields, def[1]);
            if (row == null) {
                continue;
            }
            MarketStatusResponse.IndexQuote quote = new MarketStatusResponse.IndexQuote();
            quote.setCode(String.valueOf(row.get("代码")));
            quote.setName(String.valueOf(row.get("名称")));
            quote.setValue(toBigDecimal(row.get("最新价")));
            quote.setChange(toBigDecimal(row.get("涨跌额")));
            quote.setChangePercent(toBigDecimal(row.get("涨跌幅")));
            indices.add(quote);
            quoteByCode.put(quote.getCode(), quote);

            if (TURNOVER_CODES.contains(quote.getCode()) && row.get("成交额") instanceof Number amt) {
                turnover = turnover.add(BigDecimal.valueOf(amt.doubleValue()));
            }
        }
        if (indices.isEmpty()) {
            throw new IllegalStateException("大盘行情格式异常");
        }

        // 涨跌家数：全市场口径（新浪 hs_a 分页统计）；sample 为 0 说明统计不可用，触发缓存回退
        Map<String, Object> activity = ActivityService.marketActivity("a");
        int sample = ((Number) activity.getOrDefault("sample", 0)).intValue();
        if (sample <= 0) {
            throw new IllegalStateException("涨跌家数统计不可用");
        }
        int advanceCount = ((Number) activity.getOrDefault("up", 0)).intValue();
        int declineCount = ((Number) activity.getOrDefault("down", 0)).intValue();
        int flatCount = ((Number) activity.getOrDefault("flat", 0)).intValue();

        double indexScore = SCORE_CODES.stream()
            .map(quoteByCode::get)
            .filter(item -> item != null && item.getChangePercent() != null)
            .mapToDouble(item -> item.getChangePercent().doubleValue())
            .average()
            .orElse(0D);
        int directionalCount = advanceCount + declineCount;
        double advanceRatio = directionalCount == 0 ? 0.5D : (double) advanceCount / directionalCount;
        MarketRegime regime = resolveRegime(indexScore, advanceRatio);

        MarketStatusResponse response = new MarketStatusResponse();
        response.setStatusKey(regime.key());
        response.setStatusLabel(regime.label());
        response.setTone(regime.tone());
        response.setSummary(regime.summary());
        response.setAdvanceCount(advanceCount);
        response.setDeclineCount(declineCount);
        response.setFlatCount(flatCount);
        response.setAdvanceRatio(BigDecimal.valueOf(advanceRatio * 100).setScale(2, RoundingMode.HALF_UP));
        response.setTurnover(turnover.setScale(0, RoundingMode.HALF_UP));
        response.setUpdatedAt(LocalDateTime.now(MARKET_ZONE));
        response.setSource("新浪公开行情");
        response.setIndices(indices);
        return response;
    }

    private MarketRegime resolveRegime(double indexScore, double advanceRatio) {
        if (indexScore <= -2D || advanceRatio < 0.28D) {
            return new MarketRegime("panic", "恐慌下跌", "negative", "指数与多数个股同步走弱，市场风险偏好较低。");
        }
        if (indexScore <= -0.6D || advanceRatio < 0.42D) {
            return new MarketRegime("weak", "弱势调整", "negative", "下跌个股占优，反弹信号需要控制仓位。");
        }
        if (indexScore >= 1D || advanceRatio >= 0.62D) {
            return new MarketRegime("strong", "强势上涨", "positive", "指数与个股多数走强，市场风险偏好较高。");
        }
        if (indexScore > 0.2D && advanceRatio >= 0.5D) {
            return new MarketRegime("rebound", "反弹修复", "positive", "市场正在修复，上涨家数略占优势。");
        }
        return new MarketRegime("balanced", "震荡分化", "neutral", "指数和个股表现分化，适合精选形态信号。");
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private record MarketRegime(String key, String label, String tone, String summary) {
    }

    private record CachedStatus(MarketStatusResponse response, long cachedAt) {
    }
}
