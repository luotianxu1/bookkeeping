package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.CalendarAnniversaryNoteResponse;
import com.example.tool.dto.CalendarDayResponse;
import com.example.tool.dto.CalendarMonthResponse;
import com.example.tool.dto.CalendarOverviewResponse;
import com.example.tool.entity.AnniversaryEntity;
import com.example.tool.mapper.AnniversaryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
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

@Service
public class CalendarService {

    private static final String VIEW_MONTH = "month";
    private static final String VIEW_YEAR = "year";
    private static final Set<String> VALID_VIEWS = Set.of(VIEW_MONTH, VIEW_YEAR);

    private final AnniversaryMapper anniversaryMapper;

    public CalendarService(AnniversaryMapper anniversaryMapper) {
        this.anniversaryMapper = anniversaryMapper;
    }

    public CalendarOverviewResponse overview(Long userId, String view, String anchor, String selectedDate) {
        String normalizedView = normalizeView(view);
        LocalDate today = LocalDate.now();
        List<AnniversaryEntity> anniversaries = listAnniversaries(userId);

        if (VIEW_YEAR.equals(normalizedView)) {
            return buildYearOverview(anniversaries, anchor, selectedDate, today);
        }
        return buildMonthOverview(anniversaries, anchor, selectedDate, today);
    }

    private CalendarOverviewResponse buildMonthOverview(
        List<AnniversaryEntity> anniversaries,
        String anchor,
        String selectedDate,
        LocalDate today
    ) {
        YearMonth targetMonth = resolveMonth(anchor, today);
        LocalDate resolvedSelectedDate = resolveMonthSelectedDate(selectedDate, targetMonth, today);
        Map<LocalDate, Integer> anniversaryCounts = buildAnniversaryCounts(anniversaries, targetMonth.getYear());

        CalendarOverviewResponse response = new CalendarOverviewResponse();
        response.setView(VIEW_MONTH);
        response.setAnchor(targetMonth.toString());
        response.setSelectedDate(resolvedSelectedDate.toString());
        response.setTitle(targetMonth.getMonthValue() + "月");
        response.setSubtitle(targetMonth.getYear() + "年" + targetMonth.getMonthValue() + "月");
        response.setDays(buildMonthDays(targetMonth, resolvedSelectedDate, today, anniversaryCounts));
        response.setMonths(List.of());
        response.setAnniversaries(buildMonthAnniversaryNotes(anniversaries, targetMonth, today));
        return response;
    }

    private CalendarOverviewResponse buildYearOverview(
        List<AnniversaryEntity> anniversaries,
        String anchor,
        String selectedDate,
        LocalDate today
    ) {
        int targetYear = resolveYear(anchor, today);
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
            item.setDays(buildMonthDays(yearMonth, resolvedSelectedDate, today, anniversaryCounts));
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
        Map<LocalDate, Integer> anniversaryCounts
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
}
