package com.example.app.service;

import com.example.app.dto.ReminderActionRequest;
import com.example.app.dto.ReminderResponse;
import com.example.app.dto.ReminderResponse.Item;
import com.example.app.dto.ReminderSettings;
import com.example.app.mapper.ReminderMapper;
import com.example.app.mapper.ReminderMapper.Source;
import com.example.app.mapper.ReminderMapper.State;
import com.example.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReminderService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ReminderMapper mapper;
    private final Clock clock;
    private final CategoryBudgetService categoryBudgets;
    private final MonthlyBudgetService monthlyBudgets;

    @Autowired
    public ReminderService(ReminderMapper mapper, CategoryBudgetService categoryBudgets, MonthlyBudgetService monthlyBudgets) {
        this(mapper, Clock.system(ZONE), categoryBudgets, monthlyBudgets);
    }

    ReminderService(ReminderMapper mapper, Clock clock, CategoryBudgetService categoryBudgets, MonthlyBudgetService monthlyBudgets) {
        this.categoryBudgets = categoryBudgets;
        this.monthlyBudgets = monthlyBudgets;
        this.mapper = mapper;
        this.clock = clock.withZone(ZONE);
    }

    public ReminderSettings settings() {
        return settings(SecurityUtils.requireCurrentUserId());
    }

    public void saveSettings(ReminderSettings settings) {
        if (settings.advanceDays() < 0 || settings.advanceDays() > 30) {
            throw new IllegalArgumentException("提前提醒天数应为 0 到 30 天");
        }
        mapper.saveSettings(SecurityUtils.requireCurrentUserId(), settings);
    }

    private ReminderSettings settings(long userId) {
        return Optional.ofNullable(mapper.settings(userId)).orElseGet(ReminderSettings::defaults);
    }

    public ReminderResponse list() {
        long userId = SecurityUtils.requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now(clock);
        List<Item> items = candidates(userId, now);
        Map<String, State> states = new HashMap<>();
        // 控制 IN 参数数量；提醒状态仅查询本次仍有效的业务期次。
        for (int offset = 0; offset < items.size(); offset += 200) {
            List<String> keys = items.subList(offset, Math.min(offset + 200, items.size())).stream().map(Item::key).toList();
            mapper.states(userId, keys).forEach(state -> states.put(state.getReminderKey(), state));
        }
        List<Item> result = items.stream().map(item -> {
            State state = states.get(item.key());
            return new Item(item.key(), item.type(), item.title(), item.description(), item.dueAt(), item.allDay(),
                item.urgency(), item.targetPath(), state != null && state.getReadAt() != null,
                state == null || state.getSnoozedUntil() == null || !state.getSnoozedUntil().isAfter(now)
                    ? null : atZone(state.getSnoozedUntil()));
        }).sorted(Comparator.comparingInt((Item item) -> item.snoozedUntil() != null ? 2 : item.read() ? 1 : 0)
            .thenComparingInt(item -> switch (item.urgency()) { case "failed", "exceeded" -> 0; case "overdue", "warning" -> 1; case "today" -> 2; default -> 3; })
            .thenComparing(Item::dueAt).thenComparing(Item::key)).toList();
        return new ReminderResponse(result,
            result.stream().filter(item -> !item.read() && item.snoozedUntil() == null).count(),
            result.stream().filter(item -> item.snoozedUntil() != null).count());
    }

    @Transactional
    public void act(ReminderActionRequest request) {
        long userId = SecurityUtils.requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.action() == ReminderActionRequest.Action.snooze && request.snoozeMinutes() == null) {
            throw new IllegalArgumentException("请选择稍后提醒的时间");
        }
        // 客户端只能提交当前用户真实存在的提醒。已完成/改期的旧提醒忽略，批量操作不会影响新期次。
        Set<String> validKeys = candidates(userId, now).stream().map(Item::key).collect(Collectors.toSet());
        for (String key : new LinkedHashSet<>(request.keys())) {
            if (!validKeys.contains(key)) continue;
            switch (request.action()) {
                case read -> mapper.setRead(userId, key, now);
                case unread -> mapper.setRead(userId, key, null);
                case snooze -> mapper.setSnooze(userId, key, now.plusMinutes(request.snoozeMinutes()));
                case resume -> mapper.setSnooze(userId, key, null);
            }
        }
    }

    private List<Item> candidates(long userId, LocalDateTime now) {
        ReminderSettings settings = settings(userId);
        LocalDate today = now.toLocalDate();
        LocalDateTime end = today.plusDays(settings.advanceDays() + 1L).atStartOfDay();
        List<Item> items = new ArrayList<>();
        if (settings.todoEnabled()) mapper.todos(userId, end).forEach(source -> add(items, "todo", source, false, now));
        if (settings.anniversaryEnabled()) {
            for (Source source : mapper.anniversaries(userId)) {
                if (source.getDueAt() == null) continue;
                LocalDate original = source.getDueAt().toLocalDate();
                // withYear 与现有日历一致：2 月 29 日在非闰年按 2 月 28 日提醒。
                LocalDate next = original.withYear(Math.max(today.getYear(), original.getYear()));
                if (next.isBefore(today)) next = original.withYear(today.getYear() + 1);
                if (next.atStartOfDay().isBefore(end)) {
                    source.setDueAt(next.atStartOfDay());
                    add(items, "anniversary", source, true, now);
                }
            }
        }
        if (settings.renewalEnabled()) mapper.renewals(userId, end).forEach(source -> add(items, "renewal", source, true, now));
        if (settings.liabilityEnabled()) mapper.liabilities(userId, end).forEach(source -> add(items, "liability", source, true, now));
        if (settings.investmentEnabled()) mapper.investments(userId, end).forEach(source -> add(items, "investment", source, true, now));
        if (settings.reimbursementEnabled()) mapper.reimbursements(userId, end).forEach(source -> add(items, "reimbursement", source, true, now));
        if (settings.termDepositEnabled()) mapper.termDeposits(userId, end).forEach(source -> add(items, "term_deposit", source, true, now));
        if (settings.budgetEnabled()) {
            LocalDate month = today.withDayOfMonth(1);
            categoryBudgets.list(userId, month).forEach(b -> addBudget(items, "category:" + b.id(), b.categoryName(), month,
                b.amount(), b.usedAmount(), b.currencyCode()));
            monthlyBudgets.getCurrent(userId, month).ifPresent(b -> addBudget(items, "monthly:" + b.getId(), "月总预算", month,
                b.getAmount(), b.getUsedAmount(), b.getCurrencyCode()));
        }
        return items;
    }

    private void addBudget(List<Item> items, String id, String name, LocalDate month,
        java.math.BigDecimal amount, java.math.BigDecimal used, String currency) {
        if (used.compareTo(amount.multiply(new java.math.BigDecimal("0.8"))) < 0) return;
        boolean exceeded = used.compareTo(amount) >= 0;
        int stage = exceeded ? 100 : 80;
        // 每月每个预算每个阈值独立记忆已读；100% 替换当前的 80% 提醒。
        items.add(new Item("budget:" + id + ":" + month + ":" + stage, "budget", name + (exceeded ? "预算已用完" : "预算即将用完"),
            "已用 " + currency + " " + used.toPlainString() + " / " + amount.toPlainString() + " · "
                + (exceeded ? "超出 " + used.subtract(amount).toPlainString() : "剩余 " + amount.subtract(used).toPlainString()),
            atZone(month.atStartOfDay()), true, exceeded ? "exceeded" : "warning", "/finance/budgets", false, null));
    }

    private void add(List<Item> items, String type, Source source, boolean allDay, LocalDateTime now) {
        if (source.getDueAt() == null) return;
        LocalDateTime due = allDay ? source.getDueAt().toLocalDate().atStartOfDay() : source.getDueAt();
        boolean overdue = allDay ? due.toLocalDate().isBefore(now.toLocalDate()) : due.isBefore(now);
        String urgency = source.isFailed() ? "failed" : overdue ? "overdue"
            : due.toLocalDate().equals(now.toLocalDate()) ? "today" : "upcoming";
        String description = switch (type) {
            case "todo" -> "待办截止";
            case "anniversary" -> "一年一度的重要日子";
            case "renewal" -> source.isFailed() ? "本期自动扣款失败，请检查扣款账户和分类" : "本期固定支出";
            case "term_deposit" -> "定期存款到期，待确认取回或续存";
            case "liability" -> "本期待还账单";
            case "reimbursement" -> (overdue ? "报销逾期未到账" : "报销待到账")
                + (source.getDetail() == null || source.getDetail().isBlank() ? "" : " · " + source.getDetail());
            default -> overdue ? "计划日期已过，请查看定投执行情况" : "下一次定投计划";
        };
        if (source.getAmount() != null) {
            description += " · " + Objects.requireNonNullElse(source.getCurrencyCode(), "CNY") + " " + source.getAmount().toPlainString();
        }
        // 同一期读状态不会因刷新重复生成；改期、下一期和扣款失败会产生新的提醒。
        String key = type + ":" + source.getId() + ":" + due + (source.isFailed() ? ":failed" : "");
        items.add(new Item(key, type, source.getTitle(), description, atZone(due), allDay, urgency,
            source.getTargetPath(), false, null));
    }

    private OffsetDateTime atZone(LocalDateTime value) {
        return value.atZone(ZONE).toOffsetDateTime();
    }
}
