-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/032_create_investment_fixed_expenses.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS investment_fixed_expenses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '固定支出ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  name VARCHAR(120) NOT NULL COMMENT '固定支出名称',
  amount DECIMAL(18, 2) NOT NULL COMMENT '每月固定支出金额',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  status ENUM('active', 'deleted') NOT NULL DEFAULT 'active' COMMENT '状态',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_investment_fixed_expenses_user_sort (user_id, status, sort_order, id),
  CONSTRAINT fk_investment_fixed_expenses_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT chk_investment_fixed_expenses_amount_positive
    CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='攒股收息固定支出表';
