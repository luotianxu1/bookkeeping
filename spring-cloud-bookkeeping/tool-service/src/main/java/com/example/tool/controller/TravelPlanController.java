package com.example.tool.controller;

import com.example.common.result.Result;
import com.example.tool.dto.TravelPlanCompanionRequest;
import com.example.tool.dto.TravelPlanCompanionResponse;
import com.example.tool.dto.TravelPlanDayRequest;
import com.example.tool.dto.TravelPlanDayResponse;
import com.example.tool.dto.TravelPlanDetailResponse;
import com.example.tool.dto.TravelPlanExpenseRequest;
import com.example.tool.dto.TravelPlanExpenseResponse;
import com.example.tool.dto.TravelPlanItineraryRequest;
import com.example.tool.dto.TravelPlanItineraryResponse;
import com.example.tool.dto.TravelPlanOverviewResponse;
import com.example.tool.dto.TravelPlanRequest;
import com.example.tool.dto.TravelPlanResponse;
import com.example.tool.service.TravelPlanService;
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
@RequestMapping("/api/tools/travel-plans")
@Tag(name = "旅行管理", description = "旅行、路线、同行人和费用管理接口")
public class TravelPlanController {

    private final TravelPlanService travelPlanService;

    public TravelPlanController(TravelPlanService travelPlanService) {
        this.travelPlanService = travelPlanService;
    }

    @GetMapping
    @Operation(summary = "查询旅行列表")
    public Result<List<TravelPlanResponse>> list(
        @RequestParam(name = "userId", required = false) Long userId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return Result.ok(travelPlanService.list(userId, status, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询旅行详情")
    public Result<TravelPlanDetailResponse> detail(@PathVariable("id") @NotNull Long id) {
        return travelPlanService.getById(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<TravelPlanDetailResponse>fail().code(404).message("旅行不存在"));
    }

    @GetMapping("/{id}/overview")
    @Operation(summary = "查询旅行概览")
    public Result<TravelPlanOverviewResponse> overview(@PathVariable("id") @NotNull Long id) {
        return travelPlanService.getOverview(id)
            .map(Result::ok)
            .orElseGet(() -> Result.<TravelPlanOverviewResponse>fail().code(404).message("旅行不存在"));
    }

    @PostMapping
    @Operation(summary = "新增旅行")
    public Result<TravelPlanResponse> create(@Valid @RequestBody TravelPlanRequest request) {
        return Result.ok(travelPlanService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改旅行")
    public Result<TravelPlanResponse> update(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody TravelPlanRequest request
    ) {
        return travelPlanService.update(id, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<TravelPlanResponse>fail().code(404).message("旅行不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除旅行")
    public Result<Void> delete(
        @PathVariable("id") @NotNull Long id,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!travelPlanService.delete(id, userId)) {
            return Result.<Void>fail().code(404).message("旅行不存在");
        }
        return Result.ok();
    }

    @PostMapping("/{id}/companions")
    @Operation(summary = "新增同行人")
    public Result<TravelPlanCompanionResponse> createCompanion(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody TravelPlanCompanionRequest request
    ) {
        return Result.ok(travelPlanService.createCompanion(id, request));
    }

    @PutMapping("/companions/{companionId}")
    @Operation(summary = "修改同行人")
    public Result<TravelPlanCompanionResponse> updateCompanion(
        @PathVariable("companionId") @NotNull Long companionId,
        @Valid @RequestBody TravelPlanCompanionRequest request
    ) {
        return travelPlanService.updateCompanion(companionId, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<TravelPlanCompanionResponse>fail().code(404).message("同行人不存在"));
    }

    @DeleteMapping("/companions/{companionId}")
    @Operation(summary = "删除同行人")
    public Result<Void> deleteCompanion(
        @PathVariable("companionId") @NotNull Long companionId,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!travelPlanService.deleteCompanion(companionId, userId)) {
            return Result.<Void>fail().code(404).message("同行人不存在");
        }
        return Result.ok();
    }

    @PostMapping("/{id}/days")
    @Operation(summary = "新增旅行天")
    public Result<TravelPlanDayResponse> createDay(
        @PathVariable("id") @NotNull Long id,
        @Valid @RequestBody TravelPlanDayRequest request
    ) {
        return Result.ok(travelPlanService.createDay(id, request));
    }

    @PutMapping("/days/{dayId}")
    @Operation(summary = "修改旅行天")
    public Result<TravelPlanDayResponse> updateDay(
        @PathVariable("dayId") @NotNull Long dayId,
        @Valid @RequestBody TravelPlanDayRequest request
    ) {
        return travelPlanService.updateDay(dayId, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<TravelPlanDayResponse>fail().code(404).message("旅行天不存在"));
    }

    @DeleteMapping("/days/{dayId}")
    @Operation(summary = "删除旅行天")
    public Result<Void> deleteDay(
        @PathVariable("dayId") @NotNull Long dayId,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!travelPlanService.deleteDay(dayId, userId)) {
            return Result.<Void>fail().code(404).message("旅行天不存在");
        }
        return Result.ok();
    }

    @PostMapping("/days/{dayId}/itineraries")
    @Operation(summary = "新增行程项")
    public Result<TravelPlanItineraryResponse> createItinerary(
        @PathVariable("dayId") @NotNull Long dayId,
        @Valid @RequestBody TravelPlanItineraryRequest request
    ) {
        return Result.ok(travelPlanService.createItinerary(dayId, request));
    }

    @PutMapping("/itineraries/{itineraryId}")
    @Operation(summary = "修改行程项")
    public Result<TravelPlanItineraryResponse> updateItinerary(
        @PathVariable("itineraryId") @NotNull Long itineraryId,
        @Valid @RequestBody TravelPlanItineraryRequest request
    ) {
        return travelPlanService.updateItinerary(itineraryId, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<TravelPlanItineraryResponse>fail().code(404).message("行程项不存在"));
    }

    @DeleteMapping("/itineraries/{itineraryId}")
    @Operation(summary = "删除行程项")
    public Result<Void> deleteItinerary(
        @PathVariable("itineraryId") @NotNull Long itineraryId,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!travelPlanService.deleteItinerary(itineraryId, userId)) {
            return Result.<Void>fail().code(404).message("行程项不存在");
        }
        return Result.ok();
    }

    @PostMapping("/days/{dayId}/expenses")
    @Operation(summary = "新增费用")
    public Result<TravelPlanExpenseResponse> createExpense(
        @PathVariable("dayId") @NotNull Long dayId,
        @Valid @RequestBody TravelPlanExpenseRequest request
    ) {
        return Result.ok(travelPlanService.createExpense(dayId, request));
    }

    @PutMapping("/expenses/{expenseId}")
    @Operation(summary = "修改费用")
    public Result<TravelPlanExpenseResponse> updateExpense(
        @PathVariable("expenseId") @NotNull Long expenseId,
        @Valid @RequestBody TravelPlanExpenseRequest request
    ) {
        return travelPlanService.updateExpense(expenseId, request)
            .map(Result::ok)
            .orElseGet(() -> Result.<TravelPlanExpenseResponse>fail().code(404).message("费用不存在"));
    }

    @DeleteMapping("/expenses/{expenseId}")
    @Operation(summary = "删除费用")
    public Result<Void> deleteExpense(
        @PathVariable("expenseId") @NotNull Long expenseId,
        @RequestParam("userId") @NotNull Long userId
    ) {
        if (!travelPlanService.deleteExpense(expenseId, userId)) {
            return Result.<Void>fail().code(404).message("费用不存在");
        }
        return Result.ok();
    }
}
