-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/010_create_contacts.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS contacts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '联系人ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  name VARCHAR(80) NOT NULL COMMENT '联系人姓名',
  phone VARCHAR(32) NULL COMMENT '手机号',
  remark VARCHAR(500) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status ENUM('active', 'archived') NOT NULL DEFAULT 'active' COMMENT '状态：active正常，archived已归档',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_contacts_user_phone (user_id, phone),
  KEY idx_contacts_user_status_sort (user_id, status, sort_order),
  KEY idx_contacts_user_name (user_id, name),
  CONSTRAINT fk_contacts_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='联系人表：支撑联系人管理与债务联系人选择';
