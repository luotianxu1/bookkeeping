-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/033_create_travel_plan_tables.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS travel_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '旅行计划ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  name VARCHAR(80) NOT NULL COMMENT '旅行名称',
  destination VARCHAR(120) NULL COMMENT '目的地',
  start_date DATE NULL COMMENT '开始日期',
  end_date DATE NULL COMMENT '结束日期',
  remark VARCHAR(500) NULL COMMENT '备注',
  status ENUM('active', 'completed', 'cancelled') NOT NULL DEFAULT 'active' COMMENT '状态：active进行中，completed已完成，cancelled已取消',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_travel_plans_user_status_start (user_id, status, start_date),
  KEY idx_travel_plans_user_updated (user_id, updated_at),
  CONSTRAINT fk_travel_plans_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旅行计划表：记录旅行基础信息';

CREATE TABLE IF NOT EXISTS travel_plan_companions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '同行人关联ID',
  travel_plan_id BIGINT UNSIGNED NOT NULL COMMENT '旅行计划ID',
  contact_id BIGINT UNSIGNED NOT NULL COMMENT '联系人ID',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_travel_plan_companions_plan_contact (travel_plan_id, contact_id),
  KEY idx_travel_plan_companions_contact (contact_id),
  CONSTRAINT fk_travel_plan_companions_plan
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans (id) ON DELETE CASCADE,
  CONSTRAINT fk_travel_plan_companions_contact
    FOREIGN KEY (contact_id) REFERENCES contacts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旅行同行人表：关联工具联系人';

CREATE TABLE IF NOT EXISTS travel_plan_days (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '旅行天ID',
  travel_plan_id BIGINT UNSIGNED NOT NULL COMMENT '旅行计划ID',
  day_index INT NOT NULL COMMENT '第几天，从1开始',
  title VARCHAR(80) NULL COMMENT '当天标题',
  travel_date DATE NULL COMMENT '出行日期',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_travel_plan_days_plan_day_index (travel_plan_id, day_index),
  KEY idx_travel_plan_days_plan_date (travel_plan_id, travel_date),
  CONSTRAINT fk_travel_plan_days_plan
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旅行天表：按天管理路线和费用';

CREATE TABLE IF NOT EXISTS travel_plan_itineraries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '行程项ID',
  travel_plan_day_id BIGINT UNSIGNED NOT NULL COMMENT '旅行天ID',
  type ENUM('transport', 'scenic', 'dining', 'accommodation') NOT NULL COMMENT '行程类型',
  title VARCHAR(120) NOT NULL COMMENT '行程标题',
  poi_name VARCHAR(120) NULL COMMENT '地点名称',
  poi_id VARCHAR(64) NULL COMMENT '地点POI ID',
  address VARCHAR(255) NULL COMMENT '地址',
  longitude DECIMAL(10, 6) NULL COMMENT '经度',
  latitude DECIMAL(10, 6) NULL COMMENT '纬度',
  start_time TIME NULL COMMENT '开始时间',
  transport_mode ENUM('driving', 'walking', 'riding') NULL COMMENT '交通方式：驾车、步行、骑车',
  distance_meters INT NULL COMMENT '路程距离，单位米',
  duration_seconds INT NULL COMMENT '预计时长，单位秒',
  remark VARCHAR(500) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_travel_plan_itineraries_day_time (travel_plan_day_id, start_time),
  KEY idx_travel_plan_itineraries_day_type (travel_plan_day_id, type),
  CONSTRAINT fk_travel_plan_itineraries_day
    FOREIGN KEY (travel_plan_day_id) REFERENCES travel_plan_days (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旅行行程项表：交通、景区、餐饮、住宿等';

CREATE TABLE IF NOT EXISTS travel_plan_expenses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '费用记录ID',
  travel_plan_id BIGINT UNSIGNED NOT NULL COMMENT '旅行计划ID',
  travel_plan_day_id BIGINT UNSIGNED NOT NULL COMMENT '旅行天ID',
  type ENUM('transport', 'scenic', 'dining', 'accommodation', 'other') NOT NULL COMMENT '费用类型',
  title VARCHAR(120) NOT NULL COMMENT '费用名称',
  amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '费用金额',
  payer_contact_id BIGINT UNSIGNED NULL COMMENT '付款联系人ID',
  remark VARCHAR(500) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_travel_plan_expenses_plan_day (travel_plan_id, travel_plan_day_id),
  KEY idx_travel_plan_expenses_plan_type (travel_plan_id, type),
  KEY idx_travel_plan_expenses_payer (payer_contact_id),
  CONSTRAINT fk_travel_plan_expenses_plan
    FOREIGN KEY (travel_plan_id) REFERENCES travel_plans (id) ON DELETE CASCADE,
  CONSTRAINT fk_travel_plan_expenses_day
    FOREIGN KEY (travel_plan_day_id) REFERENCES travel_plan_days (id) ON DELETE CASCADE,
  CONSTRAINT fk_travel_plan_expenses_payer
    FOREIGN KEY (payer_contact_id) REFERENCES contacts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旅行费用表：按天记录交通、门票、餐饮、住宿等费用';
