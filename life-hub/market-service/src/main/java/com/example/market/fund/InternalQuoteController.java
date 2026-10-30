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
 * 基金/报价内部接口：供 app-service 投资模块（产品报价、净值同步、分红、搜索、申购赎回）
 * 服务间调用，由 InternalApiKeyFilter 校验 X-Internal-Key，不走用户鉴权。
 */
@RestController
@RequestMapping("/api/internal/quote")
@Tag(name = "基金报价内部接口", description = "服务间基金/报价数据通道（X-Internal-Key 鉴权）")
public class InternalQuoteController {

    private final FundDataService fundDataService;

    public InternalQuoteController(FundDataService fundDataService) {
        this.fundDataService = fundDataService;
    }

    @GetMapping("/fund/base")
    @Operation(summary = "基金基础信息", description = "名称/类型/公司/申赎状态/净值摘要（字段与原东财口径兼容）")
    public Result<Map<String, Object>> fundBase(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundBase(code));
    }

    @GetMapping("/fund/estimate")
    @Operation(summary = "基金盘中估值", description = "盘中估值/昨日净值/净值日期（字段与原东财口径兼容）")
    public Result<Map<String, Object>> fundEstimate(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundEstimate(code));
    }

    @GetMapping("/fund/fees")
    @Operation(summary = "基金费率", description = "赎回费分档（label 已归一）+ 管理费/托管费")
    public Result<Map<String, Object>> fundFees(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundFees(code));
    }

    @GetMapping("/fund/nav-history")
    @Operation(summary = "基金净值历史", description = "全量升序日频净值（近年），date/nav/accNav")
    public Result<Map<String, Object>> fundNavHistory(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundNavHistory(code));
    }

    @GetMapping("/fund/dividends")
    @Operation(summary = "基金分红历史", description = "东财独家数据源，matched=false 表示页面代码不匹配")
    public Result<Map<String, Object>> fundDividends(@RequestParam("code") String code) {
        return Result.ok(fundDataService.fundDividends(code));
    }

    @GetMapping("/stock/snapshot")
    @Operation(summary = "A股快照", description = "名称/现价/交易所（腾讯 qt.gtimg.cn）")
    public Result<Map<String, Object>> stockSnapshot(@RequestParam("code") String code) {
        return Result.ok(fundDataService.stockSnapshot(code));
    }

    @GetMapping("/stock/dividends")
    @Operation(summary = "股票分红历史", description = "新浪分红送配页，每10股口径")
    public Result<Map<String, Object>> stockDividends(@RequestParam("code") String code) {
        return Result.ok(fundDataService.stockDividends(code));
    }

    @GetMapping("/search")
    @Operation(summary = "产品搜索", description = "腾讯 smartbox 联想，type: fund/stock")
    public Result<Map<String, Object>> search(@RequestParam("keyword") String keyword) throws Exception {
        return Result.ok(fundDataService.search(keyword));
    }
}
