package com.example.finance.service;

import com.example.finance.dto.MarketNewsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketNewsService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter NEWS_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long CACHE_MILLIS = 60_000L;
    private static final int CONNECT_TIMEOUT_MILLIS = 1500;
    private static final int READ_TIMEOUT_MILLIS = 2500;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 6;
    private static final int MAX_LIMIT = 40;

    private static final Map<String, CategorySpec> CATEGORY_MAP = new LinkedHashMap<>();

    static {
        registerCategory(new CategorySpec("all", "7x24", "102"));
        registerCategory(new CategorySpec("focus", "焦点", "101"));
        registerCategory(new CategorySpec("china", "中国", "110"));
        registerCategory(new CategorySpec("stock", "股市", "105"));
        registerCategory(new CategorySpec("commodity", "商品", "106"));
        registerCategory(new CategorySpec("fund", "基金", "109"));
        registerCategory(new CategorySpec("macro", "宏观", "118,119,120,121,122,123,124,125,126,127,128,129,130,131"));
    }

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String eastMoneyFastNewsApiUrl;
    private final Map<String, CachedMarketNews> cache = new ConcurrentHashMap<>();

    public MarketNewsService(
        ObjectMapper objectMapper,
        @Value("${finance.market-news.eastmoney-fast-news-api-url:https://np-weblist.eastmoney.com/comm/web/getFastNewsList}") String eastMoneyFastNewsApiUrl
    ) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .build();
        this.eastMoneyFastNewsApiUrl = eastMoneyFastNewsApiUrl;
    }

    public MarketNewsResponse getMarketNews(String category, Integer limit) {
        CategorySpec spec = resolveCategory(category);
        int pageSize = normalizeLimit(limit);
        String cacheKey = spec.key() + ":" + pageSize;
        long now = System.currentTimeMillis();
        CachedMarketNews cached = cache.get(cacheKey);
        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return cached.response();
        }

        try {
            MarketNewsResponse response = fetchMarketNews(spec, pageSize);
            cache.put(cacheKey, new CachedMarketNews(response, now));
            return response;
        } catch (Exception ex) {
            if (cached != null) {
                return cached.response();
            }
            throw new IllegalStateException("市场快讯加载失败", ex);
        }
    }

    private MarketNewsResponse fetchMarketNews(CategorySpec spec, int pageSize) throws Exception {
        JsonNode root = fetchJson(buildRequestUrl(spec, pageSize));
        if (!"1".equals(root.path("code").asText())) {
            throw new IllegalStateException("快讯源返回异常");
        }

        JsonNode listNode = root.path("data").path("fastNewsList");
        if (!listNode.isArray()) {
            throw new IllegalStateException("快讯数据格式错误");
        }

        List<MarketNewsResponse.MarketNewsItem> items = new ArrayList<>();
        for (JsonNode itemNode : listNode) {
            items.add(toNewsItem(itemNode));
        }

        MarketNewsResponse response = new MarketNewsResponse();
        response.setCategoryKey(spec.key());
        response.setCategoryLabel(spec.label());
        response.setCount(items.size());
        response.setUpdatedAt(resolveUpdatedAt(items));
        response.setSource("东方财富全球财经快讯");
        response.setItems(items);
        return response;
    }

    private String buildRequestUrl(CategorySpec spec, int pageSize) {
        return eastMoneyFastNewsApiUrl
            + "?client=web"
            + "&biz=web_724"
            + "&fastColumn=" + spec.fastColumn()
            + "&sortEnd="
            + "&pageSize=" + pageSize
            + "&req_trace=" + System.currentTimeMillis();
    }

    private JsonNode fetchJson(String url) throws Exception {
        String body = restClient.get()
            .uri(url)
            .header("Referer", "https://kuaixun.eastmoney.com/")
            .header("User-Agent", "Mozilla/5.0")
            .retrieve()
            .body(String.class);
        return objectMapper.readTree(body);
    }

    private MarketNewsResponse.MarketNewsItem toNewsItem(JsonNode itemNode) {
        MarketNewsResponse.MarketNewsItem item = new MarketNewsResponse.MarketNewsItem();
        String code = textValue(itemNode, "code");
        item.setCode(code);
        item.setTitle(textValue(itemNode, "title"));
        item.setSummary(textValue(itemNode, "summary"));
        item.setUrl(code.isBlank() ? "" : "https://finance.eastmoney.com/a/" + code + ".html");
        item.setPublishedAt(parseDateTime(textValue(itemNode, "showTime")));
        item.setCommentCount(itemNode.path("pinglun_Num").asInt(0));
        item.setShareCount(itemNode.path("share").asInt(0));
        item.setRelatedStockCount(itemNode.path("stockList").isArray() ? itemNode.path("stockList").size() : 0);
        item.setHighlight(itemNode.path("titleColor").asInt(0) > 0);
        if (item.getSummary().isBlank()) {
            item.setSummary(item.getTitle());
        }
        return item;
    }

    private LocalDateTime resolveUpdatedAt(List<MarketNewsResponse.MarketNewsItem> items) {
        for (MarketNewsResponse.MarketNewsItem item : items) {
            if (item.getPublishedAt() != null) {
                return item.getPublishedAt();
            }
        }
        return LocalDateTime.now(DEFAULT_ZONE);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), NEWS_TIME_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        return valueNode.asText("").trim();
    }

    private CategorySpec resolveCategory(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_MAP.get("all");
        }
        return CATEGORY_MAP.getOrDefault(category.trim().toLowerCase(Locale.ROOT), CATEGORY_MAP.get("all"));
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < MIN_LIMIT) {
            return MIN_LIMIT;
        }
        if (value > MAX_LIMIT) {
            return MAX_LIMIT;
        }
        return value;
    }

    private static void registerCategory(CategorySpec categorySpec) {
        CATEGORY_MAP.put(categorySpec.key(), categorySpec);
    }

    private record CategorySpec(String key, String label, String fastColumn) {
    }

    private record CachedMarketNews(MarketNewsResponse response, long cachedAt) {
    }
}
