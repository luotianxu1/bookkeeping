package com.example.finance.service;

import com.example.finance.dto.LimitUpDownResponse;
import com.example.finance.market.ActivityService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A 股涨跌停统计（涨停/跌停/炸板/封板率）。
 *
 * <p>数据源与行情中心市场热度同源：{@link ActivityService} 新浪 hs_a 全市场分页扫描
 * （自带 60 秒缓存与涨跌停判定），本类仅做 DTO 结构映射，保持
 * {@code /api/finance/limit-up-down} 响应结构不变。</p>
 */
@Service
public class LimitUpDownService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DISPLAY_STOCK_COUNT = 6;

    public LimitUpDownResponse getLimitUpDown() {
        Map<String, Object> raw = ActivityService.marketLimits();
        int limitUp = asInt(raw.get("limit_up"));
        int limitDown = asInt(raw.get("limit_down"));
        int broken = asInt(raw.get("limit_broken"));

        int attempted = limitUp + broken;
        BigDecimal sealRate = attempted == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf((double) limitUp * 100D / attempted)
                .setScale(1, RoundingMode.HALF_UP);

        LimitUpDownResponse response = new LimitUpDownResponse();
        response.setLimitUpCount(limitUp);
        response.setLimitDownCount(limitDown);
        response.setBrokenLimitCount(broken);
        response.setSealRate(sealRate);
        response.setUpdatedAt(LocalDateTime.now(MARKET_ZONE));
        response.setSource("新浪公开行情");
        response.setLimitUps(toItems(raw.get("up")));
        response.setLimitDowns(toItems(raw.get("down")));
        return response;
    }

    /** 涨跌停明细已按成交额降序（封板热度），取前 N 只展示 */
    @SuppressWarnings("unchecked")
    private List<LimitUpDownResponse.StockItem> toItems(Object rows) {
        List<LimitUpDownResponse.StockItem> items = new ArrayList<>();
        if (!(rows instanceof List<?> list)) {
            return items;
        }
        for (Object o : list) {
            if (items.size() >= DISPLAY_STOCK_COUNT) {
                break;
            }
            Map<String, Object> row = (Map<String, Object>) o;
            LimitUpDownResponse.StockItem item = new LimitUpDownResponse.StockItem();
            item.setCode(String.valueOf(row.getOrDefault("code", "")));
            item.setName(String.valueOf(row.getOrDefault("name", "")));
            Object pct = row.get("pct");
            item.setChangePercent(pct instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            // 新浪 hs_a 无行业字段，前端已兜底显示「其他」
            item.setIndustry("");
            items.add(item);
        }
        return items;
    }

    private int asInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
