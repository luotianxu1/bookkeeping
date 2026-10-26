package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.WeightRecordRequest;
import com.example.tool.dto.WeightRecordResponse;
import com.example.tool.entity.HealthProfileEntity;
import com.example.tool.entity.WeightRecordEntity;
import com.example.tool.mapper.HealthProfileMapper;
import com.example.tool.mapper.WeightRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class WeightRecordService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;
    private static final int GENDER_MALE = 1;

    private final WeightRecordMapper weightRecordMapper;
    private final HealthProfileMapper healthProfileMapper;

    public WeightRecordService(WeightRecordMapper weightRecordMapper, HealthProfileMapper healthProfileMapper) {
        this.weightRecordMapper = weightRecordMapper;
        this.healthProfileMapper = healthProfileMapper;
    }

    // 每人每日一条：同日再次打卡即覆盖更新
    public WeightRecordResponse save(WeightRecordRequest request) {
        WeightRecordEntity existing = selectByUserAndDate(request.getUserId(), request.getRecordDate());
        WeightRecordEntity entity = existing == null ? new WeightRecordEntity() : existing;
        entity.setUserId(request.getUserId());
        entity.setRecordDate(request.getRecordDate());
        entity.setWeight(request.getWeight());
        entity.setBodyFatRate(computeBodyFatRate(selectProfile(request.getUserId()), request.getWeight()));
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);

        if (existing == null) {
            try {
                weightRecordMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                // 并发打卡兜底：唯一键冲突时改为覆盖更新
                WeightRecordEntity concurrent = selectByUserAndDate(request.getUserId(), request.getRecordDate());
                if (concurrent == null) {
                    throw e;
                }
                entity.setId(concurrent.getId());
                weightRecordMapper.updateById(entity);
            }
        } else {
            weightRecordMapper.updateById(entity);
        }

        return toResponse(weightRecordMapper.selectById(entity.getId()));
    }

    public List<WeightRecordResponse> listRecent(Long userId, Integer limit) {
        int normalizedLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        return weightRecordMapper.selectList(new LambdaQueryWrapper<WeightRecordEntity>()
                .eq(userId != null, WeightRecordEntity::getUserId, userId)
                .orderByDesc(WeightRecordEntity::getRecordDate)
                .orderByDesc(WeightRecordEntity::getId)
                .last("LIMIT " + normalizedLimit))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private WeightRecordEntity selectByUserAndDate(Long userId, LocalDate recordDate) {
        return weightRecordMapper.selectOne(new LambdaQueryWrapper<WeightRecordEntity>()
            .eq(WeightRecordEntity::getUserId, userId)
            .eq(WeightRecordEntity::getRecordDate, recordDate)
            .last("LIMIT 1"));
    }

    private HealthProfileEntity selectProfile(Long userId) {
        if (userId == null) {
            return null;
        }
        return healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfileEntity>()
            .eq(HealthProfileEntity::getUserId, userId)
            .last("LIMIT 1"));
    }

    // Deurenberg 公式：体脂率 = 1.2×BMI + 0.23×年龄 − 10.8×性别(男1女0) − 5.4；未设档案时不记录
    private BigDecimal computeBodyFatRate(HealthProfileEntity profile, BigDecimal weight) {
        if (profile == null) {
            return null;
        }
        double heightM = profile.getHeightCm().doubleValue() / 100;
        double bmi = weight.doubleValue() / (heightM * heightM);
        double sexFactor = profile.getGender() != null && profile.getGender() == GENDER_MALE ? 1 : 0;
        double rate = 1.2 * bmi + 0.23 * profile.getAge() - 10.8 * sexFactor - 5.4;
        return BigDecimal.valueOf(Math.round(rate * 10) / 10.0);
    }

    private WeightRecordResponse toResponse(WeightRecordEntity entity) {
        WeightRecordResponse response = new WeightRecordResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setRecordDate(entity.getRecordDate());
        response.setWeight(entity.getWeight().doubleValue());
        response.setBodyFatRate(entity.getBodyFatRate() == null ? null : entity.getBodyFatRate().doubleValue());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
