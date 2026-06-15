package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.example.tool.entity.ContactEntity;
import com.example.tool.entity.TravelPlanCompanionEntity;
import com.example.tool.entity.TravelPlanDayEntity;
import com.example.tool.entity.TravelPlanEntity;
import com.example.tool.entity.TravelPlanExpenseEntity;
import com.example.tool.entity.TravelPlanItineraryEntity;
import com.example.tool.mapper.ContactMapper;
import com.example.tool.mapper.TravelPlanCompanionMapper;
import com.example.tool.mapper.TravelPlanDayMapper;
import com.example.tool.mapper.TravelPlanExpenseMapper;
import com.example.tool.mapper.TravelPlanItineraryMapper;
import com.example.tool.mapper.TravelPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TravelPlanService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String STATUS_ALL = "all";
    private static final Set<String> VALID_PLAN_STATUSES = Set.of(STATUS_ACTIVE, STATUS_COMPLETED, STATUS_CANCELLED);

    private static final String ITINERARY_TYPE_TRANSPORT = "transport";
    private static final String ITINERARY_TYPE_SCENIC = "scenic";
    private static final String ITINERARY_TYPE_DINING = "dining";
    private static final String ITINERARY_TYPE_ACCOMMODATION = "accommodation";
    private static final Set<String> VALID_ITINERARY_TYPES = Set.of(
        ITINERARY_TYPE_TRANSPORT,
        ITINERARY_TYPE_SCENIC,
        ITINERARY_TYPE_DINING,
        ITINERARY_TYPE_ACCOMMODATION
    );
    private static final Set<String> VALID_TRANSPORT_MODES = Set.of("driving", "walking", "riding");

    private static final String EXPENSE_TYPE_OTHER = "other";
    private static final Set<String> VALID_EXPENSE_TYPES = Set.of(
        ITINERARY_TYPE_TRANSPORT,
        ITINERARY_TYPE_SCENIC,
        ITINERARY_TYPE_DINING,
        ITINERARY_TYPE_ACCOMMODATION,
        EXPENSE_TYPE_OTHER
    );

    private final TravelPlanMapper travelPlanMapper;
    private final TravelPlanCompanionMapper travelPlanCompanionMapper;
    private final TravelPlanDayMapper travelPlanDayMapper;
    private final TravelPlanItineraryMapper travelPlanItineraryMapper;
    private final TravelPlanExpenseMapper travelPlanExpenseMapper;
    private final ContactMapper contactMapper;

    public TravelPlanService(
        TravelPlanMapper travelPlanMapper,
        TravelPlanCompanionMapper travelPlanCompanionMapper,
        TravelPlanDayMapper travelPlanDayMapper,
        TravelPlanItineraryMapper travelPlanItineraryMapper,
        TravelPlanExpenseMapper travelPlanExpenseMapper,
        ContactMapper contactMapper
    ) {
        this.travelPlanMapper = travelPlanMapper;
        this.travelPlanCompanionMapper = travelPlanCompanionMapper;
        this.travelPlanDayMapper = travelPlanDayMapper;
        this.travelPlanItineraryMapper = travelPlanItineraryMapper;
        this.travelPlanExpenseMapper = travelPlanExpenseMapper;
        this.contactMapper = contactMapper;
    }

    public List<TravelPlanResponse> list(Long userId, String status, String keyword) {
        String normalizedStatus = normalizeListStatus(status);
        List<TravelPlanEntity> plans = travelPlanMapper.selectList(new LambdaQueryWrapper<TravelPlanEntity>()
            .eq(userId != null, TravelPlanEntity::getUserId, userId)
            .eq(shouldFilterStatus(normalizedStatus), TravelPlanEntity::getStatus, normalizedStatus)
            .and(StringUtils.hasText(keyword), query -> query
                .like(TravelPlanEntity::getName, keyword.trim())
                .or()
                .like(TravelPlanEntity::getDestination, keyword.trim())
                .or()
                .like(TravelPlanEntity::getRemark, keyword.trim()))
            .orderByDesc(TravelPlanEntity::getStartDate)
            .orderByAsc(TravelPlanEntity::getSortOrder)
            .orderByDesc(TravelPlanEntity::getId));

        if (plans.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> planIds = plans.stream().map(TravelPlanEntity::getId).toList();
        Map<Long, Integer> companionCountMap = buildCountMap(
            travelPlanCompanionMapper.selectList(new LambdaQueryWrapper<TravelPlanCompanionEntity>()
                .in(TravelPlanCompanionEntity::getTravelPlanId, planIds)),
            TravelPlanCompanionEntity::getTravelPlanId
        );
        Map<Long, Integer> dayCountMap = buildCountMap(
            travelPlanDayMapper.selectList(new LambdaQueryWrapper<TravelPlanDayEntity>()
                .in(TravelPlanDayEntity::getTravelPlanId, planIds)),
            TravelPlanDayEntity::getTravelPlanId
        );
        Map<Long, List<TravelPlanExpenseEntity>> expenseMap = travelPlanExpenseMapper.selectList(
            new LambdaQueryWrapper<TravelPlanExpenseEntity>()
                .in(TravelPlanExpenseEntity::getTravelPlanId, planIds)
        ).stream().collect(Collectors.groupingBy(TravelPlanExpenseEntity::getTravelPlanId));

        return plans.stream()
            .map(plan -> toSummaryResponse(
                plan,
                companionCountMap.getOrDefault(plan.getId(), 0),
                dayCountMap.getOrDefault(plan.getId(), 0),
                expenseMap.getOrDefault(plan.getId(), Collections.emptyList())
            ))
            .toList();
    }

    public Optional<TravelPlanDetailResponse> getById(Long id) {
        TravelPlanEntity entity = travelPlanMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(buildDetail(entity));
    }

    public Optional<TravelPlanOverviewResponse> getOverview(Long id) {
        TravelPlanEntity entity = travelPlanMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(buildDetail(entity).getOverview());
    }

    @Transactional
    public TravelPlanResponse create(TravelPlanRequest request) {
        validatePlanDates(request.getStartDate(), request.getEndDate());

        TravelPlanEntity entity = new TravelPlanEntity();
        fillPlanEntity(entity, request);
        travelPlanMapper.insert(entity);
        syncPlanDays(entity);
        return toSummaryResponse(travelPlanMapper.selectById(entity.getId()), 0, 0, Collections.emptyList());
    }

    @Transactional
    public Optional<TravelPlanResponse> update(Long id, TravelPlanRequest request) {
        TravelPlanEntity entity = travelPlanMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }

        validatePlanDates(request.getStartDate(), request.getEndDate());
        fillPlanEntity(entity, request);
        travelPlanMapper.updateById(entity);
        syncPlanDays(entity);

        TravelPlanEntity latest = travelPlanMapper.selectById(id);
        TravelPlanDetailResponse detail = buildDetail(latest);
        return Optional.of(toSummaryResponse(
            latest,
            detail.getOverview().getCompanionCount(),
            detail.getOverview().getDayCount(),
            detail.getExpenses().stream().map(this::toExpenseEntityShadow).toList()
        ));
    }

    @Transactional
    public boolean delete(Long id, Long userId) {
        TravelPlanEntity entity = travelPlanMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        return travelPlanMapper.deleteById(id) > 0;
    }

    @Transactional
    public TravelPlanCompanionResponse createCompanion(Long planId, TravelPlanCompanionRequest request) {
        TravelPlanEntity plan = requirePlan(planId, request.getUserId());
        ContactEntity contact = requireUserContact(request.getContactId(), request.getUserId());

        ensureCompanionUnique(plan.getId(), contact.getId(), null);

        TravelPlanCompanionEntity entity = new TravelPlanCompanionEntity();
        entity.setTravelPlanId(plan.getId());
        entity.setContactId(contact.getId());
        entity.setSortOrder(defaultSortOrder(request.getSortOrder()));
        travelPlanCompanionMapper.insert(entity);

        return toCompanionResponse(travelPlanCompanionMapper.selectById(entity.getId()), contact);
    }

    @Transactional
    public Optional<TravelPlanCompanionResponse> updateCompanion(Long companionId, TravelPlanCompanionRequest request) {
        TravelPlanCompanionEntity entity = travelPlanCompanionMapper.selectById(companionId);
        if (entity == null) {
            return Optional.empty();
        }

        TravelPlanEntity plan = requirePlan(entity.getTravelPlanId(), request.getUserId());
        ContactEntity contact = requireUserContact(request.getContactId(), request.getUserId());
        ensureCompanionUnique(plan.getId(), contact.getId(), companionId);

        entity.setContactId(contact.getId());
        entity.setSortOrder(defaultSortOrder(request.getSortOrder()));
        travelPlanCompanionMapper.updateById(entity);

        return Optional.of(toCompanionResponse(travelPlanCompanionMapper.selectById(companionId), contact));
    }

    @Transactional
    public boolean deleteCompanion(Long companionId, Long userId) {
        TravelPlanCompanionEntity entity = travelPlanCompanionMapper.selectById(companionId);
        if (entity == null) {
            return false;
        }

        TravelPlanEntity plan = travelPlanMapper.selectById(entity.getTravelPlanId());
        if (plan == null || !userId.equals(plan.getUserId())) {
            return false;
        }

        return travelPlanCompanionMapper.deleteById(companionId) > 0;
    }

    @Transactional
    public TravelPlanDayResponse createDay(Long planId, TravelPlanDayRequest request) {
        TravelPlanEntity plan = requirePlan(planId, request.getUserId());
        ensureDayIndexUnique(plan.getId(), request.getDayIndex(), null);
        validateDayDateWithinPlan(plan, request.getTravelDate());

        TravelPlanDayEntity entity = new TravelPlanDayEntity();
        fillDayEntity(entity, plan.getId(), request);
        travelPlanDayMapper.insert(entity);
        return toDayResponse(travelPlanDayMapper.selectById(entity.getId()), Collections.emptyList(), Collections.emptyList());
    }

    @Transactional
    public Optional<TravelPlanDayResponse> updateDay(Long dayId, TravelPlanDayRequest request) {
        TravelPlanDayEntity entity = travelPlanDayMapper.selectById(dayId);
        if (entity == null) {
            return Optional.empty();
        }

        TravelPlanEntity plan = requirePlan(entity.getTravelPlanId(), request.getUserId());
        ensureDayIndexUnique(plan.getId(), request.getDayIndex(), dayId);
        validateDayDateWithinPlan(plan, request.getTravelDate());

        fillDayEntity(entity, plan.getId(), request);
        travelPlanDayMapper.updateById(entity);
        return Optional.of(toDayResponse(travelPlanDayMapper.selectById(dayId), Collections.emptyList(), Collections.emptyList()));
    }

    @Transactional
    public boolean deleteDay(Long dayId, Long userId) {
        TravelPlanDayEntity entity = travelPlanDayMapper.selectById(dayId);
        if (entity == null) {
            return false;
        }

        TravelPlanEntity plan = travelPlanMapper.selectById(entity.getTravelPlanId());
        if (plan == null || !userId.equals(plan.getUserId())) {
            return false;
        }

        return travelPlanDayMapper.deleteById(dayId) > 0;
    }

    @Transactional
    public TravelPlanItineraryResponse createItinerary(Long dayId, TravelPlanItineraryRequest request) {
        TravelPlanDayEntity day = requireDay(dayId);
        requirePlan(day.getTravelPlanId(), request.getUserId());

        TravelPlanItineraryEntity entity = new TravelPlanItineraryEntity();
        fillItineraryEntity(entity, day.getId(), request);
        travelPlanItineraryMapper.insert(entity);
        return toItineraryResponse(travelPlanItineraryMapper.selectById(entity.getId()));
    }

    @Transactional
    public Optional<TravelPlanItineraryResponse> updateItinerary(Long itineraryId, TravelPlanItineraryRequest request) {
        TravelPlanItineraryEntity entity = travelPlanItineraryMapper.selectById(itineraryId);
        if (entity == null) {
            return Optional.empty();
        }

        TravelPlanDayEntity day = requireDay(entity.getTravelPlanDayId());
        requirePlan(day.getTravelPlanId(), request.getUserId());

        fillItineraryEntity(entity, day.getId(), request);
        travelPlanItineraryMapper.updateById(entity);
        return Optional.of(toItineraryResponse(travelPlanItineraryMapper.selectById(itineraryId)));
    }

    @Transactional
    public boolean deleteItinerary(Long itineraryId, Long userId) {
        TravelPlanItineraryEntity entity = travelPlanItineraryMapper.selectById(itineraryId);
        if (entity == null) {
            return false;
        }

        TravelPlanDayEntity day = travelPlanDayMapper.selectById(entity.getTravelPlanDayId());
        if (day == null) {
            return false;
        }

        TravelPlanEntity plan = travelPlanMapper.selectById(day.getTravelPlanId());
        if (plan == null || !userId.equals(plan.getUserId())) {
            return false;
        }

        return travelPlanItineraryMapper.deleteById(itineraryId) > 0;
    }

    @Transactional
    public TravelPlanExpenseResponse createExpense(Long dayId, TravelPlanExpenseRequest request) {
        TravelPlanDayEntity day = requireDay(dayId);
        TravelPlanEntity plan = requirePlan(day.getTravelPlanId(), request.getUserId());
        ContactEntity payerContact = requireOptionalUserContact(request.getPayerContactId(), request.getUserId());

        TravelPlanExpenseEntity entity = new TravelPlanExpenseEntity();
        fillExpenseEntity(entity, plan.getId(), day.getId(), request);
        travelPlanExpenseMapper.insert(entity);
        return toExpenseResponse(travelPlanExpenseMapper.selectById(entity.getId()), payerContact);
    }

    @Transactional
    public Optional<TravelPlanExpenseResponse> updateExpense(Long expenseId, TravelPlanExpenseRequest request) {
        TravelPlanExpenseEntity entity = travelPlanExpenseMapper.selectById(expenseId);
        if (entity == null) {
            return Optional.empty();
        }

        TravelPlanDayEntity day = requireDay(entity.getTravelPlanDayId());
        TravelPlanEntity plan = requirePlan(day.getTravelPlanId(), request.getUserId());
        ContactEntity payerContact = requireOptionalUserContact(request.getPayerContactId(), request.getUserId());

        fillExpenseEntity(entity, plan.getId(), day.getId(), request);
        travelPlanExpenseMapper.updateById(entity);
        return Optional.of(toExpenseResponse(travelPlanExpenseMapper.selectById(expenseId), payerContact));
    }

    @Transactional
    public boolean deleteExpense(Long expenseId, Long userId) {
        TravelPlanExpenseEntity entity = travelPlanExpenseMapper.selectById(expenseId);
        if (entity == null) {
            return false;
        }

        TravelPlanEntity plan = travelPlanMapper.selectById(entity.getTravelPlanId());
        if (plan == null || !userId.equals(plan.getUserId())) {
            return false;
        }

        return travelPlanExpenseMapper.deleteById(expenseId) > 0;
    }

    private TravelPlanDetailResponse buildDetail(TravelPlanEntity plan) {
        List<TravelPlanCompanionEntity> companionEntities = travelPlanCompanionMapper.selectList(
            new LambdaQueryWrapper<TravelPlanCompanionEntity>()
                .eq(TravelPlanCompanionEntity::getTravelPlanId, plan.getId())
                .orderByAsc(TravelPlanCompanionEntity::getSortOrder)
                .orderByAsc(TravelPlanCompanionEntity::getId)
        );

        List<TravelPlanDayEntity> dayEntities = travelPlanDayMapper.selectList(
            new LambdaQueryWrapper<TravelPlanDayEntity>()
                .eq(TravelPlanDayEntity::getTravelPlanId, plan.getId())
                .orderByAsc(TravelPlanDayEntity::getDayIndex)
                .orderByAsc(TravelPlanDayEntity::getSortOrder)
                .orderByAsc(TravelPlanDayEntity::getId)
        );

        List<Long> dayIds = dayEntities.stream().map(TravelPlanDayEntity::getId).toList();
        List<TravelPlanItineraryEntity> itineraryEntities = dayIds.isEmpty()
            ? Collections.emptyList()
            : travelPlanItineraryMapper.selectList(new LambdaQueryWrapper<TravelPlanItineraryEntity>()
                .in(TravelPlanItineraryEntity::getTravelPlanDayId, dayIds)
                .orderByAsc(TravelPlanItineraryEntity::getStartTime)
                .orderByAsc(TravelPlanItineraryEntity::getSortOrder)
                .orderByAsc(TravelPlanItineraryEntity::getId));

        List<TravelPlanExpenseEntity> expenseEntities = dayIds.isEmpty()
            ? Collections.emptyList()
            : travelPlanExpenseMapper.selectList(new LambdaQueryWrapper<TravelPlanExpenseEntity>()
                .eq(TravelPlanExpenseEntity::getTravelPlanId, plan.getId())
                .in(TravelPlanExpenseEntity::getTravelPlanDayId, dayIds)
                .orderByAsc(TravelPlanExpenseEntity::getSortOrder)
                .orderByAsc(TravelPlanExpenseEntity::getId));

        Set<Long> contactIds = new HashSet<>();
        companionEntities.stream().map(TravelPlanCompanionEntity::getContactId).forEach(contactIds::add);
        expenseEntities.stream()
            .map(TravelPlanExpenseEntity::getPayerContactId)
            .filter(id -> id != null)
            .forEach(contactIds::add);

        Map<Long, ContactEntity> contactMap = loadContactMap(contactIds);

        List<TravelPlanCompanionResponse> companions = companionEntities.stream()
            .map(entity -> toCompanionResponse(entity, contactMap.get(entity.getContactId())))
            .toList();

        Map<Long, List<TravelPlanItineraryResponse>> itineraryMap = itineraryEntities.stream()
            .map(this::toItineraryResponse)
            .collect(Collectors.groupingBy(
                TravelPlanItineraryResponse::getTravelPlanDayId,
                HashMap::new,
                Collectors.toCollection(ArrayList::new)
            ));
        itineraryMap.values().forEach(list -> list.sort(Comparator
            .comparing(TravelPlanItineraryResponse::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TravelPlanItineraryResponse::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TravelPlanItineraryResponse::getId)));

        Map<Long, List<TravelPlanExpenseResponse>> expenseMap = expenseEntities.stream()
            .map(entity -> toExpenseResponse(entity, contactMap.get(entity.getPayerContactId())))
            .collect(Collectors.groupingBy(
                TravelPlanExpenseResponse::getTravelPlanDayId,
                HashMap::new,
                Collectors.toCollection(ArrayList::new)
            ));
        expenseMap.values().forEach(list -> list.sort(Comparator
            .comparing(TravelPlanExpenseResponse::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TravelPlanExpenseResponse::getId)));

        List<TravelPlanDayResponse> days = dayEntities.stream()
            .map(day -> toDayResponse(
                day,
                itineraryMap.getOrDefault(day.getId(), Collections.emptyList()),
                expenseMap.getOrDefault(day.getId(), Collections.emptyList())
            ))
            .toList();

        List<TravelPlanExpenseResponse> allExpenses = new ArrayList<>();
        days.forEach(day -> allExpenses.addAll(day.getExpenses()));

        TravelPlanDetailResponse detail = new TravelPlanDetailResponse();
        detail.setId(plan.getId());
        detail.setUserId(plan.getUserId());
        detail.setName(plan.getName());
        detail.setDestination(plan.getDestination());
        detail.setStartDate(plan.getStartDate());
        detail.setEndDate(plan.getEndDate());
        detail.setRemark(plan.getRemark());
        detail.setStatus(plan.getStatus());
        detail.setSortOrder(plan.getSortOrder());
        detail.setOverview(buildOverview(companions.size(), days, allExpenses));
        detail.setCompanions(companions);
        detail.setDays(days);
        detail.setExpenses(allExpenses);
        detail.setCreatedAt(plan.getCreatedAt());
        detail.setUpdatedAt(plan.getUpdatedAt());
        return detail;
    }

    private TravelPlanOverviewResponse buildOverview(
        int companionCount,
        List<TravelPlanDayResponse> days,
        List<TravelPlanExpenseResponse> expenses
    ) {
        int itineraryCount = days.stream()
            .mapToInt(day -> day.getItineraries() == null ? 0 : day.getItineraries().size())
            .sum();
        BigDecimal totalAmount = expenses.stream()
            .map(TravelPlanExpenseResponse::getAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        int travelerCount = Math.max(1, companionCount + 1);

        TravelPlanOverviewResponse overview = new TravelPlanOverviewResponse();
        overview.setCompanionCount(companionCount);
        overview.setTravelerCount(travelerCount);
        overview.setDayCount(days.size());
        overview.setItineraryCount(itineraryCount);
        overview.setExpenseCount(expenses.size());
        overview.setTotalExpenseAmount(totalAmount);
        overview.setPerPersonExpenseAmount(divideAmount(totalAmount, travelerCount));
        return overview;
    }

    private TravelPlanResponse toSummaryResponse(
        TravelPlanEntity entity,
        int companionCount,
        int dayCount,
        List<TravelPlanExpenseEntity> expenses
    ) {
        BigDecimal totalAmount = expenses.stream()
            .map(TravelPlanExpenseEntity::getAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        int travelerCount = Math.max(1, companionCount + 1);

        TravelPlanResponse response = new TravelPlanResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setName(entity.getName());
        response.setDestination(entity.getDestination());
        response.setStartDate(entity.getStartDate());
        response.setEndDate(entity.getEndDate());
        response.setRemark(entity.getRemark());
        response.setStatus(entity.getStatus());
        response.setSortOrder(entity.getSortOrder());
        response.setCompanionCount(companionCount);
        response.setTravelerCount(travelerCount);
        response.setDayCount(dayCount);
        response.setExpenseCount(expenses.size());
        response.setTotalExpenseAmount(totalAmount);
        response.setPerPersonExpenseAmount(divideAmount(totalAmount, travelerCount));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private TravelPlanCompanionResponse toCompanionResponse(TravelPlanCompanionEntity entity, ContactEntity contact) {
        TravelPlanCompanionResponse response = new TravelPlanCompanionResponse();
        response.setId(entity.getId());
        response.setTravelPlanId(entity.getTravelPlanId());
        response.setContactId(entity.getContactId());
        response.setContactName(contact == null ? null : contact.getName());
        response.setContactPhone(contact == null ? null : contact.getPhone());
        response.setContactRemark(contact == null ? null : contact.getRemark());
        response.setSortOrder(entity.getSortOrder());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private TravelPlanDayResponse toDayResponse(
        TravelPlanDayEntity entity,
        List<TravelPlanItineraryResponse> itineraries,
        List<TravelPlanExpenseResponse> expenses
    ) {
        TravelPlanDayResponse response = new TravelPlanDayResponse();
        response.setId(entity.getId());
        response.setTravelPlanId(entity.getTravelPlanId());
        response.setDayIndex(entity.getDayIndex());
        response.setTitle(entity.getTitle());
        response.setTravelDate(entity.getTravelDate());
        response.setSortOrder(entity.getSortOrder());
        response.setItineraries(new ArrayList<>(itineraries));
        response.setExpenses(new ArrayList<>(expenses));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private TravelPlanItineraryResponse toItineraryResponse(TravelPlanItineraryEntity entity) {
        TravelPlanItineraryResponse response = new TravelPlanItineraryResponse();
        response.setId(entity.getId());
        response.setTravelPlanDayId(entity.getTravelPlanDayId());
        response.setType(entity.getType());
        response.setTitle(entity.getTitle());
        response.setPoiName(entity.getPoiName());
        response.setPoiId(entity.getPoiId());
        response.setAddress(entity.getAddress());
        response.setLongitude(entity.getLongitude());
        response.setLatitude(entity.getLatitude());
        response.setStartTime(entity.getStartTime());
        response.setTransportMode(entity.getTransportMode());
        response.setDistanceMeters(entity.getDistanceMeters());
        response.setDurationSeconds(entity.getDurationSeconds());
        response.setRemark(entity.getRemark());
        response.setSortOrder(entity.getSortOrder());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private TravelPlanExpenseResponse toExpenseResponse(TravelPlanExpenseEntity entity, ContactEntity payerContact) {
        TravelPlanExpenseResponse response = new TravelPlanExpenseResponse();
        response.setId(entity.getId());
        response.setTravelPlanId(entity.getTravelPlanId());
        response.setTravelPlanDayId(entity.getTravelPlanDayId());
        response.setType(entity.getType());
        response.setTitle(entity.getTitle());
        response.setAmount(entity.getAmount());
        response.setPayerContactId(entity.getPayerContactId());
        response.setPayerContactName(payerContact == null ? null : payerContact.getName());
        response.setRemark(entity.getRemark());
        response.setSortOrder(entity.getSortOrder());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillPlanEntity(TravelPlanEntity entity, TravelPlanRequest request) {
        entity.setUserId(request.getUserId());
        entity.setName(request.getName().trim());
        entity.setDestination(trimNullable(request.getDestination()));
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setRemark(trimNullable(request.getRemark()));
        entity.setStatus(normalizePlanStatusForSave(request.getStatus()));
        entity.setSortOrder(defaultSortOrder(request.getSortOrder()));
    }

    private void fillDayEntity(TravelPlanDayEntity entity, Long planId, TravelPlanDayRequest request) {
        entity.setTravelPlanId(planId);
        entity.setDayIndex(request.getDayIndex());
        entity.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : buildDefaultDayTitle(request.getDayIndex()));
        entity.setTravelDate(request.getTravelDate());
        entity.setSortOrder(defaultSortOrder(request.getSortOrder()));
    }

    private void syncPlanDays(TravelPlanEntity plan) {
        if (plan.getStartDate() == null || plan.getEndDate() == null) {
            return;
        }

        List<TravelPlanDayEntity> existingDays = travelPlanDayMapper.selectList(new LambdaQueryWrapper<TravelPlanDayEntity>()
            .eq(TravelPlanDayEntity::getTravelPlanId, plan.getId())
            .orderByAsc(TravelPlanDayEntity::getDayIndex));

        Map<Integer, TravelPlanDayEntity> dayIndexMap = existingDays.stream()
            .collect(Collectors.toMap(TravelPlanDayEntity::getDayIndex, item -> item, (left, right) -> left, HashMap::new));

        LocalDate cursor = plan.getStartDate();
        int dayIndex = 1;
        while (!cursor.isAfter(plan.getEndDate())) {
            TravelPlanDayEntity existing = dayIndexMap.get(dayIndex);
            if (existing == null) {
                TravelPlanDayEntity entity = new TravelPlanDayEntity();
                entity.setTravelPlanId(plan.getId());
                entity.setDayIndex(dayIndex);
                entity.setTitle(buildDefaultDayTitle(dayIndex));
                entity.setTravelDate(cursor);
                entity.setSortOrder(dayIndex);
                travelPlanDayMapper.insert(entity);
            } else {
                boolean changed = false;
                if (!cursor.equals(existing.getTravelDate())) {
                    existing.setTravelDate(cursor);
                    changed = true;
                }
                if (!StringUtils.hasText(existing.getTitle())) {
                    existing.setTitle(buildDefaultDayTitle(dayIndex));
                    changed = true;
                }
                if (existing.getSortOrder() == null || existing.getSortOrder() != dayIndex) {
                    existing.setSortOrder(dayIndex);
                    changed = true;
                }
                if (changed) {
                    travelPlanDayMapper.updateById(existing);
                }
            }

            cursor = cursor.plusDays(1);
            dayIndex += 1;
        }
    }

    private void fillItineraryEntity(TravelPlanItineraryEntity entity, Long dayId, TravelPlanItineraryRequest request) {
        entity.setTravelPlanDayId(dayId);
        entity.setType(normalizeItineraryType(request.getType()));
        entity.setTitle(request.getTitle().trim());
        entity.setPoiName(trimNullable(request.getPoiName()));
        entity.setPoiId(trimNullable(request.getPoiId()));
        entity.setAddress(trimNullable(request.getAddress()));
        entity.setLongitude(request.getLongitude());
        entity.setLatitude(request.getLatitude());
        entity.setStartTime(request.getStartTime());
        entity.setTransportMode(normalizeTransportMode(entity.getType(), request.getTransportMode()));
        entity.setDistanceMeters(normalizeNonNegativeInteger(request.getDistanceMeters(), "路程距离"));
        entity.setDurationSeconds(normalizeNonNegativeInteger(request.getDurationSeconds(), "路程时长"));
        entity.setRemark(trimNullable(request.getRemark()));
        entity.setSortOrder(defaultSortOrder(request.getSortOrder()));
    }

    private void fillExpenseEntity(
        TravelPlanExpenseEntity entity,
        Long planId,
        Long dayId,
        TravelPlanExpenseRequest request
    ) {
        entity.setTravelPlanId(planId);
        entity.setTravelPlanDayId(dayId);
        entity.setType(normalizeExpenseType(request.getType()));
        entity.setTitle(request.getTitle().trim());
        entity.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entity.setPayerContactId(request.getPayerContactId());
        entity.setRemark(trimNullable(request.getRemark()));
        entity.setSortOrder(defaultSortOrder(request.getSortOrder()));
    }

    private TravelPlanEntity requirePlan(Long planId, Long userId) {
        TravelPlanEntity entity = travelPlanMapper.selectById(planId);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new IllegalArgumentException("旅行不存在");
        }
        return entity;
    }

    private TravelPlanDayEntity requireDay(Long dayId) {
        TravelPlanDayEntity entity = travelPlanDayMapper.selectById(dayId);
        if (entity == null) {
            throw new IllegalArgumentException("旅行天不存在");
        }
        return entity;
    }

    private ContactEntity requireUserContact(Long contactId, Long userId) {
        ContactEntity contact = contactMapper.selectById(contactId);
        if (contact == null || !userId.equals(contact.getUserId())) {
            throw new IllegalArgumentException("联系人不存在");
        }
        return contact;
    }

    private ContactEntity requireOptionalUserContact(Long contactId, Long userId) {
        if (contactId == null) {
            return null;
        }
        return requireUserContact(contactId, userId);
    }

    private void ensureCompanionUnique(Long planId, Long contactId, Long ignoredId) {
        TravelPlanCompanionEntity duplicate = travelPlanCompanionMapper.selectOne(new LambdaQueryWrapper<TravelPlanCompanionEntity>()
            .eq(TravelPlanCompanionEntity::getTravelPlanId, planId)
            .eq(TravelPlanCompanionEntity::getContactId, contactId)
            .ne(ignoredId != null, TravelPlanCompanionEntity::getId, ignoredId)
            .last("LIMIT 1"));
        if (duplicate != null) {
            throw new IllegalArgumentException("该联系人已关联到当前旅行");
        }
    }

    private void ensureDayIndexUnique(Long planId, Integer dayIndex, Long ignoredId) {
        TravelPlanDayEntity duplicate = travelPlanDayMapper.selectOne(new LambdaQueryWrapper<TravelPlanDayEntity>()
            .eq(TravelPlanDayEntity::getTravelPlanId, planId)
            .eq(TravelPlanDayEntity::getDayIndex, dayIndex)
            .ne(ignoredId != null, TravelPlanDayEntity::getId, ignoredId)
            .last("LIMIT 1"));
        if (duplicate != null) {
            throw new IllegalArgumentException("该天数已存在");
        }
    }

    private void validatePlanDates(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
    }

    private void validateDayDateWithinPlan(TravelPlanEntity plan, java.time.LocalDate travelDate) {
        if (travelDate == null) {
            return;
        }
        if (plan.getStartDate() != null && travelDate.isBefore(plan.getStartDate())) {
            throw new IllegalArgumentException("出行日期不能早于旅行开始日期");
        }
        if (plan.getEndDate() != null && travelDate.isAfter(plan.getEndDate())) {
            throw new IllegalArgumentException("出行日期不能晚于旅行结束日期");
        }
    }

    private String normalizeListStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ALL;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUS_ALL.equals(normalized) && !VALID_PLAN_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("旅行状态不正确");
        }
        return normalized;
    }

    private String normalizePlanStatusForSave(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!VALID_PLAN_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("旅行状态不正确");
        }
        return normalized;
    }

    private String normalizeItineraryType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("行程类型不能为空");
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!VALID_ITINERARY_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("行程类型不支持");
        }
        return normalized;
    }

    private String normalizeExpenseType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("费用类型不能为空");
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!VALID_EXPENSE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("费用类型不支持");
        }
        return normalized;
    }

    private String normalizeTransportMode(String itineraryType, String transportMode) {
        if (!StringUtils.hasText(transportMode)) {
            return null;
        }
        String normalized = transportMode.trim().toLowerCase(Locale.ROOT);
        if (!VALID_TRANSPORT_MODES.contains(normalized)) {
            throw new IllegalArgumentException("交通方式不支持");
        }
        return normalized;
    }

    private Integer normalizeNonNegativeInteger(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于0");
        }
        return value;
    }

    private boolean shouldFilterStatus(String status) {
        return StringUtils.hasText(status) && !STATUS_ALL.equals(status);
    }

    private Integer defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String trimNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildDefaultDayTitle(Integer dayIndex) {
        return "第" + dayIndex + "天";
    }

    private BigDecimal divideAmount(BigDecimal totalAmount, int divisor) {
        if (divisor <= 0) {
            return totalAmount.setScale(2, RoundingMode.HALF_UP);
        }
        return totalAmount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private Map<Long, ContactEntity> loadContactMap(Collection<Long> contactIds) {
        if (contactIds == null || contactIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return contactMapper.selectBatchIds(contactIds).stream()
            .collect(Collectors.toMap(ContactEntity::getId, contact -> contact));
    }

    private <T> Map<Long, Integer> buildCountMap(List<T> entities, java.util.function.Function<T, Long> keyGetter) {
        return entities.stream().collect(Collectors.toMap(
            keyGetter,
            entity -> 1,
            Integer::sum
        ));
    }

    private TravelPlanExpenseEntity toExpenseEntityShadow(TravelPlanExpenseResponse response) {
        TravelPlanExpenseEntity entity = new TravelPlanExpenseEntity();
        entity.setAmount(response.getAmount());
        return entity;
    }
}
