package com.example.market.fund;

import com.example.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market/funds")
@Tag(name = "基金行情", description = "净值型公募基金排行、代码及名称搜索")
public class FundMarketController {
    private final FundMarketService service;
    public FundMarketController(FundMarketService service) { this.service = service; }

    @GetMapping
    public Result<FundMarketService.Ranking> list(@RequestParam(defaultValue = "all") String type,
        @RequestParam(defaultValue = "year") String sort, @RequestParam(defaultValue = "desc") String order,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.list(type, sort, order, page, pageSize));
    }
    @GetMapping("/search")
    public Result<FundMarketService.SearchResult> search(@RequestParam String keyword) {
        return Result.ok(service.search(keyword));
    }
}
