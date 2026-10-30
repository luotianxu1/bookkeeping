package com.example.market.forex;

import com.example.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 汇率内部接口：供 app-service 汇率换算（/api/finance/exchange-rates）服务间调用，
 * 由 InternalApiKeyFilter 校验 X-Internal-Key，不走用户鉴权。
 */
@RestController
@RequestMapping("/api/internal/forex")
@Tag(name = "汇率内部接口", description = "服务间汇率数据通道（X-Internal-Key 鉴权）")
public class InternalForexController {

    private final ForexRateService forexRateService;

    public InternalForexController(ForexRateService forexRateService) {
        this.forexRateService = forexRateService;
    }

    @GetMapping("/rate")
    @Operation(summary = "查询币种汇率", description = "open.er-api 主源 + 内置兜底，字段与原 app-service 口径一致")
    public Result<Map<String, Object>> rate(
        @RequestParam(name = "from", required = false, defaultValue = "USD") String fromCurrency,
        @RequestParam(name = "to", required = false, defaultValue = "CNY") String toCurrency
    ) {
        return Result.ok(forexRateService.getRate(fromCurrency, toCurrency));
    }
}
