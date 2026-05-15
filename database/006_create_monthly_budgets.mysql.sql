-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/006_create_monthly_budgets.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS monthly_budgets (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预算ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  budget_month DATE NOT NULL COMMENT '预算月份，固定存每月1号，例如 2026-05-01',
  amount DECIMAL(18, 2) NOT NULL COMMENT '月预算金额',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  status ENUM('active', 'deleted') NOT NULL DEFAULT 'active' COMMENT '状态：active生效，deleted已删除',
  active_key TINYINT GENERATED ALWAYS AS (IF(status = 'active', 1, NULL)) STORED COMMENT '唯一索引用状态键，仅生效预算参与唯一约束',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_monthly_budgets_user_month_active (user_id, budget_month, active_key),
  KEY idx_monthly_budgets_user_month (user_id, budget_month),
  KEY idx_monthly_budgets_user_status_month (user_id, status, budget_month),
  CONSTRAINT fk_monthly_budgets_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT chk_monthly_budgets_amount_positive
    CHECK (amount > 0),
  CONSTRAINT chk_monthly_budgets_month_first_day
    CHECK (DAYOFMONTH(budget_month) = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月预算表：每个用户每个月只能有一个生效预算';

