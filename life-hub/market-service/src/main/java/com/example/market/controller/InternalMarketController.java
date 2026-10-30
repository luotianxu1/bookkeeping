package com.example.market.controller;

import com.example.common.result.Result;
import com.example.market.market.ActivityService;
import com.example.market.market.KlineService;
import com.example.market.market.StockListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 行情中心内部接口：供 app-service（选股日K、涨跌停统计）等服务间调用，
 * 由 ApiKeyFilter 校验 X-Internal-Key，不走用户鉴权。
 */
@RestController
@RequestMapping("/api/internal/market")
@Tag(name = "行情内部接口", description = "服务间行情数据通道（X-Internal-Key 鉴权）")
public class InternalMarketController {

    @GetMapping("/kline")
    @Operation(summary = "新浪日K线（选股引擎逐股抓取用）")
    public Result<List<Map<String, Object>>> kline(
        @RequestParam("symbol") String symbol,
        @RequestParam("datalen") int datalen,
        @RequestParam("scale") String scale
    ) {
        return Result.ok(KlineService.sinaKline(symbol, datalen, scale));
    }

    @GetMapping("/limits")
    @Operation(summary = "A股涨跌停原始统计（limit_up/limit_down/limit_broken/up/down）")
    public Result<Map<String, Object>> limits() {
        return Result.ok(ActivityService.marketLimits());
    }

    @GetMapping("/stock-list")
    @Operation(summary = "全市场A股清单（新浪 hs_a：沪深主板/创业板/科创板/北交所）",
        description = "选股引擎枚举用，行字段 code/name/symbol/market，停牌无效标的已过滤，服务端缓存 30 分钟")
    public Result<List<Map<String, Object>>> stockList() {
        return Result.ok(StockListService.aShareUniverse());
    }
}
