-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/031_create_weight_records.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS weight_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  record_date DATE NOT NULL COMMENT '记录日期',
  weight DECIMAL(5,1) NOT NULL COMMENT '体重(kg)',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_weight_records_user_date (user_id, record_date),
  CONSTRAINT fk_weight_records_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体重打卡表：每人每日一条体重记录';
