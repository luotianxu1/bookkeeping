-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/031_create_families.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS families (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '家庭ID',
  owner_user_id BIGINT UNSIGNED NOT NULL COMMENT '家庭管理员用户ID',
  invite_code VARCHAR(32) NOT NULL COMMENT '家庭邀请码',
  family_name VARCHAR(80) NOT NULL COMMENT '家庭名称',
  status ENUM('active', 'archived') NOT NULL DEFAULT 'active' COMMENT '家庭状态',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_families_owner_user (owner_user_id),
  UNIQUE KEY uk_families_invite_code (invite_code),
  KEY idx_families_status (status),
  CONSTRAINT fk_families_owner_user
    FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭表：支撑邀请码、成员绑定和解绑';
