package com.example.app.service;

import com.example.tool.service.CalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A股市场日历：统一判断某日是否休市。
 *
 * <p>判定顺序：周末恒休市（调休补班的周末也不是交易日）→ 节假日 API
 * （{@link CalendarService}，按年缓存 12h + 失败降级，三数据源自动切换，2027 年起无需人工维护）
 * → yml 静态兜底清单 {@code finance.investment.market-closed-dates}（API 不可用时保底，
 * 覆盖年份有限，仅作降级用）。</p>
 */
@Service
public class MarketCalendarService {

    private static final Logger log = LoggerFactory.getLogger(MarketCalendarService.class);

    private final CalendarService calendarService;
    private final Set<LocalDate> staticClosedDates;

    public MarketCalendarService(
        CalendarService calendarService,
        @Value("${finance.investment.market-closed-dates:}") String marketClosedDates
    ) {
        this.calendarService = calendarService;
        this.staticClosedDates = Arrays.stream(marketClosedDates.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(LocalDate::parse)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** 是否休市（周末恒休市；工作日按节假日 API 判定，API 不可用时回退静态清单） */
    public boolean isClosed(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return true;
        }
        if (staticClosedDates.contains(date)) {
            return true;
        }
        var dayMarks = calendarService.officialDayMarks(date.getYear());
        if (dayMarks.isEmpty()) {
            // 节假日数据源全部不可用：仅靠周末 + 静态兜底判定，明确留痕便于发现数据源故障
            log.warn("节假日数据不可用，休市判定回退静态清单：date={}", date);
            return false;
        }
        var mark = dayMarks.get(date);
        // holidayLabel 非空 = 法定休假日；无标记的工作日 = 正常交易日（调休补班日不适用 A股）
        return mark != null && mark.holidayLabel() != null;
    }

    public boolean isTradingDay(LocalDate date) {
        return !isClosed(date);
    }
}
