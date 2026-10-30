package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 基金/报价（行情中心 market-service）客户端：投资模块的外部行情取数 2026-10-30
 * 整体收编到 market-service /api/internal/quote/** 后的服务间通道。
 *
 * <p>与 {@link MarketGoldClient} / {@link MarketClient} 同一套 X-Internal-Key 鉴权约定。
 * 上游数据源已去东财化（新浪基金频道 fu_/JJGK/JJFL + 新浪净值开放接口 + 腾讯快照/搜索
 * + 新浪股票分红），仅基金分红历史保留东财独家数据。字段口径见各方法注释。</p>
 */
@Component
public class MarketQuoteClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String internalApiKey;

    public MarketQuoteClient(
        ObjectMapper objectMapper,
        @Value("${market.center.base-url:http://localhost:8083}") String baseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(20_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    /**
     * 基金基础信息，返回含 Datas 节点的对象：
     * Datas.{SHORTNAME,FCODE,DWJZ,FSRQ,RZDF,LJJZ,SGZT,SHZT,FTYPE,JJGS}（与原东财字段名一致）。
     */
    public JsonNode fundBase(String code) {
        return fetchNode("/api/internal/quote/fund/base?code=" + encode(code));
    }

    /**
     * 基金盘中估值：{fundcode,name,jzrq,dwjz,gsz,gztime}（与原东财字段名一致）。
     */
    public JsonNode fundEstimate(String code) {
        return fetchNode("/api/internal/quote/fund/estimate?code=" + encode(code));
    }

    /** 基金费率：{matched, redeemFeeRules:[{label,feeRateText}], managementFeeRate?, custodyFeeRate?} */
    public JsonNode fundFees(String code) {
        return fetchNode("/api/internal/quote/fund/fees?code=" + encode(code));
    }

    /** 基金净值历史（倒序）：{matched, items:[{date,nav,accNav}]}，近4年日频 */
    public JsonNode fundNavHistory(String code) {
        return fetchNode("/api/internal/quote/fund/nav-history?code=" + encode(code));
    }

    /** 基金分红历史（东财独家源）：{matched, items:[{dividendYear,recordDate,exDividendDate,dividendPerUnit,payDate}]} */
    public JsonNode fundDividends(String code) {
        return fetchNode("/api/internal/quote/fund/dividends?code=" + encode(code));
    }

    /** 产品搜索（腾讯 smartbox）：{items:[{code,name,type,exchange}]} */
    public JsonNode searchProducts(String keyword) {
        return fetchNode("/api/internal/quote/search?keyword=" + encode(keyword));
    }

    /** A股快照：{matched, code, name, price, exchangeCode, quoteTime} */
    public JsonNode stockSnapshot(String code) {
        return fetchNode("/api/internal/quote/stock/snapshot?code=" + encode(code));
    }

    /** 股票分红历史（新浪，每10股口径）：{matched, items:[{announcementDate,bonusPer10,conversionPer10,pretaxPer10,progress,exDividendDate,recordDate,listingDate}]} */
    public JsonNode stockDividends(String code) {
        return fetchNode("/api/internal/quote/stock/dividends?code=" + encode(code));
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private JsonNode fetchNode(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey);
        ResponseEntity<String> response;
        try {
            // path 中参数已自行 URL 编码，必须传 URI 对象，避免 RestTemplate 当作 URI 模板二次编码
            // （中文关键词会被双重编码成 %25E7... 导致搜索恒为空）
            response = restTemplate.exchange(java.net.URI.create(baseUrl + path), HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            throw new IllegalStateException("行情中心报价接口不可用: " + e.getMessage(), e);
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.path("code").asInt(-1) != 0) {
                throw new IllegalStateException("行情中心报价接口返回错误: " + root.path("message").asText());
            }
            return root.path("data");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("行情中心报价响应解析失败", e);
        }
    }
}
