package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.StockScreenPageResponse;
import com.example.finance.dto.StockScreenRunResponse;
import com.example.finance.service.StockScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/stock-screener")
@Tag(name = "A股选股", description = "A股全市场扫描、规则筛选和运行状态接口")
public class StockScreenerController {

    private final StockScreenerService stockScreenerService;

    public StockScreenerController(StockScreenerService stockScreenerService) {
        this.stockScreenerService = stockScreenerService;
    }

    @GetMapping("/status")
    @Operation(summary = "查询最近一次全市场扫描状态")
    public Result<StockScreenRunResponse> status() {
        return Result.ok(stockScreenerService.getLatestRun());
    }

    @GetMapping("/results")
    @Operation(summary = "按所选规则筛选最近一次全市场快照")
    public Result<StockScreenPageResponse> results(
        @RequestParam(name = "market", required = false, defaultValue = "ALL") String market,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "ruleKey", required = false, defaultValue = "sunrise-rise") String ruleKey,
        @RequestParam(name = "minBearishCount", required = false, defaultValue = "4") Integer minBearishCount,
        @RequestParam(name = "minThreeDayDecline", required = false, defaultValue = "9") BigDecimal minThreeDayDecline,
        @RequestParam(name = "minLastDayDecline", required = false, defaultValue = "3") BigDecimal minLastDayDecline,
        @RequestParam(name = "requireVolumeUp", required = false, defaultValue = "false") Boolean requireVolumeUp,
        @RequestParam(name = "requireNoLowerShadow", required = false, defaultValue = "false") Boolean requireNoLowerShadow,
        @RequestParam(name = "includeChiNext", required = false, defaultValue = "false") Boolean includeChiNext,
        @RequestParam(name = "includeStar", required = false, defaultValue = "false") Boolean includeStarMarket,
        @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
        @RequestParam(name = "pageSize", required = false, defaultValue = "20") Integer pageSize
    ) {
        return Result.ok(stockScreenerService.screen(
            market,
            keyword,
            ruleKey,
            minBearishCount,
            minThreeDayDecline,
            minLastDayDecline,
            requireVolumeUp,
            requireNoLowerShadow,
            includeChiNext,
            includeStarMarket,
            page,
            pageSize
        ));
    }

    @PostMapping("/runs")
    @Operation(summary = "手动提交A股全市场扫描")
    public Result<Map<String, Object>> run(
        @RequestParam(name = "force", required = false, defaultValue = "false") Boolean force
    ) {
        var submission = stockScreenerService.submitManualScan(Boolean.TRUE.equals(force));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", submission.status());
        response.put("message", submission.message());
        if (submission.runId() != null) {
            response.put("runId", submission.runId());
        }
        if (submission.tradeDate() != null) {
            response.put("tradeDate", submission.tradeDate());
        }
        response.put("submittedAt", LocalDateTime.now());
        return Result.ok(response);
    }

    @PostMapping("/runs/stop")
    @Operation(summary = "停止正在运行的A股全市场扫描")
    public Result<Map<String, Object>> stop() {
        var submission = stockScreenerService.requestStop();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", submission.status());
        response.put("message", submission.message());
        if (submission.runId() != null) {
            response.put("runId", submission.runId());
        }
        response.put("submittedAt", LocalDateTime.now());
        return Result.ok(response);
    }
}
