package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.HealthProfileRequest;
import com.example.tool.dto.HealthProfileResponse;
import com.example.tool.entity.HealthProfileEntity;
import com.example.tool.mapper.HealthProfileMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class HealthProfileService {

    private final HealthProfileMapper healthProfileMapper;

    public HealthProfileService(HealthProfileMapper healthProfileMapper) {
        this.healthProfileMapper = healthProfileMapper;
    }

    public Optional<HealthProfileResponse> get(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(selectByUserId(userId)).map(this::toResponse);
    }

    public HealthProfileResponse save(HealthProfileRequest request) {
        HealthProfileEntity entity = selectByUserId(request.getUserId());
        boolean creating = entity == null;
        if (creating) {
            entity = new HealthProfileEntity();
            entity.setUserId(request.getUserId());
        }
        entity.setGender(request.getGender());
        entity.setAge(request.getAge());
        entity.setHeightCm(request.getHeightCm());

        if (creating) {
            healthProfileMapper.insert(entity);
        } else {
            healthProfileMapper.updateById(entity);
        }

        return toResponse(selectByUserId(request.getUserId()));
    }

    private HealthProfileEntity selectByUserId(Long userId) {
        return healthProfileMapper.selectOne(new LambdaQueryWrapper<HealthProfileEntity>()
            .eq(HealthProfileEntity::getUserId, userId)
            .last("LIMIT 1"));
    }

    private HealthProfileResponse toResponse(HealthProfileEntity entity) {
        HealthProfileResponse response = new HealthProfileResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setGender(entity.getGender());
        response.setAge(entity.getAge());
        response.setHeightCm(entity.getHeightCm().doubleValue());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
