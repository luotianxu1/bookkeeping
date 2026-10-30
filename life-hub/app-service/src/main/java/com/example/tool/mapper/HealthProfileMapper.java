package com.example.tool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.tool.entity.HealthProfileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthProfileMapper extends BaseMapper<HealthProfileEntity> {
}
