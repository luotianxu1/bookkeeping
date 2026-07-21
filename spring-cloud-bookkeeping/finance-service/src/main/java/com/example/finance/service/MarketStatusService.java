package com.example.finance.service;

import com.example.finance.dto.MarketStatusResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class MarketStatusService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 30_000L;
    private static final Set<String> MARKET_BREADTH_CODES = Set.of("000001", "399001", "899050");
    private static final Set<String> SCORE_CODES = Set.of("000001", "399001", "399006", "000300");

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String indexApiUrl;
    private final AtomicReference<CachedStatus> cache = new AtomicReference<>();

    public MarketStatusService(
        ObjectMapper objectMapper,
        @Value("${finance.market-status.index-api-url:https://push2delay.eastmoney.com/api/qt/ulist.np/get}") String indexApiUrl
    ) {
        this.objectMapper = objectMapper;
        this.indexApiUrl = indexApiUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3500);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

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

    private MarketStatusResponse fetchMarketStatus() throws Exception {
        String url = indexApiUrl
            + "?fltt=2&invt=2"
            + "&fields=f12,f13,f14,f2,f3,f4,f5,f6,f104,f105,f106"
            + "&secids=1.000001,0.399001,0.399006,1.000688,0.899050,1.000300";
        String body = restClient.get()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://quote.eastmoney.com/")
            .retrieve()
            .body(String.class);
        JsonNode rows = objectMapper.readTree(body).path("data").path("diff");
        if (!rows.isArray() || rows.isEmpty()) {
            throw new IllegalStateException("大盘行情格式异常");
        }

        List<MarketStatusResponse.IndexQuote> indices = new ArrayList<>();
        int advanceCount = 0;
        int declineCount = 0;
        int flatCount = 0;
        BigDecimal turnover = BigDecimal.ZERO;
        for (JsonNode row : rows) {
            String code = row.path("f12").asText("");
            MarketStatusResponse.IndexQuote quote = new MarketStatusResponse.IndexQuote();
            quote.setCode(code);
            quote.setName(row.path("f14").asText("指数"));
            quote.setValue(decimal(row.path("f2")));
            quote.setChange(decimal(row.path("f4")));
            quote.setChangePercent(decimal(row.path("f3")));
            indices.add(quote);

            if (MARKET_BREADTH_CODES.contains(code)) {
                advanceCount += integer(row.path("f104"));
                declineCount += integer(row.path("f105"));
                flatCount += integer(row.path("f106"));
                turnover = turnover.add(decimal(row.path("f6")));
            }
        }

        Map<String, MarketStatusResponse.IndexQuote> quoteByCode = indices.stream()
            .collect(Collectors.toMap(MarketStatusResponse.IndexQuote::getCode, item -> item));
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
        response.setSource("东方财富公开行情");
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

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(node.asText("0"));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private int integer(JsonNode node) {
        return decimal(node).intValue();
    }

    private record MarketRegime(String key, String label, String tone, String summary) {
    }

    private record CachedStatus(MarketStatusResponse response, long cachedAt) {
    }
}
