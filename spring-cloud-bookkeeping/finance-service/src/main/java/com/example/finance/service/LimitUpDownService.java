package com.example.finance.service;

import com.example.finance.dto.LimitUpDownResponse;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LimitUpDownService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 30_000L;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final int DISPLAY_STOCK_COUNT = 6;
    private static final BigDecimal EXTREME_CHANGE_THRESHOLD = new BigDecimal("4.5");

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String stockListApiUrl;
    private final AtomicReference<CachedStatistics> cache = new AtomicReference<>();

    public LimitUpDownService(
        ObjectMapper objectMapper,
        @Value("${finance.limit-up-down.stock-list-api-url:https://push2delay.eastmoney.com/api/qt/clist/get}") String stockListApiUrl
    ) {
        this.objectMapper = objectMapper;
        this.stockListApiUrl = stockListApiUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2500);
        requestFactory.setReadTimeout(4500);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public LimitUpDownResponse getLimitUpDown() {
        long now = System.currentTimeMillis();
        CachedStatistics cached = cache.get();
        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return cached.response();
        }
        try {
            LimitUpDownResponse response = fetchLimitUpDown();
            cache.set(new CachedStatistics(response, now));
            return response;
        } catch (Exception ex) {
            if (cached != null) {
                return cached.response();
            }
            throw new IllegalStateException("涨跌停数据加载失败", ex);
        }
    }

    private LimitUpDownResponse fetchLimitUpDown() throws Exception {
        List<StockQuote> gainers = fetchExtremeQuotes(true);
        List<StockQuote> decliners = fetchExtremeQuotes(false);
        List<LimitUpDownResponse.StockItem> limitUps = new ArrayList<>();
        List<LimitUpDownResponse.StockItem> limitDowns = new ArrayList<>();
        int limitUpCount = 0;
        int limitDownCount = 0;
        int brokenLimitCount = 0;

        for (StockQuote quote : gainers) {
            if (isNoLimitStock(quote) || quote.previousClose() == null || quote.price() == null) {
                continue;
            }
            if (matchesLimitPrice(quote, quote.price(), true)) {
                limitUpCount++;
                addDisplayItem(limitUps, quote);
            } else if (matchesLimitPrice(quote, quote.high(), true)) {
                brokenLimitCount++;
            }
        }

        for (StockQuote quote : decliners) {
            if (isNoLimitStock(quote) || quote.previousClose() == null || quote.price() == null) {
                continue;
            }
            if (matchesLimitPrice(quote, quote.price(), false)) {
                limitDownCount++;
                addDisplayItem(limitDowns, quote);
            }
        }

        int attemptedLimitUps = limitUpCount + brokenLimitCount;
        BigDecimal sealRate = attemptedLimitUps == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf((double) limitUpCount * 100D / attemptedLimitUps)
                .setScale(1, RoundingMode.HALF_UP);

        LimitUpDownResponse response = new LimitUpDownResponse();
        response.setLimitUpCount(limitUpCount);
        response.setLimitDownCount(limitDownCount);
        response.setBrokenLimitCount(brokenLimitCount);
        response.setSealRate(sealRate);
        response.setUpdatedAt(LocalDateTime.now(MARKET_ZONE));
        response.setSource("东方财富公开行情");
        response.setLimitUps(limitUps);
        response.setLimitDowns(limitDowns);
        return response;
    }

    private List<StockQuote> fetchExtremeQuotes(boolean descending) throws Exception {
        List<StockQuote> quotes = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String url = stockListApiUrl
                + "?pn=" + page
                + "&pz=" + PAGE_SIZE
                + "&po=" + (descending ? 1 : 0)
                + "&np=1&fltt=2&invt=2&fid=f3"
                + "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048"
                + "&fields=f12,f14,f2,f3,f15,f16,f18,f100";
            String body = restClient.get()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://quote.eastmoney.com/")
                .retrieve()
                .body(String.class);
            JsonNode rows = objectMapper.readTree(body).path("data").path("diff");
            if (!rows.isArray() || rows.isEmpty()) {
                if (page == 1) {
                    throw new IllegalStateException("全市场行情格式异常");
                }
                break;
            }

            BigDecimal lastChangePercent = null;
            for (JsonNode row : rows) {
                BigDecimal changePercent = decimal(row.path("f3"));
                if (changePercent == null) {
                    continue;
                }
                lastChangePercent = changePercent;
                boolean isExtreme = descending
                    ? changePercent.compareTo(EXTREME_CHANGE_THRESHOLD) >= 0
                    : changePercent.compareTo(EXTREME_CHANGE_THRESHOLD.negate()) <= 0;
                if (isExtreme) {
                    quotes.add(toQuote(row, changePercent));
                }
            }

            if (rows.size() < PAGE_SIZE || lastChangePercent == null) {
                break;
            }
            boolean passedCandidateRange = descending
                ? lastChangePercent.compareTo(EXTREME_CHANGE_THRESHOLD) < 0
                : lastChangePercent.compareTo(EXTREME_CHANGE_THRESHOLD.negate()) > 0;
            if (passedCandidateRange) {
                break;
            }
        }
        return quotes;
    }

    private StockQuote toQuote(JsonNode row, BigDecimal changePercent) {
        return new StockQuote(
            row.path("f12").asText(""),
            row.path("f14").asText(""),
            row.path("f100").asText(""),
            decimal(row.path("f2")),
            decimal(row.path("f15")),
            decimal(row.path("f18")),
            changePercent
        );
    }

    private boolean matchesLimitPrice(StockQuote quote, BigDecimal price, boolean upper) {
        if (price == null) {
            return false;
        }
        return resolveLimitRates(quote).stream().anyMatch(rate -> {
            BigDecimal multiplier = upper ? BigDecimal.ONE.add(rate) : BigDecimal.ONE.subtract(rate);
            BigDecimal limitPrice = quote.previousClose()
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);
            return price.compareTo(limitPrice) == 0;
        });
    }

    private List<BigDecimal> resolveLimitRates(StockQuote quote) {
        String code = quote.code();
        if (code.startsWith("4") || code.startsWith("8") || code.startsWith("92")) {
            return List.of(new BigDecimal("0.30"));
        }
        if (code.startsWith("30") || code.startsWith("688") || code.startsWith("689")) {
            return List.of(new BigDecimal("0.20"));
        }
        if (quote.name().toUpperCase(Locale.ROOT).contains("ST")) {
            return List.of(new BigDecimal("0.05"), new BigDecimal("0.10"));
        }
        return List.of(new BigDecimal("0.10"));
    }

    private boolean isNoLimitStock(StockQuote quote) {
        String name = quote.name().toUpperCase(Locale.ROOT);
        return name.startsWith("N") || name.startsWith("C");
    }

    private void addDisplayItem(List<LimitUpDownResponse.StockItem> items, StockQuote quote) {
        if (items.size() >= DISPLAY_STOCK_COUNT) {
            return;
        }
        LimitUpDownResponse.StockItem item = new LimitUpDownResponse.StockItem();
        item.setCode(quote.code());
        item.setName(quote.name());
        item.setChangePercent(quote.changePercent());
        item.setIndustry(quote.industry());
        items.add(item);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        if (value.isEmpty() || "-".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record StockQuote(
        String code,
        String name,
        String industry,
        BigDecimal price,
        BigDecimal high,
        BigDecimal previousClose,
        BigDecimal changePercent
    ) {
    }

    private record CachedStatistics(LimitUpDownResponse response, long cachedAt) {
    }
}
