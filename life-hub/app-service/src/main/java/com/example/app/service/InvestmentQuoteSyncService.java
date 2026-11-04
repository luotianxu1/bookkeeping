package com.example.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.client.MarketQuoteClient;
import com.example.app.entity.InvestmentPositionEntity;
import com.example.app.entity.InvestmentPriceQuoteEntity;
import com.example.app.entity.InvestmentProductEntity;
import com.example.app.mapper.InvestmentPriceQuoteMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;

import static com.example.app.service.InvestmentCalcUtils.*;

/**
 * 投资行情抓取域服务：基金净值/股票快照抓取落库、结算报价查询、行情中心网关。
 * 自 InvestmentService 拆出；快照 record 为同包顶层类型，主服务零前缀引用。
 */
@Service
public class InvestmentQuoteSyncService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentQuoteSyncService.class);

    // 与 InvestmentService 中同名常量保持一致（行情域专用副本）
    private static final String FUND_QUOTE_SOURCE = "EASTMONEY_FUND_DAILY";
    private static final String STOCK_QUOTE_SOURCE = "TENCENT_STOCK_LATEST";
    private static final LocalTime FUND_CONFIRM_READY_TIME = LocalTime.of(9, 5);

    private final InvestmentPriceQuoteMapper priceQuoteMapper;
    private final MarketQuoteClient marketQuoteClient;
    private final ObjectMapper objectMapper;

    public InvestmentQuoteSyncService(
        InvestmentPriceQuoteMapper priceQuoteMapper,
        MarketQuoteClient marketQuoteClient,
        ObjectMapper objectMapper
    ) {
        this.priceQuoteMapper = priceQuoteMapper;
        this.marketQuoteClient = marketQuoteClient;
        this.objectMapper = objectMapper;
    }

    record FundQuoteSnapshot(
        LocalDate quoteDate,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
    }

    record StockQuoteSnapshot(
        LocalDate quoteDate,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
    }

    // ---------- static 工具 ----------

        public static boolean isFundConfirmationReady(LocalDate expectedConfirmDate, LocalDateTime settlementDateTime) {
        if (expectedConfirmDate == null) {
            return false;
        }
        LocalDateTime effectiveSettlementDateTime = settlementDateTime == null ? LocalDateTime.now() : settlementDateTime;
        return !effectiveSettlementDateTime.isBefore(expectedConfirmDate.atTime(FUND_CONFIRM_READY_TIME));
    }

        public static String toTencentSymbol(String code, String exchangeCode) {
        if ("SSE".equals(exchangeCode)) {
            return "sh" + code;
        }
        if ("SZSE".equals(exchangeCode)) {
            return "sz" + code;
        }
        return code.startsWith("6") ? "sh" + code : "sz" + code;
    }

    // ---------- 抓取落库 ----------

        public FundQuoteSnapshot fetchAndSaveLatestFundQuote(InvestmentProductEntity product,
        Map<Long, FundQuoteSnapshot> sharedFundQuotes) {
        if (sharedFundQuotes != null) {
            FundQuoteSnapshot cached = sharedFundQuotes.get(product.getId());
            if (cached != null) {
                return cached;
            }
        }

        JsonNode baseInfo = fetchFundBaseInfo(product.getSymbol()).path("Datas");
        BigDecimal latestPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        LocalDate quoteDate = safeDate(baseInfo.path("FSRQ").asText(null));
        if (latestPrice == null || quoteDate == null) {
            log.warn("基金净值同步跳过：symbol={} 未返回有效净值或净值日期", product.getSymbol());
            return null;
        }

        InvestmentPriceQuoteEntity existingQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .eq(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .last("LIMIT 1"));
        InvestmentPriceQuoteEntity previousQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .lt(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .orderByDesc(InvestmentPriceQuoteEntity::getQuoteDate)
            .last("LIMIT 1"));

        BigDecimal changeRate = safeDecimal(baseInfo.path("RZDF").asText(null));
        BigDecimal preClosePrice = resolveFundPreClose(existingQuote, previousQuote, latestPrice, changeRate);
        BigDecimal changeAmount = preClosePrice == null
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : latestPrice.subtract(preClosePrice).setScale(6, RoundingMode.HALF_UP);
        BigDecimal normalizedChangeRate = changeRate != null
            ? changeRate.setScale(4, RoundingMode.HALF_UP)
            : rate(changeAmount, preClosePrice);
        LocalDateTime syncedAt = LocalDateTime.now();

        saveFundQuote(product.getId(), existingQuote, quoteDate, syncedAt, latestPrice, preClosePrice, changeAmount, normalizedChangeRate);
        FundQuoteSnapshot snapshot = new FundQuoteSnapshot(quoteDate, latestPrice, preClosePrice, syncedAt);
        if (sharedFundQuotes != null) {
            sharedFundQuotes.put(product.getId(), snapshot);
        }
        return snapshot;
    }

        public StockQuoteSnapshot fetchAndSaveLatestStockQuote(InvestmentProductEntity product) {
        String symbol = toTencentSymbol(product.getSymbol(), product.getExchangeCode());
        JsonNode quote = fetchTencentQuoteFields(symbol);
        BigDecimal latestPrice = safeDecimal(quote.path("price").asText(null));
        BigDecimal preClosePrice = safeDecimal(quote.path("prevClose").asText(null));
        if (latestPrice == null || latestPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("股票行情同步跳过：symbol={} 未返回有效最新价", product.getSymbol());
            return null;
        }

        LocalDate quoteDate = resolveStockQuoteDate(quote.path("timeRaw").asText(null));
        if (quoteDate == null) {
            quoteDate = LocalDate.now();
        }
        LocalDateTime syncedAt = LocalDateTime.now();
        BigDecimal normalizedPreClosePrice = preClosePrice != null && preClosePrice.compareTo(BigDecimal.ZERO) > 0
            ? preClosePrice.setScale(6, RoundingMode.HALF_UP)
            : null;
        BigDecimal changeAmount = normalizedPreClosePrice == null
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : latestPrice.subtract(normalizedPreClosePrice).setScale(6, RoundingMode.HALF_UP);
        BigDecimal changeRate = rate(changeAmount, normalizedPreClosePrice);

        InvestmentPriceQuoteEntity existingQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .eq(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .last("LIMIT 1"));

        saveStockQuote(
            product.getId(),
            existingQuote,
            quoteDate,
            syncedAt,
            safeDecimal(quote.path("open").asText(null)),
            safeDecimal(quote.path("high").asText(null)),
            safeDecimal(quote.path("low").asText(null)),
            latestPrice,
            normalizedPreClosePrice,
            changeAmount,
            changeRate
        );
        return new StockQuoteSnapshot(quoteDate, latestPrice, normalizedPreClosePrice, syncedAt);
    }

        private void saveFundQuote(
        Long productId,
        InvestmentPriceQuoteEntity existingQuote,
        LocalDate quoteDate,
        LocalDateTime syncedAt,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        BigDecimal changeAmount,
        BigDecimal changeRate
    ) {
        InvestmentPriceQuoteEntity quote = existingQuote == null ? new InvestmentPriceQuoteEntity() : existingQuote;
        quote.setProductId(productId);
        quote.setQuoteDate(quoteDate);
        quote.setQuoteTime(syncedAt);
        quote.setClosePrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        quote.setLatestPrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        quote.setPreClosePrice(preClosePrice == null ? null : preClosePrice.setScale(6, RoundingMode.HALF_UP));
        quote.setChangeAmount(changeAmount.setScale(6, RoundingMode.HALF_UP));
        quote.setChangeRate(changeRate.setScale(4, RoundingMode.HALF_UP));
        quote.setSource(FUND_QUOTE_SOURCE);
        if (existingQuote == null) {
            priceQuoteMapper.insert(quote);
        } else {
            priceQuoteMapper.updateById(quote);
        }
    }

        private void saveStockQuote(
        Long productId,
        InvestmentPriceQuoteEntity existingQuote,
        LocalDate quoteDate,
        LocalDateTime syncedAt,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        BigDecimal changeAmount,
        BigDecimal changeRate
    ) {
        InvestmentPriceQuoteEntity quote = existingQuote == null ? new InvestmentPriceQuoteEntity() : existingQuote;
        quote.setProductId(productId);
        quote.setQuoteDate(quoteDate);
        quote.setQuoteTime(syncedAt);
        quote.setOpenPrice(scaleStockQuoteValue(openPrice));
        quote.setHighPrice(scaleStockQuoteValue(highPrice));
        quote.setLowPrice(scaleStockQuoteValue(lowPrice));
        quote.setClosePrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        quote.setLatestPrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        quote.setPreClosePrice(preClosePrice == null ? null : preClosePrice.setScale(6, RoundingMode.HALF_UP));
        quote.setChangeAmount(changeAmount.setScale(6, RoundingMode.HALF_UP));
        quote.setChangeRate(changeRate.setScale(4, RoundingMode.HALF_UP));
        quote.setSource(STOCK_QUOTE_SOURCE);
        if (existingQuote == null) {
            priceQuoteMapper.insert(quote);
        } else {
            priceQuoteMapper.updateById(quote);
        }
    }

    // ---------- 行情应用与结算报价 ----------

        public void applyFundQuoteToPosition(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal marketValue = holdingQuantity.multiply(latestPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousMarketValue = preClosePrice == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : holdingQuantity.multiply(preClosePrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dayProfit = preClosePrice == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : marketValue.subtract(previousMarketValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);

        position.setCurrentPrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        position.setMarketValue(marketValue);
        position.setDayProfit(dayProfit);
        position.setDayProfitRate(rate(dayProfit, previousMarketValue));
        position.setHoldingProfit(holdingProfit);
        position.setHoldingProfitRate(rate(holdingProfit, costAmount));
        position.setLastSyncedAt(syncedAt);
    }

        public void applyStockQuoteToPosition(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        BigDecimal preClosePrice,
        LocalDateTime syncedAt
    ) {
        BigDecimal holdingQuantity = defaultZero(position.getHoldingQuantity()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal costAmount = defaultZero(position.getCostAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal marketValue = holdingQuantity.multiply(latestPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousMarketValue = preClosePrice == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : holdingQuantity.multiply(preClosePrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dayProfit = preClosePrice == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : marketValue.subtract(previousMarketValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal holdingProfit = marketValue.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);

        position.setCurrentPrice(latestPrice.setScale(6, RoundingMode.HALF_UP));
        position.setMarketValue(marketValue);
        position.setDayProfit(dayProfit);
        position.setDayProfitRate(rate(dayProfit, previousMarketValue));
        position.setHoldingProfit(holdingProfit);
        position.setHoldingProfitRate(rate(holdingProfit, costAmount));
        position.setLastSyncedAt(syncedAt);
    }

        public void updatePendingFundPositionSnapshot(
        InvestmentPositionEntity position,
        BigDecimal latestPrice,
        LocalDateTime syncedAt
    ) {
        position.setCurrentPrice(defaultZero(latestPrice).setScale(6, RoundingMode.HALF_UP));
        position.setDayProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setDayProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        position.setHoldingProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setHoldingProfitRate(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        position.setMarketValue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        position.setLastSyncedAt(syncedAt);
    }

        public InvestmentPriceQuoteEntity findSettlementQuoteByProductAndDate(Long productId, LocalDate quoteDate) {
        if (productId == null || quoteDate == null) {
            return null;
        }
        return priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, productId)
            .ge(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .orderByAsc(InvestmentPriceQuoteEntity::getQuoteDate)
            .last("LIMIT 1"));
    }

        public BigDecimal resolveConfirmedFundPrice(InvestmentPriceQuoteEntity appliedQuote) {
        return defaultZero(
            appliedQuote == null
                ? null
                : (appliedQuote.getClosePrice() != null ? appliedQuote.getClosePrice() : appliedQuote.getLatestPrice())
        ).setScale(6, RoundingMode.HALF_UP);
    }

        private BigDecimal resolveFundPreClose(
        InvestmentPriceQuoteEntity existingQuote,
        InvestmentPriceQuoteEntity previousQuote,
        BigDecimal latestPrice,
        BigDecimal changeRate
    ) {
        if (existingQuote != null && existingQuote.getPreClosePrice() != null) {
            return existingQuote.getPreClosePrice().setScale(6, RoundingMode.HALF_UP);
        }
        if (previousQuote != null) {
            BigDecimal previousPrice = previousQuote.getClosePrice() != null
                ? previousQuote.getClosePrice()
                : previousQuote.getLatestPrice();
            if (previousPrice != null) {
                return previousPrice.setScale(6, RoundingMode.HALF_UP);
            }
        }
        if (changeRate != null && latestPrice != null && changeRate.compareTo(BigDecimal.valueOf(-100)) > 0) {
            BigDecimal divisor = BigDecimal.ONE.add(changeRate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
            if (divisor.compareTo(BigDecimal.ZERO) != 0) {
                return latestPrice.divide(divisor, 6, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

        public InvestmentPriceQuoteEntity resolveImmediateSettlementQuote(
        InvestmentProductEntity product,
        JsonNode baseInfo,
        LocalDate appliedDate,
        LocalDate expectedConfirmDate,
        LocalDateTime syncedAt
    ) {
        upsertFundQuoteFromBaseInfo(product, baseInfo, syncedAt);
        LocalDate effectiveExpectedConfirmDate = expectedConfirmDate == null ? appliedDate : expectedConfirmDate;
        if (!isFundConfirmationReady(effectiveExpectedConfirmDate, syncedAt)) {
            return null;
        }
        return findSettlementQuoteByProductAndDate(product.getId(), appliedDate);
    }

        private void upsertFundQuoteFromBaseInfo(InvestmentProductEntity product, JsonNode baseInfo, LocalDateTime syncedAt) {
        if (product == null || baseInfo == null) {
            return;
        }
        BigDecimal latestPrice = safeDecimal(baseInfo.path("DWJZ").asText(null));
        LocalDate quoteDate = safeDate(baseInfo.path("FSRQ").asText(null));
        if (latestPrice == null || quoteDate == null) {
            return;
        }

        InvestmentPriceQuoteEntity existingQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .eq(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .last("LIMIT 1"));
        InvestmentPriceQuoteEntity previousQuote = priceQuoteMapper.selectOne(new LambdaQueryWrapper<InvestmentPriceQuoteEntity>()
            .eq(InvestmentPriceQuoteEntity::getProductId, product.getId())
            .lt(InvestmentPriceQuoteEntity::getQuoteDate, quoteDate)
            .orderByDesc(InvestmentPriceQuoteEntity::getQuoteDate)
            .last("LIMIT 1"));

        BigDecimal changeRate = safeDecimal(baseInfo.path("RZDF").asText(null));
        BigDecimal preClosePrice = resolveFundPreClose(existingQuote, previousQuote, latestPrice, changeRate);
        BigDecimal changeAmount = preClosePrice == null
            ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
            : latestPrice.subtract(preClosePrice).setScale(6, RoundingMode.HALF_UP);
        BigDecimal normalizedChangeRate = changeRate != null
            ? changeRate.setScale(4, RoundingMode.HALF_UP)
            : rate(changeAmount, preClosePrice);

        saveFundQuote(product.getId(), existingQuote, quoteDate, syncedAt, latestPrice, preClosePrice, changeAmount, normalizedChangeRate);
    }

    // ---------- 行情中心网关 ----------

        public JsonNode fetchFundBaseInfo(String code) {
        try {
            return marketQuoteClient.fundBase(code);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

        public JsonNode fetchFundEstimateInfo(String code) {
        try {
            return marketQuoteClient.fundEstimate(code);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

        public JsonNode fetchTencentQuoteFields(String symbol) {
        // 腾讯快照经 market-service /api/internal/quote/stock/snapshot 获取（ symbol 可能带 sh/sz 前缀，需剥离）
        String bareCode = symbol.replaceFirst("^(?:sh|sz|bj)", "");
        try {
            JsonNode snapshot = marketQuoteClient.stockSnapshot(bareCode);
            if (snapshot == null || !snapshot.path("matched").asBoolean(false)) {
                return objectMapper.createObjectNode();
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("name", snapshot.path("name").asText(null));
            node.put("code", snapshot.path("code").asText(null));
            node.put("price", snapshot.path("price").asText(null));
            node.put("prevClose", snapshot.path("prevClose").asText(null));
            node.put("open", snapshot.path("open").asText(null));
            node.put("volume", snapshot.path("volume").asText(null));
            node.put("timeRaw", snapshot.path("timeRaw").asText(null));
            node.put("change", snapshot.path("change").asText(null));
            node.put("changePercent", snapshot.path("changePercent").asText(null));
            node.put("high", snapshot.path("high").asText(null));
            node.put("low", snapshot.path("low").asText(null));
            node.put("turnoverRate", snapshot.path("turnoverRate").asText(null));
            node.put("pe", snapshot.path("pe").asText(null));
            return node;
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }
}
