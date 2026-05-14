package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.ExchangeRateResponse;
import com.example.finance.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/exchange-rates")
@Tag(name = "汇率", description = "汇率换算接口")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping
    @Operation(summary = "查询币种汇率")
    public Result<ExchangeRateResponse> detail(
        @RequestParam(name = "from", required = false, defaultValue = "USD") String fromCurrency,
        @RequestParam(name = "to", required = false, defaultValue = "CNY") String toCurrency
    ) {
        return Result.ok(exchangeRateService.getRate(fromCurrency, toCurrency));
    }
}
