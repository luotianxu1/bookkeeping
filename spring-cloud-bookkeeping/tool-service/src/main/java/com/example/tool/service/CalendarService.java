package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.tool.dto.CalendarAnniversaryNoteResponse;
import com.example.tool.dto.CalendarDayResponse;
import com.example.tool.dto.CalendarMonthResponse;
import com.example.tool.dto.CalendarOverviewResponse;
import com.example.tool.entity.AnniversaryEntity;
import com.example.tool.mapper.AnniversaryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);
    private static final String VIEW_MONTH = "month";
    private static final String VIEW_YEAR = "year";
    private static final Set<String> VALID_VIEWS = Set.of(VIEW_MONTH, VIEW_YEAR);
    private static final Duration HOLIDAY_API_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration HOLIDAY_CACHE_TTL = Duration.ofHours(12);
    private static final String HOLIDAY_API_USER_AGENT = "bookkeeping-calendar/1.0";
    private static final String JIE_JIA_RI_HOLIDAYS_API = "https://api.jiejiariapi.com/v1/holidays/%d";
    private static final String AILCC_YEAR_API = "https://holiday.ailcc.com/api/holiday/year/%d";
    private static final String TIMOR_YEAR_API = "https://timor.tech/api/holiday/year/%d/";

    private final AnniversaryMapper anniversaryMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ConcurrentMap<Integer, HolidayYearCache> holidayCache = new ConcurrentHashMap<>();

    public CalendarService(
        AnniversaryMapper anniversaryMapper,
        ObjectMapper objectMapper
    ) {
        this.anniversaryMapper = anniversaryMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HOLIDAY_API_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public CalendarOverviewResponse overview(Long userId, String view, String anchor, String selectedDate) {
        String normalizedView = normalizeView(view);
        LocalDate today = LocalDate.now();
        List<AnniversaryEntity> anniversaries = listAnniversaries(userId);

        if (VIEW_YEAR.equals(normalizedView)) {
            int targetYear = resolveYear(anchor, today);
            Map<LocalDate, CalendarDayMark> dayMarks = buildOfficialDayMarks(targetYear);
            return buildYearOverview(anniversaries, targetYear, selectedDate, today, dayMarks);
        }
        YearMonth targetMonth = resolveMonth(anchor, today);
        Map<LocalDate, CalendarDayMark> dayMarks = buildOfficialDayMarks(targetMonth.getYear());
        return buildMonthOverview(anniversaries, targetMonth, selectedDate, today, dayMarks);
    }

    private CalendarOverviewResponse buildMonthOverview(
        List<AnniversaryEntity> anniversaries,
        YearMonth targetMonth,
        String selectedDate,
        LocalDate today,
        Map<LocalDate, CalendarDayMark> dayMarks
    ) {
        LocalDate resolvedSelectedDate = resolveMonthSelectedDate(selectedDate, targetMonth, today);
        Map<LocalDate, Integer> anniversaryCounts = buildAnniversaryCounts(anniversaries, targetMonth.getYear());

        CalendarOverviewResponse response = new CalendarOverviewResponse();
        response.setView(VIEW_MONTH);
        response.setAnchor(targetMonth.toString());
        response.setSelectedDate(resolvedSelectedDate.toString());
        response.setTitle(targetMonth.getMonthValue() + "月");
        response.setSubtitle(targetMonth.getYear() + "年" + targetMonth.getMonthValue() + "月");
        response.setDays(buildMonthDays(targetMonth, resolvedSelectedDate, today, anniversaryCounts, dayMarks));
        response.setMonths(List.of());
        response.setAnniversaries(buildMonthAnniversaryNotes(anniversaries, targetMonth, today));
        return response;
    }

    private CalendarOverviewResponse buildYearOverview(
        List<AnniversaryEntity> anniversaries,
        int targetYear,
        String selectedDate,
        LocalDate today,
        Map<LocalDate, CalendarDayMark> dayMarks
    ) {
        LocalDate resolvedSelectedDate = resolveYearSelectedDate(selectedDate, targetYear, today);
        Map<LocalDate, Integer> anniversaryCounts = buildAnniversaryCounts(anniversaries, targetYear);

        List<CalendarMonthResponse> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(targetYear, month);
            CalendarMonthResponse item = new CalendarMonthResponse();
            item.setKey(yearMonth.toString());
            item.setLabel(month + "月");
            item.setDaysInMonth(yearMonth.lengthOfMonth());
            item.setCurrent(today.getYear() == targetYear && today.getMonthValue() == month);
            item.setSelected(resolvedSelectedDate.getYear() == targetYear && resolvedSelectedDate.getMonthValue() == month);
            item.setDays(buildMonthDays(yearMonth, resolvedSelectedDate, today, anniversaryCounts, dayMarks));
            months.add(item);
        }

        CalendarOverviewResponse response = new CalendarOverviewResponse();
        response.setView(VIEW_YEAR);
        response.setAnchor(String.valueOf(targetYear));
        response.setSelectedDate(resolvedSelectedDate.toString());
        response.setTitle(String.valueOf(targetYear));
        response.setSubtitle(targetYear + "年");
        response.setDays(List.of());
        response.setMonths(months);
        response.setAnniversaries(List.of());
        return response;
    }

    private List<AnniversaryEntity> listAnniversaries(Long userId) {
        LambdaQueryWrapper<AnniversaryEntity> wrapper = new LambdaQueryWrapper<AnniversaryEntity>()
            .eq(AnniversaryEntity::getUserId, userId)
            .orderByAsc(AnniversaryEntity::getSortOrder)
            .orderByAsc(AnniversaryEntity::getId);
        return anniversaryMapper.selectList(wrapper);
    }

    private List<CalendarDayResponse> buildMonthDays(
        YearMonth month,
        LocalDate selectedDate,
        LocalDate today,
        Map<LocalDate, Integer> anniversaryCounts,
        Map<LocalDate, CalendarDayMark> dayMarks
    ) {
        List<CalendarDayResponse> days = new ArrayList<>();
        LocalDate firstDay = month.atDay(1);
        int offset = firstDay.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        LocalDate cursor = firstDay.minusDays(Math.floorMod(offset, 7));

        for (int index = 0; index < 42; index++) {
            CalendarDayResponse item = new CalendarDayResponse();
            item.setDate(cursor.toString());
            item.setDay(cursor.getDayOfMonth());
            item.setCurrentMonth(cursor.getMonthValue() == month.getMonthValue());
            item.setWeekend(isWeekend(cursor));
            item.setToday(cursor.equals(today));
            item.setSelected(cursor.equals(selectedDate));
            item.setAnniversaryCount(anniversaryCounts.getOrDefault(cursor, 0));
            CalendarDayMark dayMark = dayMarks.get(cursor);
            item.setHolidayLabel(dayMark == null ? null : dayMark.holidayLabel());
            item.setWorkdayLabel(dayMark == null ? null : dayMark.workdayLabel());
            days.add(item);
            cursor = cursor.plusDays(1);
        }

        return days;
    }

    private Map<LocalDate, Integer> buildAnniversaryCounts(List<AnniversaryEntity> anniversaries, int year) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (AnniversaryEntity entity : anniversaries) {
            LocalDate occurrence = clampDate(entity.getAnniversaryDate(), year);
            counts.merge(occurrence, 1, Integer::sum);
        }
        return counts;
    }

    private List<CalendarAnniversaryNoteResponse> buildMonthAnniversaryNotes(
        List<AnniversaryEntity> anniversaries,
        YearMonth month,
        LocalDate today
    ) {
        return anniversaries.stream()
            .map(entity -> Map.entry(entity, clampDate(entity.getAnniversaryDate(), month.getYear())))
            .filter(entry -> YearMonth.from(entry.getValue()).equals(month))
            .sorted(Comparator
                .comparing((Map.Entry<AnniversaryEntity, LocalDate> entry) -> entry.getValue())
                .thenComparing(entry -> Optional.ofNullable(entry.getKey().getSortOrder()).orElse(0))
                .thenComparing(entry -> entry.getKey().getId()))
            .map(entry -> toAnniversaryNote(entry.getKey(), entry.getValue(), today))
            .toList();
    }

    private CalendarAnniversaryNoteResponse toAnniversaryNote(
        AnniversaryEntity entity,
        LocalDate occurrenceDate,
        LocalDate today
    ) {
        long daysOffset = ChronoUnit.DAYS.between(today, occurrenceDate);
        CalendarAnniversaryNoteResponse response = new CalendarAnniversaryNoteResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setOccurrenceDate(occurrenceDate.toString());
        response.setRemark(entity.getRemark());
        response.setDaysOffset(daysOffset);
        response.setStatusLabel(buildStatusLabel(daysOffset));
        return response;
    }

    private String buildStatusLabel(long daysOffset) {
        if (daysOffset == 0) {
            return "就是今天";
        }
        if (daysOffset > 0) {
            return "还有 " + daysOffset + " 天";
        }
        return "已过 " + Math.abs(daysOffset) + " 天";
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private Map<LocalDate, CalendarDayMark> buildOfficialDayMarks(int year) {
        HolidayYearCache cached = holidayCache.get(year);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.dayMarks();
        }

        Map<LocalDate, CalendarDayMark> dayMarks = fetchDayMarks(year);
        if (!dayMarks.isEmpty()) {
            Map<LocalDate, CalendarDayMark> immutableDayMarks = Map.copyOf(dayMarks);
            holidayCache.put(year, new HolidayYearCache(immutableDayMarks, now.plus(HOLIDAY_CACHE_TTL)));
            return immutableDayMarks;
        }

        if (cached != null) {
            return cached.dayMarks();
        }
        return Map.of();
    }

    private Map<LocalDate, CalendarDayMark> fetchDayMarks(int year) {
        for (String endpoint : List.of(
            JIE_JIA_RI_HOLIDAYS_API.formatted(year),
            AILCC_YEAR_API.formatted(year),
            TIMOR_YEAR_API.formatted(year)
        )) {
            Map<LocalDate, CalendarDayMark> dayMarks = fetchDayMarksFromApi(year, endpoint);
            if (!dayMarks.isEmpty()) {
                return dayMarks;
            }
        }
        return Map.of();
    }

    private Map<LocalDate, CalendarDayMark> fetchDayMarksFromApi(int year, String endpoint) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .GET()
            .timeout(HOLIDAY_API_TIMEOUT)
            .header("Accept", "application/json")
            .header("User-Agent", HOLIDAY_API_USER_AGENT)
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Holiday API returned non-200 status. year={}, endpoint={}, status={}", year, endpoint, response.statusCode());
                return Map.of();
            }
            return parseDayMarks(year, endpoint, response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Holiday API request failed. year={}, endpoint={}", year, endpoint, exception);
            return Map.of();
        }
    }

    private Map<LocalDate, CalendarDayMark> parseDayMarks(int year, String endpoint, String body) {
        try {
            JsonNode rootNode = objectMapper.readTree(body);
            Map<LocalDate, CalendarDayMark> jieJiaRiMarks = parseJieJiaRiDayMarks(year, rootNode);
            if (!jieJiaRiMarks.isEmpty()) {
                return jieJiaRiMarks;
            }

            JsonNode holidayNode = rootNode.path("holiday");
            if (!holidayNode.isObject()) {
                return Map.of();
            }

            TreeMap<LocalDate, String> holidayDays = new TreeMap<>();
            Map<LocalDate, CalendarDayMark> dayMarks = new HashMap<>();
            holidayNode.fields().forEachRemaining(entry -> {
                JsonNode item = entry.getValue();
                LocalDate dayDate = parseDate(item.path("date").asText(null));
                if (dayDate == null || dayDate.getYear() != year) {
                    return;
                }

                if (item.path("holiday").asBoolean(false)) {
                    String holidayName = normalizeHolidayName(item.path("name").asText(""));
                    if (!StringUtils.hasText(holidayName)) {
                        return;
                    }
                    holidayDays.put(dayDate, holidayName);
                    return;
                }

                if (isWorkdayItem(item)) {
                    dayMarks.put(dayDate, new CalendarDayMark(null, "班"));
                }
            });

            if (holidayDays.isEmpty() && dayMarks.isEmpty()) {
                return Map.of();
            }

            LocalDate previousDate = null;
            for (Map.Entry<LocalDate, String> entry : holidayDays.entrySet()) {
                LocalDate currentDate = entry.getKey();
                boolean continuedHoliday = previousDate != null && previousDate.plusDays(1).equals(currentDate);
                dayMarks.put(currentDate, new CalendarDayMark(continuedHoliday ? "休" : entry.getValue(), null));
                previousDate = currentDate;
            }
            return dayMarks;
        } catch (IOException exception) {
            log.warn("Failed to parse holiday API response. year={}, endpoint={}", year, endpoint, exception);
            return Map.of();
        }
    }

    private boolean isWorkdayItem(JsonNode item) {
        String name = item.path("name").asText("");
        return name.contains("补班");
    }

    private Map<LocalDate, CalendarDayMark> parseJieJiaRiDayMarks(int year, JsonNode rootNode) {
        if (!rootNode.isObject() || rootNode.has("holiday") || rootNode.has("code")) {
            return Map.of();
        }

        TreeMap<LocalDate, String> holidayDays = new TreeMap<>();
        Map<LocalDate, CalendarDayMark> dayMarks = new HashMap<>();
        rootNode.fields().forEachRemaining(entry -> {
            JsonNode item = entry.getValue();
            LocalDate dayDate = parseDate(item.path("date").asText(null));
            if (dayDate == null || dayDate.getYear() != year) {
                return;
            }

            String name = normalizeHolidayName(item.path("name").asText(""));
            if (!StringUtils.hasText(name)) {
                return;
            }

            boolean offDay = item.path("isOffDay").asBoolean(false);
            if (offDay) {
                holidayDays.put(dayDate, name);
                return;
            }

            if (isWeekend(dayDate)) {
                dayMarks.put(dayDate, new CalendarDayMark(null, "班"));
            }
        });

        if (holidayDays.isEmpty() && dayMarks.isEmpty()) {
            return Map.of();
        }

        LocalDate previousDate = null;
        for (Map.Entry<LocalDate, String> entry : holidayDays.entrySet()) {
            LocalDate currentDate = entry.getKey();
            boolean continuedHoliday = previousDate != null && previousDate.plusDays(1).equals(currentDate);
            dayMarks.put(currentDate, new CalendarDayMark(continuedHoliday ? "休" : entry.getValue(), null));
            previousDate = currentDate;
        }
        return dayMarks;
    }

    private String normalizeHolidayName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
            .trim()
            .replace("（休）", "")
            .replace("(休)", "")
            .replace("后补班", "")
            .replace("前补班", "");
    }

    private YearMonth resolveMonth(String anchor, LocalDate today) {
        if (!StringUtils.hasText(anchor)) {
            return YearMonth.from(today);
        }
        try {
            return YearMonth.parse(anchor.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日历月份格式不正确");
        }
    }

    private int resolveYear(String anchor, LocalDate today) {
        if (!StringUtils.hasText(anchor)) {
            return today.getYear();
        }
        try {
            return Integer.parseInt(anchor.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("日历年份格式不正确");
        }
    }

    private LocalDate resolveMonthSelectedDate(String selectedDate, YearMonth month, LocalDate today) {
        LocalDate parsed = parseDate(selectedDate);
        if (parsed != null && YearMonth.from(parsed).equals(month)) {
            return parsed;
        }
        if (YearMonth.from(today).equals(month)) {
            return today;
        }
        return month.atDay(1);
    }

    private LocalDate resolveYearSelectedDate(String selectedDate, int year, LocalDate today) {
        LocalDate parsed = parseDate(selectedDate);
        if (parsed != null && parsed.getYear() == year) {
            return parsed;
        }
        if (today.getYear() == year) {
            return today;
        }
        return LocalDate.of(year, 1, 1);
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private LocalDate clampDate(LocalDate source, int year) {
        YearMonth targetMonth = YearMonth.of(year, source.getMonthValue());
        return LocalDate.of(year, source.getMonthValue(), Math.min(source.getDayOfMonth(), targetMonth.lengthOfMonth()));
    }

    private String normalizeView(String view) {
        if (!StringUtils.hasText(view)) {
            return VIEW_MONTH;
        }
        String normalized = view.trim().toLowerCase(Locale.ROOT);
        if (!VALID_VIEWS.contains(normalized)) {
            throw new IllegalArgumentException("日历视图类型不支持");
        }
        return normalized;
    }

    private record HolidayYearCache(
        Map<LocalDate, CalendarDayMark> dayMarks,
        Instant expiresAt
    ) {
    }

    private record CalendarDayMark(
        String holidayLabel,
        String workdayLabel
    ) {
    }
}
