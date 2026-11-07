package com.example.app.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.example.app.controller.ReminderController;
import com.example.app.dto.ReminderActionRequest;
import com.example.app.dto.ReminderSettings;
import com.example.app.mapper.ReminderMapper;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.security.SecurityUtils;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReminderServiceTest {
    private JdbcTemplate jdbc;
    private ReminderMapper mapper;
    private ReminderService service;
    private MockMvc mvc;
    private final CategoryBudgetService categoryBudgets = org.mockito.Mockito.mock(CategoryBudgetService.class);
    private final MonthlyBudgetService monthlyBudgets = org.mockito.Mockito.mock(MonthlyBudgetService.class);

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO users VALUES(1),(2)");
        // 执行真实迁移，仅去除 H2 不支持的 MySQL 字符集/存储引擎语法。
        String migration = Files.readString(Path.of("../../database/045_create_reminder_preferences.mysql.sql"))
            .replace("USE bookkeeping_app;", "").replace("SET NAMES utf8mb4;", "")
            .replace("CHARACTER SET ascii COLLATE ascii_bin", "")
            .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        var populator = new ResourceDatabasePopulator(new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)));
        populator.execute(source);
        populator.execute(source); // 可重复执行，不重置已有状态。
        jdbc.execute("ALTER TABLE reminder_settings ADD budget_enabled BOOLEAN DEFAULT TRUE");
        jdbc.execute("CREATE TABLE todo_items(id BIGINT PRIMARY KEY,user_id BIGINT,title VARCHAR(120),remark VARCHAR(500),due_at TIMESTAMP,status VARCHAR(20))");
        jdbc.execute("CREATE TABLE anniversaries(id BIGINT PRIMARY KEY,user_id BIGINT,title VARCHAR(120),remark VARCHAR(500),anniversary_date DATE)");
        jdbc.execute("CREATE TABLE renewal_subscriptions(id BIGINT PRIMARY KEY,user_id BIGINT,name VARCHAR(80),next_billing_date DATE,amount DECIMAL(18,2),currency_code VARCHAR(3),last_charge_status VARCHAR(20),last_charge_message VARCHAR(255),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE accounts(id BIGINT PRIMARY KEY,user_id BIGINT,name VARCHAR(80),status VARCHAR(20),loan_settled_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE liability_records(id BIGINT PRIMARY KEY,user_id BIGINT,account_id BIGINT,occurred_at TIMESTAMP,amount DECIMAL(18,2),currency_code VARCHAR(3),status VARCHAR(20),repayment_status VARCHAR(20),repayment_type VARCHAR(20))");
        jdbc.execute("CREATE TABLE investment_positions(id BIGINT PRIMARY KEY,user_id BIGINT,status VARCHAR(20))");
        jdbc.execute("CREATE TABLE investment_products(id BIGINT PRIMARY KEY,name VARCHAR(80))");
        jdbc.execute("CREATE TABLE investment_auto_invest_plans(id BIGINT PRIMARY KEY,user_id BIGINT,account_id BIGINT,position_id BIGINT,product_id BIGINT,next_execute_date DATE,amount DECIMAL(18,2),currency_code VARCHAR(3),status VARCHAR(20))");
        var configuration = new MybatisConfiguration();
        configuration.addMapper(ReminderMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ReminderMapper.class);
        at("2026-11-07T10:00:00+08:00");
        asUser(1L);
    }

    private void at(String value) {
        service = new ReminderService(mapper, Clock.fixed(OffsetDateTime.parse(value).toInstant(), ZoneOffset.UTC), categoryBudgets, monthlyBudgets);
        mvc = MockMvcBuilders.standaloneSetup(new ReminderController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void asUser(long userId) {
        var request = new MockHttpServletRequest();
        request.setAttribute(SecurityUtils.USER_ID_ATTRIBUTE, userId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void todo(long id, long userId, String due, String status) {
        jdbc.update("INSERT INTO todo_items VALUES(?,?,?,NULL,?,?)", id, userId, "待办" + id, due, status);
    }

    private void action(String key, ReminderActionRequest.Action action, Integer minutes) {
        service.act(new ReminderActionRequest(List.of(key), action, minutes));
    }

    @Test void budgetThresholdStagesKeepReadStateAndRespectToggleAndMonth() {
        var month = LocalDate.of(2026,11,1);
        java.util.function.Function<String, com.example.app.dto.CategoryBudgetResponse> budget = used ->
            new com.example.app.dto.CategoryBudgetResponse(7L, month, 2L, "午餐", new java.math.BigDecimal("100"),
                new java.math.BigDecimal(used), new java.math.BigDecimal("100").subtract(new java.math.BigDecimal(used)), new java.math.BigDecimal(used), "CNY");
        org.mockito.Mockito.when(categoryBudgets.list(1L,month)).thenReturn(List.of(budget.apply("79.99")));
        assertTrue(service.list().items().isEmpty());
        org.mockito.Mockito.when(categoryBudgets.list(1L,month)).thenReturn(List.of(budget.apply("80")));
        var first = service.list().items().get(0); assertEquals("warning", first.urgency());
        action(first.key(), ReminderActionRequest.Action.read, null); assertEquals(0,service.list().unreadCount());
        org.mockito.Mockito.when(categoryBudgets.list(1L,month)).thenReturn(List.of(budget.apply("100")));
        var second = service.list().items().get(0); assertEquals("exceeded", second.urgency());
        assertNotEquals(first.key(),second.key()); assertEquals(1,service.list().unreadCount());
        action(second.key(), ReminderActionRequest.Action.read, null); assertEquals(0,service.list().unreadCount());
        service.saveSettings(new ReminderSettings(3,true,true,true,true,true,false)); assertTrue(service.list().items().isEmpty());
        service.saveSettings(ReminderSettings.defaults()); assertEquals(0,service.list().unreadCount());
        asUser(2L); assertTrue(service.list().items().isEmpty());
        asUser(1L); at("2026-12-01T10:00:00+08:00"); assertTrue(service.list().items().isEmpty());
    }

    @AfterEach void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test void windowUsesShanghaiDatesAndIncludesOverdueButExcludesCompletedAndOtherUsers() {
        todo(1, 1, "2025-01-01 10:00:00", "pending");
        todo(2, 1, "2026-11-10 23:59:59", "pending");
        todo(3, 1, "2026-11-11 00:00:00", "pending");
        todo(4, 1, "2026-11-07 09:00:00", "completed");
        todo(5, 2, "2026-11-07 09:00:00", "pending");
        assertEquals(List.of("待办1", "待办2"), service.list().items().stream().map(item -> item.title()).toList());
        assertEquals(2, service.list().unreadCount());
        assertEquals("overdue", service.list().items().get(0).urgency());
        assertEquals(ZoneOffset.ofHours(8), service.list().items().get(0).dueAt().getOffset());
    }

    @Test void anniversariesHandleNewYearLeapDayAndFutureFirstOccurrence() {
        jdbc.update("INSERT INTO anniversaries VALUES(1,1,'元旦',NULL,'2020-01-01'),(2,1,'闰日',NULL,'2024-02-29'),(3,1,'未来',NULL,'2029-01-01')");
        at("2026-12-30T12:00:00+08:00");
        assertEquals(1, service.list().items().size());
        var first = service.list().items().get(0);
        assertEquals(LocalDate.of(2027, 1, 1), first.dueAt().toLocalDate());
        action(first.key(), ReminderActionRequest.Action.read, null);
        assertEquals(0, service.list().unreadCount());
        at("2027-12-30T12:00:00+08:00");
        assertEquals(1, service.list().unreadCount());
        at("2027-02-27T12:00:00+08:00");
        assertEquals(LocalDate.of(2027, 2, 28), service.list().items().get(0).dueAt().toLocalDate());
    }

    @Test void readSnoozeExpiryRescheduleAndCompletionArePersistedPerOccurrence() {
        todo(1, 1, "2026-11-07 12:00:00", "pending");
        String key = service.list().items().get(0).key();
        action(key, ReminderActionRequest.Action.read, null);
        action(key, ReminderActionRequest.Action.read, null);
        assertEquals(0, service.list().unreadCount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reminder_states", Integer.class));
        action(key, ReminderActionRequest.Action.snooze, 60);
        assertEquals(1, service.list().snoozedCount());
        assertEquals(0, service.list().unreadCount());
        at("2026-11-07T11:00:00+08:00");
        assertEquals(1, service.list().unreadCount());
        assertEquals(0, service.list().snoozedCount());
        action(key, ReminderActionRequest.Action.read, null);
        jdbc.update("UPDATE todo_items SET due_at='2026-11-07 13:00:00' WHERE id=1");
        assertEquals(1, service.list().unreadCount());
        action(key, ReminderActionRequest.Action.read, null); // 旧页面不会读掉新期次。
        assertEquals(1, service.list().unreadCount());
        jdbc.update("UPDATE todo_items SET status='completed' WHERE id=1");
        assertTrue(service.list().items().isEmpty());
    }

    @Test void settingsAndActionsAreIsolatedAndSettingsSurviveReload() {
        todo(1, 1, "2026-11-08 12:00:00", "pending");
        todo(2, 2, "2026-11-07 12:00:00", "pending");
        String key = service.list().items().get(0).key();
        asUser(2);
        action(key, ReminderActionRequest.Action.read, null);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reminder_states", Integer.class));
        service.saveSettings(new ReminderSettings(0, false, true, true, true, true, true));
        assertFalse(service.settings().todoEnabled());
        assertTrue(service.list().items().isEmpty());
        asUser(1);
        assertEquals(ReminderSettings.defaults(), service.settings());
        assertEquals(1, service.list().unreadCount());
        service.saveSettings(new ReminderSettings(0, true, true, true, true, true, true));
        assertTrue(service.list().items().isEmpty());
    }

    @Test void recurringPaymentsFailuresAndInactiveSourcesRefreshFromBusinessData() {
        jdbc.update("INSERT INTO renewal_subscriptions VALUES(1,1,'会员','2026-11-07',20,'CNY','idle',NULL,'active'),(2,1,'暂停','2026-11-07',20,'CNY','idle',NULL,'paused')");
        var first = service.list().items().get(0);
        assertEquals("today", first.urgency());
        action(first.key(), ReminderActionRequest.Action.read, null);
        jdbc.update("UPDATE renewal_subscriptions SET last_charge_status='failed' WHERE id=1");
        assertEquals(1, service.list().unreadCount());
        assertEquals("failed", service.list().items().get(0).urgency());
        jdbc.update("UPDATE renewal_subscriptions SET last_charge_status='success',next_billing_date='2026-12-07' WHERE id=1");
        assertTrue(service.list().items().isEmpty());
        at("2026-12-06T12:00:00+08:00");
        assertEquals(1, service.list().unreadCount());
    }

    @Test void loansAndInvestmentsOnlyIncludeActiveUnsettledSources() {
        jdbc.update("INSERT INTO accounts VALUES(1,1,'房贷','active',NULL),(2,1,'基金','active',NULL)");
        jdbc.update("INSERT INTO liability_records VALUES(1,1,1,'2026-11-07 00:00:00',1000,'CNY','active','pending','monthly')");
        jdbc.update("INSERT INTO investment_positions VALUES(1,1,'active')");
        jdbc.update("INSERT INTO investment_products VALUES(1,'指数基金')");
        jdbc.update("INSERT INTO investment_auto_invest_plans VALUES(1,1,2,1,1,'2026-11-07',100,'CNY','active')");
        assertEquals(2, service.list().items().size());
        assertTrue(service.list().items().stream().allMatch(item -> item.urgency().equals("today")));
        jdbc.update("UPDATE liability_records SET repayment_status='paid'");
        jdbc.update("UPDATE investment_auto_invest_plans SET status='paused'");
        assertTrue(service.list().items().isEmpty());
        jdbc.update("UPDATE liability_records SET repayment_status='pending'");
        jdbc.update("UPDATE accounts SET loan_settled_at=CURRENT_TIMESTAMP WHERE id=1");
        jdbc.update("UPDATE investment_auto_invest_plans SET status='active'");
        jdbc.update("UPDATE investment_positions SET status='closed'");
        assertTrue(service.list().items().isEmpty());
    }

    @Test void httpIgnoresFamilyUserParametersAndRejectsInvalidActions() throws Exception {
        todo(1, 1, "2026-11-07 12:00:00", "pending");
        todo(2, 2, "2026-11-07 12:00:00", "pending");
        mvc.perform(get("/api/tools/reminders").param("userId", "2").requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, 1L))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("待办1"));
        mvc.perform(post("/api/tools/reminders/actions").requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, 1L)
            .contentType("application/json").content("{\"keys\":[\"x\"],\"action\":\"snooze\",\"snoozeMinutes\":-1}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tools/reminders/actions").requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, 1L)
            .contentType("application/json").content("{\"keys\":[],\"action\":\"read\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tools/reminders")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/tools/reminders/settings").requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, 1L)
            .contentType("application/json").content("{\"advanceDays\":31}"))
            .andExpect(status().isBadRequest());
    }
}
