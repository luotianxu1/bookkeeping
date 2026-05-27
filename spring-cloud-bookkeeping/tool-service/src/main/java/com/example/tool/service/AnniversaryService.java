package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.AnniversaryRequest;
import com.example.tool.dto.AnniversaryResponse;
import com.example.tool.entity.AnniversaryEntity;
import com.example.tool.mapper.AnniversaryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class AnniversaryService {

    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MONTH = "month";
    private static final String SCOPE_EXPIRED = "expired";
    private static final Set<String> VALID_SCOPES = Set.of(SCOPE_ALL, SCOPE_MONTH, SCOPE_EXPIRED);

    private final AnniversaryMapper anniversaryMapper;

    public AnniversaryService(AnniversaryMapper anniversaryMapper) {
        this.anniversaryMapper = anniversaryMapper;
    }

    public List<AnniversaryResponse> list(Long userId, String scope, String keyword) {
        String normalizedScope = normalizeScope(scope);
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<AnniversaryEntity> wrapper = new LambdaQueryWrapper<AnniversaryEntity>()
            .eq(userId != null, AnniversaryEntity::getUserId, userId)
            .and(StringUtils.hasText(keyword), query -> query
                .like(AnniversaryEntity::getTitle, keyword.trim())
                .or()
                .like(AnniversaryEntity::getRemark, keyword.trim()))
            .orderByAsc(AnniversaryEntity::getSortOrder)
            .orderByAsc(AnniversaryEntity::getId);

        return anniversaryMapper.selectList(wrapper).stream()
            .filter(entity -> matchesScope(entity, normalizedScope, today))
            .sorted(buildComparator(normalizedScope, today))
            .map(this::toResponse)
            .toList();
    }

    public Optional<AnniversaryResponse> getById(Long id) {
        return Optional.ofNullable(anniversaryMapper.selectById(id)).map(this::toResponse);
    }

    public AnniversaryResponse create(AnniversaryRequest request) {
        validateDuplicate(request.getUserId(), request.getTitle(), request.getAnniversaryDate(), null);

        AnniversaryEntity entity = new AnniversaryEntity();
        fillEntity(entity, request);
        anniversaryMapper.insert(entity);

        return toResponse(anniversaryMapper.selectById(entity.getId()));
    }

    public Optional<AnniversaryResponse> update(Long id, AnniversaryRequest request) {
        AnniversaryEntity entity = anniversaryMapper.selectById(id);
        if (entity == null || !request.getUserId().equals(entity.getUserId())) {
            return Optional.empty();
        }

        validateDuplicate(request.getUserId(), request.getTitle(), request.getAnniversaryDate(), id);
        fillEntity(entity, request);
        anniversaryMapper.updateById(entity);

        return Optional.of(toResponse(anniversaryMapper.selectById(id)));
    }

    public boolean delete(Long id, Long userId) {
        AnniversaryEntity entity = anniversaryMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            return false;
        }

        return anniversaryMapper.deleteById(id) > 0;
    }

    private void validateDuplicate(Long userId, String title, LocalDate anniversaryDate, Long ignoredId) {
        LambdaQueryWrapper<AnniversaryEntity> wrapper = new LambdaQueryWrapper<AnniversaryEntity>()
            .eq(AnniversaryEntity::getUserId, userId)
            .eq(AnniversaryEntity::getTitle, title.trim())
            .eq(AnniversaryEntity::getAnniversaryDate, anniversaryDate)
            .ne(ignoredId != null, AnniversaryEntity::getId, ignoredId)
            .last("LIMIT 1");

        if (anniversaryMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("该纪念日已存在");
        }
    }

    private void fillEntity(AnniversaryEntity entity, AnniversaryRequest request) {
        entity.setUserId(request.getUserId());
        entity.setTitle(request.getTitle().trim());
        entity.setAnniversaryDate(request.getAnniversaryDate());
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private AnniversaryResponse toResponse(AnniversaryEntity entity) {
        AnniversaryResponse response = new AnniversaryResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setTitle(entity.getTitle());
        response.setAnniversaryDate(entity.getAnniversaryDate());
        response.setRemark(entity.getRemark());
        response.setSortOrder(entity.getSortOrder());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private boolean matchesScope(AnniversaryEntity entity, String scope, LocalDate today) {
        if (SCOPE_MONTH.equals(scope)) {
            return entity.getAnniversaryDate().getMonthValue() == today.getMonthValue();
        }
        if (SCOPE_EXPIRED.equals(scope)) {
            return isExpiredThisYear(entity.getAnniversaryDate(), today);
        }
        return true;
    }

    private Comparator<AnniversaryEntity> buildComparator(String scope, LocalDate today) {
        if (SCOPE_MONTH.equals(scope)) {
            return Comparator
                .comparing((AnniversaryEntity entity) -> getOccurrenceThisYear(entity.getAnniversaryDate(), today))
                .thenComparing(entity -> Optional.ofNullable(entity.getSortOrder()).orElse(0))
                .thenComparing(AnniversaryEntity::getId);
        }
        if (SCOPE_EXPIRED.equals(scope)) {
            return Comparator
                .comparing((AnniversaryEntity entity) -> getOccurrenceThisYear(entity.getAnniversaryDate(), today), Comparator.reverseOrder())
                .thenComparing(entity -> Optional.ofNullable(entity.getSortOrder()).orElse(0))
                .thenComparing(AnniversaryEntity::getId);
        }

        return Comparator
            .comparing((AnniversaryEntity entity) -> getNextOccurrence(entity.getAnniversaryDate(), today))
            .thenComparing(entity -> Optional.ofNullable(entity.getSortOrder()).orElse(0))
            .thenComparing(AnniversaryEntity::getId);
    }

    private boolean isExpiredThisYear(LocalDate anniversaryDate, LocalDate today) {
        return getOccurrenceThisYear(anniversaryDate, today).isBefore(today);
    }

    private LocalDate getNextOccurrence(LocalDate anniversaryDate, LocalDate today) {
        LocalDate occurrenceThisYear = getOccurrenceThisYear(anniversaryDate, today);
        if (!occurrenceThisYear.isBefore(today)) {
            return occurrenceThisYear;
        }
        return clampDate(anniversaryDate, today.getYear() + 1);
    }

    private LocalDate getOccurrenceThisYear(LocalDate anniversaryDate, LocalDate today) {
        return clampDate(anniversaryDate, today.getYear());
    }

    private LocalDate clampDate(LocalDate source, int year) {
        YearMonth yearMonth = YearMonth.of(year, source.getMonthValue());
        return LocalDate.of(year, source.getMonthValue(), Math.min(source.getDayOfMonth(), yearMonth.lengthOfMonth()));
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return SCOPE_ALL;
        }

        String normalized = scope.trim().toLowerCase(Locale.ROOT);
        if (!VALID_SCOPES.contains(normalized)) {
            throw new IllegalArgumentException("纪念日筛选条件不支持");
        }
        return normalized;
    }
}
