-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/024_create_anniversaries.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS anniversaries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '纪念日ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  title VARCHAR(120) NOT NULL COMMENT '纪念日名称',
  anniversary_date DATE NOT NULL COMMENT '纪念日期',
  remark VARCHAR(500) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_anniversaries_user_title_date (user_id, title, anniversary_date),
  KEY idx_anniversaries_user_date (user_id, anniversary_date),
  KEY idx_anniversaries_user_sort (user_id, sort_order),
  CONSTRAINT fk_anniversaries_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纪念日表：记录每年循环提醒的重要日期';
