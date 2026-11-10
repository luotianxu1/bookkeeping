package com.example.market.fund;

import com.example.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 基金/报价对外接口：供前端投资详情页消费（替代浏览器直连东财/腾讯），
 * 走 /api/market/** 统一 JWT 鉴权（与行情中心其他公开接口同套）。
 */
@RestController
@RequestMapping("/api/market/quote")
@Tag(name = "基金报价接口", description = "投资详情页基金/股票行情（登录态）")
public class MarketQuoteController {

    private final FundDataService fundDataService;

    public MarketQuoteController(FundDataService fundDataService) {
        this.fundDataService = fundDataService;
    }

    @GetMapping("/fund/base")
    @Operation(summary = "基金基础信息", description = "名称/类型/公司/申赎状态/净值摘要（EM 兼容字段）")
    public Result<Map<String, Object>> fundBase(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundBase(fundCode(code)));
    }

    @GetMapping("/fund/trend")
    @Operation(summary = "基金净值走势", description = "近4年日频净值历史（倒序），date/nav/accNav")
    public Result<Map<String, Object>> fundTrend(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundNavHistory(fundCode(code)));
    }

    @GetMapping("/fund/period-returns")
    @Operation(summary = "基金阶段涨幅", description = "近1月/3月/6月/1年/2年/3年/5年收益率，rows:[{title,syl}]，title=Y/3Y/6Y/1N/2N/3N/5N")
    public Result<Map<String, Object>> fundPeriodReturns(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundPeriodReturns(fundCode(code)));
    }

    private String fundCode(String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("请输入6位基金代码");
        return code;
    }

    @GetMapping("/stock/snapshot")
    @Operation(summary = "A股快照", description = "名称/现价/开高低/涨跌/换手率/PE（腾讯 qt.gtimg.cn）")
    public Result<Map<String, Object>> stockSnapshot(@RequestParam("code") String code) {
        return Result.ok(fundDataService.stockSnapshot(code));
    }
}
