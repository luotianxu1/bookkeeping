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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UsPremarketService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final long CACHE_MILLIS = 30_000L;
    private static final DateTimeFormatter NASDAQ_DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("MMM d, uuuu hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter NASDAQ_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH);
    private static final Pattern EXTENDED_QUOTE_PATTERN = Pattern.compile(
        "^\\$?([\\d,]+(?:\\.\\d+)?)\\s+([+-]?[\\d,]+(?:\\.\\d+)?)\\s+\\(([+-]?[\\d,]+(?:\\.\\d+)?)%\\)$"
    );
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
        String currentSessionStatus = results.stream()
            .map(IndexResult::currentSessionStatus)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("Unknown");
        UsPremarketResponse response = new UsPremarketResponse();
        response.setSessionStatus(sessionStatus);
        response.setSessionLabel(resolveSessionLabel(sessionStatus, currentSessionStatus));
        response.setUpdatedAt(results.stream()
            .map(IndexResult::updatedAt)
            .filter(value -> value != null)
            .max(LocalDateTime::compareTo)
            .orElseGet(() -> LocalDateTime.now(MARKET_ZONE)));
        response.setSource(resolveSourceLabel(sessionStatus));
        response.setIndices(results.stream().map(IndexResult::quote).toList());
        return response;
    }

    private IndexResult fetchIndexQuote(IndexProxy proxy) {
        String url = quoteInfoApiUrl + "/" + proxy.proxySymbol() + "/info?assetclass=etf";
        JsonNode data = requestJson(url).path("data");
        JsonNode primary = data.path("primaryData");
        if (primary.isMissingNode() || primary.isNull()) {
            return null;
        }
        String currentSessionStatus = normalizeSessionStatus(data.path("marketStatus").asText("Unknown"));
        ExtendedQuote extendedQuote = selectExtendedQuote(proxy.proxySymbol(), currentSessionStatus);
        JsonNode activeQuote = extendedQuote.quote();
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
        return new IndexResult(
            extendedQuote.sessionStatus(),
            currentSessionStatus,
            quote,
            extendedQuote.updatedAt()
        );
    }

    private ExtendedQuote selectExtendedQuote(String symbol, String currentSessionStatus) {
        if ("Pre-Market".equals(currentSessionStatus) || "After-Hours".equals(currentSessionStatus)) {
            return fetchExtendedQuote(symbol, currentSessionStatus);
        }

        ExtendedQuote premarket = tryFetchExtendedQuote(symbol, "Pre-Market");
        ExtendedQuote afterHours = tryFetchExtendedQuote(symbol, "After-Hours");
        if (premarket == null && afterHours == null) {
            throw new IllegalStateException("Nasdaq盘前盘后行情暂无可用数据");
        }
        if (premarket == null) {
            return afterHours;
        }
        if (afterHours == null) {
            return premarket;
        }
        return afterHours.updatedAt().isAfter(premarket.updatedAt()) ? afterHours : premarket;
    }

    private ExtendedQuote tryFetchExtendedQuote(String symbol, String sessionStatus) {
        try {
            return fetchExtendedQuote(symbol, sessionStatus);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ExtendedQuote fetchExtendedQuote(String symbol, String sessionStatus) {
        String marketType = "Pre-Market".equals(sessionStatus) ? "pre" : "post";
        String url = quoteInfoApiUrl + "/" + symbol
            + "/extended-trading?assetclass=etf&markettype=" + marketType;
        JsonNode data = requestJson(url).path("data");
        JsonNode summary = data.path("infoTable").path("rows").path(0);
        Matcher matcher = EXTENDED_QUOTE_PATTERN.matcher(summary.path("consolidated").asText("").trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("Nasdaq扩展时段行情格式异常");
        }

        var quote = objectMapper.createObjectNode();
        quote.put("lastSalePrice", matcher.group(1));
        quote.put("netChange", matcher.group(2));
        quote.put("percentageChange", matcher.group(3));
        quote.put("volume", summary.path("volume").asText(""));
        String lastTradeTime = data.path("lastUpdateInfo").path(0).asText("");
        quote.put("lastTradeTimestamp", lastTradeTime);
        LocalDateTime updatedAt = parseNasdaqTime(lastTradeTime);
        if (updatedAt == null) {
            throw new IllegalStateException("Nasdaq扩展时段行情时间格式异常");
        }
        return new ExtendedQuote(sessionStatus, quote, updatedAt);
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

    private LocalDateTime parseNasdaqTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
            .replaceFirst("^Data last updated\\s+", "")
            .replaceFirst("\\.?\\s+ET\\.?$", "")
            .replaceFirst("\\.$", "")
            .trim();
        try {
            LocalDateTime newYorkTime = LocalDateTime.parse(normalized, NASDAQ_DATE_TIME_FORMATTER);
            return newYorkTime.atZone(NEW_YORK_ZONE).withZoneSameInstant(MARKET_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate marketDate = LocalDate.parse(normalized, NASDAQ_DATE_FORMATTER);
                return marketDate.atTime(LocalTime.of(16, 0))
                    .atZone(NEW_YORK_ZONE)
                    .withZoneSameInstant(MARKET_ZONE)
                    .toLocalDateTime();
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
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

    private String resolveSessionLabel(String status, String currentStatus) {
        if (!status.equals(currentStatus)) {
            return switch (status) {
                case "Pre-Market" -> "最近盘前";
                case "After-Hours" -> "最近盘后";
                default -> "最近扩展行情";
            };
        }
        return switch (status) {
            case "Pre-Market" -> "盘前交易中";
            case "After-Hours" -> "盘后交易中";
            default -> "最近扩展行情";
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

    private record IndexResult(
        String sessionStatus,
        String currentSessionStatus,
        UsPremarketResponse.IndexQuote quote,
        LocalDateTime updatedAt
    ) {
    }

    private record ExtendedQuote(String sessionStatus, JsonNode quote, LocalDateTime updatedAt) {
    }

    private record CachedPremarket(UsPremarketResponse response, long cachedAt) {
    }
}
