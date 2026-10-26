-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/032_create_health_profiles.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

-- 健康档案：计算体脂率所需的身体数据（一人一条，Deurenberg 公式需要性别、年龄、身高）
CREATE TABLE IF NOT EXISTS health_profiles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '档案ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  gender TINYINT NOT NULL COMMENT '性别：1男 2女',
  age INT NOT NULL COMMENT '年龄',
  height_cm DECIMAL(5,1) NOT NULL COMMENT '身高(cm)',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_health_profiles_user (user_id),
  CONSTRAINT fk_health_profiles_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康档案表：计算体脂率所需的身体数据';

-- 体重打卡记录体脂率（按打卡当时的身体数据快照计算）
ALTER TABLE weight_records
  ADD COLUMN body_fat_rate DECIMAL(4,1) NULL COMMENT '体脂率(%)' AFTER weight;
