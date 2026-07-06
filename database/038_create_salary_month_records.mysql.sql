-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p bookkeeping_app < database/038_create_salary_month_records.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS salary_month_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '月度工资记录ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  salary_month DATE NOT NULL COMMENT '工资月份，统一存每月1日',
  gross_salary DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '当月税前工资',
  note VARCHAR(500) NULL COMMENT '备注说明',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_salary_month_records_user_month (user_id, salary_month),
  KEY idx_salary_month_records_month (salary_month),
  CONSTRAINT fk_salary_month_records_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度工资记录表：记录实际每月税前工资';
