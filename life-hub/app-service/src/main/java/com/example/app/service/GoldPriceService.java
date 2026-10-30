package com.example.app.service;

import com.example.app.client.MarketGoldClient;
import com.example.app.dto.GoldPriceResponse;
import com.example.app.dto.GoldRealtimePriceResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 金价服务（2026-10-30 重构为行情中心适配层）。
 *
 * <p>金投网抓取/解析/缓存逻辑已整体迁出到 market-service（{@code com.example.market.gold}），
 * 本类仅保留迁移前的公开方法签名，内部经 {@link MarketGoldClient}（X-Internal-Key）
 * 调行情中心 /api/internal/gold/**，数据口径不变。金价页对外接口与定时任务、
 * 金账户/资产趋势等内部消费方无需感知迁移。</p>
 */
@Service
public class GoldPriceService {

    private final MarketGoldClient marketGoldClient;

    public GoldPriceService(MarketGoldClient marketGoldClient) {
        this.marketGoldClient = marketGoldClient;
    }

    public GoldPriceResponse getGoldPrice(String range) {
        return getGoldPrice(range, false, true);
    }

    public GoldPriceResponse getGoldPrice(String range, boolean forceRefreshCurrent) {
        return getGoldPrice(range, forceRefreshCurrent, true);
    }

    public GoldPriceResponse getGoldPrice(String range, boolean forceRefreshCurrent, boolean includeChart) {
        return marketGoldClient.getGoldPrice(range, forceRefreshCurrent, includeChart);
    }

    public GoldRealtimePriceResponse getRealtimePrice() {
        try {
            return marketGoldClient.getRealtimePrice(false);
        } catch (Exception e) {
            // 与迁移前语义一致：读缓存路径不抛受检异常；仅行情中心不可达时降级报错
            throw new IllegalStateException("获取实时金价失败: " + e.getMessage(), e);
        }
    }

    public GoldRealtimePriceResponse getRealtimePrice(boolean forceRefreshCurrent) throws Exception {
        return marketGoldClient.getRealtimePrice(forceRefreshCurrent);
    }

    public BigDecimal getCachedSpotPrice() {
        return marketGoldClient.getCachedSpotPrice();
    }

    public BigDecimal getStrictRealtimeSpotPrice() {
        return getCachedSpotPrice();
    }

    public boolean hasWarmCache() {
        return marketGoldClient.hasWarmCache();
    }

    public void refreshCache() throws Exception {
        marketGoldClient.refreshCache();
    }
}
