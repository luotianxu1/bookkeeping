package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基金/报价（行情中心 market-service）客户端：投资模块的外部行情取数 2026-10-30
 * 整体收编到 market-service /api/internal/quote/** 后的服务间通道。
 *
 * <p>与 {@link MarketGoldClient} / {@link MarketClient} 同一套 X-Internal-Key 鉴权约定。
 * 上游数据源已去东财化（新浪基金频道 fu_/JJGK/JJFL + 新浪净值开放接口 + 腾讯快照/搜索
 * + 新浪股票分红），仅基金分红历史保留东财独家数据。字段口径见各方法注释。</p>
 */
@Component
public class MarketQuoteClient extends InternalMarketClientSupport {

    public MarketQuoteClient(
        ObjectMapper objectMapper,
        @Value("${market.center.base-url:http://localhost:8083}") String baseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        super(objectMapper, baseUrl, internalApiKey, "报价", 20_000);
    }

    /**
     * 基金基础信息，返回含 Datas 节点的对象：
     * Datas.{SHORTNAME,FCODE,DWJZ,FSRQ,RZDF,LJJZ,SGZT,SHZT,FTYPE,JJGS}（与原东财字段名一致）。
     */
    public JsonNode fundBase(String code) {
        return fetchNode(baseUrl + "/api/internal/quote/fund/base?code=" + encode(code));
    }

    /**
     * 基金盘中估值：{fundcode,name,jzrq,dwjz,gsz,gztime}（与原东财字段名一致）。
     */
    public JsonNode fundEstimate(String code) {
        return fetchNode(baseUrl + "/api/internal/quote/fund/estimate?code=" + encode(code));
    }

    /** 基金费率：{matched, redeemFeeRules:[{label,feeRateText}], managementFeeRate?, custodyFeeRate?} */
    public JsonNode fundFees(String code) {
        return fetchNode(baseUrl + "/api/internal/quote/fund/fees?code=" + encode(code));
    }

    /** 基金净值历史（倒序）：{matched, items:[{date,nav,accNav}]}，近4年日频 */
    public JsonNode fundNavHistory(String code) {
        return fetchNode(baseUrl + "/api/internal/quote/fund/nav-history?code=" + encode(code));
    }

    /** 基金分红历史（东财独家源）：{matched, items:[{dividendYear,recordDate,exDividendDate,dividendPerUnit,payDate}]} */
    public JsonNode fundDividends(String code) {
        return fetchNode(baseUrl + "/api/internal/quote/fund/dividends?code=" + encode(code));
    }

    /** 产品搜索（腾讯 smartbox）：{items:[{code,name,type,exchange}]} */
    public JsonNode searchProducts(String keyword) {
        return fetchNode(baseUrl + "/api/internal/quote/search?keyword=" + encode(keyword));
    }

    /** A股快照：{matched, code, name, price, exchangeCode, quoteTime} */
    public JsonNode stockSnapshot(String code) {
        return fetchNode(baseUrl + "/api/internal/quote/stock/snapshot?code=" + encode(code));
    }

    /** 股票分红历史（新浪，每10股口径）：{matched, items:[{announcementDate,bonusPer10,conversionPer10,pretaxPer10,progress,exDividendDate,recordDate,listingDate}]} */
    public JsonNode stockDividends(String code) {
        return fetchNode(baseUrl + "/api/internal/quote/stock/dividends?code=" + encode(code));
    }
}
