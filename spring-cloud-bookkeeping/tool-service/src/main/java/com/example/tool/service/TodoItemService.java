package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.TodoItemRequest;
import com.example.tool.dto.TodoItemResponse;
import com.example.tool.dto.TodoItemStatusRequest;
import com.example.tool.entity.TodoItemEntity;
import com.example.tool.mapper.TodoItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class TodoItemService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_ALL = "all";
    private static final String DUE_SCOPE_ALL = "all";
    private static final String DUE_SCOPE_TODAY = "today";
    private static final Set<String> VALID_STATUSES = Set.of(STATUS_PENDING, STATUS_COMPLETED);

    private final TodoItemMapper todoItemMapper;

    public TodoItemService(TodoItemMapper todoItemMapper) {
        this.todoItemMapper = todoItemMapper;
    }

    public List<TodoItemResponse> list(Long userId, String status, String dueScope, String keyword) {
        String normalizedStatus = normalizeListStatus(status);
        String normalizedDueScope = normalizeDueScope(dueScope);
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<TodoItemEntity> wrapper = new LambdaQueryWrapper<TodoItemEntity>()
            .eq(userId != null, TodoItemEntity::getUserId, userId)
            .eq(shouldFilterStatus(normalizedStatus), TodoItemEntity::getStatus, normalizedStatus)
            .ge(DUE_SCOPE_TODAY.equals(normalizedDueScope), TodoItemEntity::getDueAt, today.atStartOfDay())
            .le(DUE_SCOPE_TODAY.equals(normalizedDueScope), TodoItemEntity::getDueAt, LocalDateTime.of(today, LocalTime.MAX))
            .and(StringUtils.hasText(keyword), query -> query
                .like(TodoItemEntity::getTitle, keyword.trim())
                .or()
                .like(TodoItemEntity::getRemark, keyword.trim()))
            .orderByAsc(TodoItemEntity::getSortOrder)
            .orderByAsc(TodoItemEntity::getDueAt)
            .orderByDesc(TodoItemEntity::getId);

        return todoItemMapper.selectList(wrapper).stream()
            .map(this::toResponse)
            .toList();
    }

    public Optional<TodoItemResponse> getById(Long id) {
        return Optional.ofNullable(todoItemMapper.selectById(id)).map(this::toResponse);
    }

    public TodoItemResponse create(TodoItemRequest request) {
        TodoItemEntity entity = new TodoItemEntity();
        fillEntity(entity, request);
        todoItemMapper.insert(entity);
        return toResponse(todoItemMapper.selectById(entity.getId()));
    }

    public Optional<TodoItemResponse> update(Long id, TodoItemRequest request) {
        TodoItemEntity entity = todoItemMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }

        fillEntity(entity, request);
        todoItemMapper.updateById(entity);
        return Optional.of(toResponse(todoItemMapper.selectById(id)));
    }

    public Optional<TodoItemResponse> updateStatus(Long id, TodoItemStatusRequest request) {
        TodoItemEntity entity = todoItemMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }

        String nextStatus = normalizeEntityStatus(request.getStatus());
        entity.setStatus(nextStatus);
        entity.setCompletedAt(STATUS_COMPLETED.equals(nextStatus) ? LocalDateTime.now() : null);
        todoItemMapper.updateById(entity);

        return Optional.of(toResponse(todoItemMapper.selectById(id)));
    }

    public boolean delete(Long id, Long userId) {
        TodoItemEntity entity = todoItemMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }
        return todoItemMapper.deleteById(id) > 0;
    }

    private boolean shouldFilterStatus(String status) {
        return StringUtils.hasText(status) && !STATUS_ALL.equals(status);
    }

    private String normalizeListStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ALL;
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUS_ALL.equals(normalized) && !VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("待办事项状态不正确");
        }
        return normalized;
    }

    private String normalizeDueScope(String dueScope) {
        if (!StringUtils.hasText(dueScope)) {
            return DUE_SCOPE_ALL;
        }

        String normalized = dueScope.trim().toLowerCase(Locale.ROOT);
        if (!DUE_SCOPE_ALL.equals(normalized) && !DUE_SCOPE_TODAY.equals(normalized)) {
            throw new IllegalArgumentException("截止时间筛选不支持");
        }
        return normalized;
    }

    private String normalizeEntityStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_PENDING;
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("待办事项状态不正确");
        }
        return normalized;
    }

    private void fillEntity(TodoItemEntity entity, TodoItemRequest request) {
        entity.setUserId(request.getUserId());
        entity.setTitle(request.getTitle().trim());
        entity.setDueAt(request.getDueAt());
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());

        String status = normalizeEntityStatus(request.getStatus());
        entity.setStatus(status);
        if (STATUS_COMPLETED.equals(status)) {
            entity.setCompletedAt(entity.getCompletedAt() == null ? LocalDateTime.now() : entity.getCompletedAt());
        } else {
            entity.setCompletedAt(null);
        }
    }

    private TodoItemResponse toResponse(TodoItemEntity entity) {
        TodoItemResponse response = new TodoItemResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setTitle(entity.getTitle());
        response.setDueAt(entity.getDueAt());
        response.setRemark(entity.getRemark());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setCompletedAt(entity.getCompletedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
