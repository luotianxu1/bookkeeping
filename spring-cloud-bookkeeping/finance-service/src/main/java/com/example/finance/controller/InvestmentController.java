package com.example.finance.controller;

import com.example.common.result.Result;
import com.example.finance.dto.InvestmentAssetDetailResponse;
import com.example.finance.dto.InvestmentAutoInvestPlanRequest;
import com.example.finance.dto.InvestmentAutoInvestPlanResponse;
import com.example.finance.dto.InvestmentDividendIncomePageResponse;
import com.example.finance.dto.InvestmentFixedExpenseRequest;
import com.example.finance.dto.InvestmentFixedExpenseResponse;
import com.example.finance.dto.InvestmentDividendResponse;
import com.example.finance.dto.InvestmentPositionRequest;
import com.example.finance.dto.InvestmentPositionResponse;
import com.example.finance.dto.InvestmentProductRequest;
import com.example.finance.dto.InvestmentProductResponse;
import com.example.finance.dto.InvestmentSummaryResponse;
import com.example.finance.dto.InvestmentTrendResponse;
import com.example.finance.dto.InvestmentTransactionRequest;
import com.example.finance.dto.InvestmentTransactionResponse;
import com.example.finance.dto.FundProfitForecastResponse;
import com.example.finance.dto.FundProfitPageResponse;
import com.example.finance.service.InvestmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/finance/investments")
@Tag(name = "投资", description = "投资账户、持仓、交易、分红接口")
public class InvestmentController {

    private final InvestmentService investmentService;

    public InvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @GetMapping("/products")
    @Operation(summary = "查询投资产品")
    public Result<List<InvestmentProductResponse>> listProducts(
        @RequestParam(name = "productType", required = false) String productType,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(investmentService.listProducts(productType, keyword));
    }

    @PostMapping("/products")
    @Operation(summary = "新增投资产品")
    public Result<InvestmentProductResponse> createProduct(@Valid @RequestBody InvestmentProductRequest request) {
        return Result.ok(investmentService.createProduct(request));
    }

    @GetMapping("/summary")
    @Operation(summary = "查询投资汇总")
    public Result<InvestmentSummaryResponse> summary(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(investmentService.summary(userId, accountId));
    }

    @GetMapping("/trend")
    @Operation(summary = "查询投资资产趋势")
    public Result<InvestmentTrendResponse> trend(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId,
        @RequestParam(name = "range", required = false) String range
    ) {
        return Result.ok(investmentService.trend(userId, accountId, range));
    }

    @GetMapping("/fund-profit-forecast")
    @Operation(summary = "查询基金收益预测")
    public Result<FundProfitForecastResponse> fundProfitForecast(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(investmentService.fundProfitForecast(userId, accountId));
    }

    @GetMapping("/fund-profit")
    @Operation(summary = "查询基金收益页数据")
    public Result<FundProfitPageResponse> fundProfitPage(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId,
        @RequestParam(name = "view", required = false) String view,
        @RequestParam(name = "anchor", required = false) String anchor,
        @RequestParam(name = "selected", required = false) String selected
    ) {
        return Result.ok(investmentService.fundProfitPage(userId, accountId, view, anchor, selected));
    }

    @GetMapping("/positions")
    @Operation(summary = "查询投资持仓列表")
    public Result<List<InvestmentPositionResponse>> listPositions(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId,
        @RequestParam(name = "productType", required = false) String productType,
        @RequestParam(name = "status", required = false) String status
    ) {
        return Result.ok(investmentService.listPositions(userId, accountId, productType, status));
    }

    @GetMapping("/positions/{id}")
    @Operation(summary = "查询投资持仓详情")
    public Result<InvestmentPositionResponse> positionDetail(@PathVariable("id") @NotNull Long id) {
        return investmentService.getPosition(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<InvestmentPositionResponse>fail().code(404).message("投资持仓不存在"));
    }

    @GetMapping("/positions/{id}/detail")
    @Operation(summary = "查询投资资产行情详情")
    public Result<InvestmentAssetDetailResponse> assetDetail(@PathVariable("id") @NotNull Long id) {
        return investmentService.getPositionDetail(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<InvestmentAssetDetailResponse>fail().code(404).message("投资持仓不存在"));
    }

    @PostMapping("/positions")
    @Operation(summary = "新增投资持仓")
    public Result<InvestmentPositionResponse> createPosition(@Valid @RequestBody InvestmentPositionRequest request) {
        return Result.ok(investmentService.createPosition(request));
    }

    @PutMapping("/positions/{id}")
    @Operation(summary = "修改投资持仓")
    public Result<InvestmentPositionResponse> updatePosition(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody InvestmentPositionRequest request
    ) {
        return investmentService.updatePosition(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<InvestmentPositionResponse>fail().code(404).message("投资持仓不存在"));
    }

    @DeleteMapping("/positions/{id}")
    @Operation(summary = "删除投资持仓")
    public Result<Void> deletePosition(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!investmentService.deletePosition(id, userId)) {
            return Result.<Void>fail().code(404).message("投资持仓不存在");
        }
        return Result.ok();
    }

    @GetMapping("/transactions")
    @Operation(summary = "查询投资交易流水")
    public Result<List<InvestmentTransactionResponse>> listTransactions(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId,
        @RequestParam(name = "positionId", required = false) Long positionId
    ) {
        return Result.ok(investmentService.listTransactions(userId, accountId, positionId));
    }

    @PostMapping("/transactions")
    @Operation(summary = "新增投资交易流水")
    public Result<InvestmentTransactionResponse> createTransaction(@Valid @RequestBody InvestmentTransactionRequest request) {
        return Result.ok(investmentService.createTransaction(request));
    }

    @DeleteMapping("/transactions/{id}")
    @Operation(summary = "删除投资交易流水")
    public Result<Void> deleteTransaction(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!investmentService.deleteTransaction(id, userId)) {
            return Result.<Void>fail().code(404).message("投资交易不存在");
        }
        return Result.ok();
    }

    @GetMapping("/auto-invest-plans")
    @Operation(summary = "查询基金定投计划")
    public Result<List<InvestmentAutoInvestPlanResponse>> listAutoInvestPlans(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId,
        @RequestParam(name = "positionId", required = false) Long positionId,
        @RequestParam(name = "status", required = false) String status
    ) {
        return Result.ok(investmentService.listAutoInvestPlans(userId, accountId, positionId, status));
    }

    @PostMapping("/auto-invest-plans")
    @Operation(summary = "新增基金定投计划")
    public Result<InvestmentAutoInvestPlanResponse> createAutoInvestPlan(@Valid @RequestBody InvestmentAutoInvestPlanRequest request) {
        return Result.ok(investmentService.createAutoInvestPlan(request));
    }

    @PutMapping("/auto-invest-plans/{id}")
    @Operation(summary = "修改基金定投计划")
    public Result<InvestmentAutoInvestPlanResponse> updateAutoInvestPlan(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody InvestmentAutoInvestPlanRequest request
    ) {
        return investmentService.updateAutoInvestPlan(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<InvestmentAutoInvestPlanResponse>fail().code(404).message("定投计划不存在"));
    }

    @DeleteMapping("/auto-invest-plans/{id}")
    @Operation(summary = "删除基金定投计划")
    public Result<Void> deleteAutoInvestPlan(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!investmentService.deleteAutoInvestPlan(id, userId)) {
            return Result.<Void>fail().code(404).message("定投计划不存在");
        }
        return Result.ok();
    }

    @GetMapping("/dividends")
    @Operation(summary = "查询投资分红")
    public Result<List<InvestmentDividendResponse>> listDividends(
        @RequestParam(name = "userId") @NotNull Long userId,
        @RequestParam(name = "accountId", required = false) Long accountId
    ) {
        return Result.ok(investmentService.listDividends(userId, accountId));
    }

    @GetMapping("/dividend-income")
    @Operation(summary = "查询攒股收息页面数据")
    public Result<InvestmentDividendIncomePageResponse> dividendIncome(
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(investmentService.dividendIncome(userId));
    }

    @GetMapping("/fixed-expenses")
    @Operation(summary = "查询投资固定支出")
    public Result<List<InvestmentFixedExpenseResponse>> listFixedExpenses(
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        return Result.ok(investmentService.listFixedExpenses(userId));
    }

    @PostMapping("/fixed-expenses")
    @Operation(summary = "新增投资固定支出")
    public Result<InvestmentFixedExpenseResponse> createFixedExpense(@Valid @RequestBody InvestmentFixedExpenseRequest request) {
        return Result.ok(investmentService.createFixedExpense(request));
    }

    @PutMapping("/fixed-expenses/{id}")
    @Operation(summary = "修改投资固定支出")
    public Result<InvestmentFixedExpenseResponse> updateFixedExpense(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody InvestmentFixedExpenseRequest request
    ) {
        return investmentService.updateFixedExpense(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<InvestmentFixedExpenseResponse>fail().code(404).message("固定支出不存在"));
    }

    @DeleteMapping("/fixed-expenses/{id}")
    @Operation(summary = "删除投资固定支出")
    public Result<Void> deleteFixedExpense(
        @PathVariable("id") @NotNull Long id,
        @RequestParam(name = "userId") @NotNull Long userId
    ) {
        if (!investmentService.deleteFixedExpense(id, userId)) {
            return Result.<Void>fail().code(404).message("固定支出不存在");
        }
        return Result.ok();
    }
}
