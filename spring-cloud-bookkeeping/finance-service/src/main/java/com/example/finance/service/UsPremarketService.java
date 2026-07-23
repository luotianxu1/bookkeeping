package com.example.finance.service;

import com.example.finance.dto.UsPremarketResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class UsPremarketService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 30_000L;
    private static final List<IndexProxy> INDEX_PROXIES = List.of(
        new IndexProxy("SPX", "标普500", "SPY", "SPDR标普500 ETF"),
        new IndexProxy("NDX100", "纳斯达克100", "QQQ", "Invesco QQQ ETF")
    );

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String quoteInfoApiUrl;
    private final AtomicReference<CachedPremarket> cache = new AtomicReference<>();

    public UsPremarketService(
        ObjectMapper objectMapper,
        @Value("${finance.us-premarket.quote-info-api-url:https://api.nasdaq.com/api/quote}") String quoteInfoApiUrl
    ) {
        this.objectMapper = objectMapper;
        this.quoteInfoApiUrl = quoteInfoApiUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(6000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public UsPremarketResponse getPremarket() {
        long now = System.currentTimeMillis();
        CachedPremarket cached = cache.get();
        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return cached.response();
        }
        try {
            UsPremarketResponse response = fetchPremarket();
            cache.set(new CachedPremarket(response, now));
            return response;
        } catch (Exception ex) {
            if (cached != null) {
                return cached.response();
            }
            throw new IllegalStateException("美股指数盘前行情加载失败", ex);
        }
    }

    private UsPremarketResponse fetchPremarket() {
        List<CompletableFuture<IndexResult>> futures = INDEX_PROXIES.stream()
            .map(proxy -> CompletableFuture.supplyAsync(() -> fetchIndexQuote(proxy))
                .exceptionally(error -> null))
            .toList();
        List<IndexResult> results = futures.stream()
            .map(CompletableFuture::join)
            .filter(item -> item != null && item.quote() != null)
            .toList();
        if (results.isEmpty()) {
            throw new IllegalStateException("Nasdaq指数盘前行情暂无可用数据");
        }

        String sessionStatus = results.stream()
            .map(IndexResult::sessionStatus)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("Unknown");
        UsPremarketResponse response = new UsPremarketResponse();
        response.setSessionStatus(sessionStatus);
        response.setSessionLabel(resolveSessionLabel(sessionStatus));
        response.setUpdatedAt(LocalDateTime.now(MARKET_ZONE));
        response.setSource(resolveSourceLabel(sessionStatus));
        response.setIndices(results.stream().map(IndexResult::quote).toList());
        return response;
    }

    private IndexResult fetchIndexQuote(IndexProxy proxy) {
        String url = quoteInfoApiUrl + "/" + proxy.proxySymbol() + "/info?assetclass=etf";
        JsonNode data = requestJson(url).path("data");
        JsonNode primary = data.path("primaryData");
        JsonNode secondary = data.path("secondaryData");
        if (primary.isMissingNode() || primary.isNull()) {
            return null;
        }
        String sessionStatus = normalizeSessionStatus(data.path("marketStatus").asText("Unknown"));
        JsonNode activeQuote = selectActiveQuote(primary, secondary, sessionStatus);
        UsPremarketResponse.IndexQuote quote = new UsPremarketResponse.IndexQuote();
        quote.setIndexCode(proxy.indexCode());
        quote.setIndexName(proxy.indexName());
        quote.setProxySymbol(proxy.proxySymbol());
        quote.setProxyName(proxy.proxyName());
        quote.setPrice(decimal(firstField(activeQuote, primary, "lastSalePrice")));
        quote.setChange(decimal(firstField(activeQuote, primary, "netChange")));
        quote.setChangePercent(decimal(firstField(activeQuote, primary, "percentageChange")));
        quote.setVolume(longValue(firstField(activeQuote, primary, "volume")));
        quote.setBidPrice(decimal(firstField(activeQuote, primary, "bidPrice")));
        quote.setAskPrice(decimal(firstField(activeQuote, primary, "askPrice")));
        quote.setLastTradeTime(firstText(activeQuote, primary, "lastTradeTimestamp"));
        if (quote.getPrice() == null) {
            return null;
        }
        return new IndexResult(sessionStatus, quote);
    }

    private JsonNode requestJson(String url) {
        String body = restClient.get()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/126 Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://www.nasdaq.com")
            .header("Referer", "https://www.nasdaq.com/market-activity/etf")
            .retrieve()
            .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("status").path("rCode").asInt(500) != 200) {
                throw new IllegalStateException("Nasdaq行情接口返回异常");
            }
            return root;
        } catch (Exception ex) {
            throw new IllegalStateException("Nasdaq行情解析失败", ex);
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("")
            .replace("$", "")
            .replace(",", "")
            .replace("%", "")
            .trim();
        if (value.isEmpty() || "N/A".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long longValue(JsonNode node) {
        BigDecimal value = decimal(node);
        return value == null ? null : value.longValue();
    }

    private JsonNode selectActiveQuote(JsonNode primary, JsonNode secondary, String status) {
        if (isExtendedSession(status) && hasPrice(secondary)) {
            return secondary;
        }
        return primary;
    }

    private boolean isExtendedSession(String status) {
        return "Pre-Market".equals(status) || "After-Hours".equals(status);
    }

    private boolean hasPrice(JsonNode node) {
        return decimal(node.path("lastSalePrice")) != null;
    }

    private JsonNode firstField(JsonNode preferred, JsonNode fallback, String fieldName) {
        JsonNode preferredValue = preferred.path(fieldName);
        if (!isBlankValue(preferredValue)) {
            return preferredValue;
        }
        return fallback.path(fieldName);
    }

    private String firstText(JsonNode preferred, JsonNode fallback, String fieldName) {
        JsonNode value = firstField(preferred, fallback, fieldName);
        return isBlankValue(value) ? "" : value.asText("");
    }

    private boolean isBlankValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return true;
        }
        String value = node.asText("").trim();
        return value.isEmpty() || "N/A".equalsIgnoreCase(value);
    }

    private String normalizeSessionStatus(String status) {
        return switch (status) {
            case "Open", "Market Open" -> "Market Open";
            case "Pre-Market", "Premarket" -> "Pre-Market";
            case "After-Hours", "After Hours" -> "After-Hours";
            case "Closed", "Market Closed" -> "Market Closed";
            default -> status == null || status.isBlank() ? "Unknown" : status;
        };
    }

    private String resolveSessionLabel(String status) {
        return switch (status) {
            case "Pre-Market" -> "盘前交易中";
            case "Market Open" -> "常规交易中";
            case "After-Hours" -> "盘后交易中";
            case "Market Closed" -> "已收盘";
            default -> "最近行情";
        };
    }

    private String resolveSourceLabel(String status) {
        return switch (status) {
            case "Pre-Market" -> "Nasdaq ETF盘前行情";
            case "Market Open" -> "Nasdaq ETF实时行情";
            case "After-Hours" -> "Nasdaq ETF盘后行情";
            default -> "Nasdaq ETF最近行情";
        };
    }

    private record IndexProxy(String indexCode, String indexName, String proxySymbol, String proxyName) {
    }

    private record IndexResult(String sessionStatus, UsPremarketResponse.IndexQuote quote) {
    }

    private record CachedPremarket(UsPremarketResponse response, long cachedAt) {
    }
}
