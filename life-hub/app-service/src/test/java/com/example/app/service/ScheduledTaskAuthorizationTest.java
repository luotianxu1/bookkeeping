package com.example.app.service;

import com.example.app.controller.ScheduledTaskController;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ScheduledTaskAuthorizationTest {
    @Test void onlyCurrentActiveDatabaseAdminsMayReadOrExecuteGlobalTasks() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, status VARCHAR(20), role_name VARCHAR(30))");
        jdbc.update("INSERT INTO users VALUES(1,'active','user'),(2,'active','super_admin'),(3,'disabled','admin'),(4,'active','admin')");
        var tasks = mock(ScheduledTaskStatusService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ScheduledTaskController(tasks, new AdminAccessService(jdbc)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        String base = "/api/finance/scheduled-tasks/today";
        String execute = base + "/salary-settlement/scheduled-08:10/execute";
        try {
            mvc.perform(post(execute)).andExpect(status().isUnauthorized());
            for (long id : new long[]{1, 3}) {
                mvc.perform(get(base).requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, id)).andExpect(status().isForbidden());
                mvc.perform(post(execute).requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, id)
                    .param("roleName", "super_admin").param("userId", "2")).andExpect(status().isForbidden());
            }
            verifyNoInteractions(tasks);
            for (long id : new long[]{2, 4}) {
                mvc.perform(get(base).requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, id)).andExpect(status().isOk());
                mvc.perform(post(execute).requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, id)).andExpect(status().isOk());
            }
            verify(tasks, times(2)).executeTodayTask("salary-settlement", "scheduled-08:10");
            clearInvocations(tasks);
            jdbc.update("UPDATE users SET role_name='user' WHERE id=2");
            mvc.perform(post(execute).requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, 2L)).andExpect(status().isForbidden());
            jdbc.execute("DROP TABLE users");
            mvc.perform(post(execute).requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, 4L)).andExpect(status().isServiceUnavailable());
            verifyNoInteractions(tasks);
        } finally { jdbc.execute("DROP ALL OBJECTS"); }
    }
}
