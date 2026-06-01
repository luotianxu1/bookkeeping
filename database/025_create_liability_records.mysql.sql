-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/025_create_liability_records.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

INSERT INTO account_types (
  code,
  name,
  category,
  balance_direction,
  include_in_net_worth_default,
  allow_overdraft,
  is_system,
  sort_order,
  remark
) VALUES (
  'liability',
  '负债',
  'liability',
  'credit',
  1,
  0,
  1,
  60,
  '房贷、车贷等长期待还负债账户'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  category = VALUES(category),
  balance_direction = VALUES(balance_direction),
  include_in_net_worth_default = VALUES(include_in_net_worth_default),
  allow_overdraft = VALUES(allow_overdraft),
  is_system = VALUES(is_system),
  sort_order = VALUES(sort_order),
  status = 'active',
  remark = VALUES(remark);

CREATE TABLE IF NOT EXISTS liability_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '负债记录ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '负债账户ID',
  amount DECIMAL(18, 2) NOT NULL COMMENT '负债金额',
  installment_total_periods INT UNSIGNED NULL COMMENT '分期总期数',
  installment_current_period INT UNSIGNED NULL COMMENT '当前期数',
  repayment_status ENUM('pending', 'paid') NOT NULL DEFAULT 'pending' COMMENT '还款状态',
  repayment_type ENUM('monthly', 'prepayment') NOT NULL DEFAULT 'monthly' COMMENT '还款类型',
  paid_at DATETIME(3) NULL COMMENT '还款时间',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  remark VARCHAR(255) NULL COMMENT '备注',
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
  status ENUM('active', 'voided') NOT NULL DEFAULT 'active' COMMENT '状态',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_liability_records_user_time (user_id, occurred_at),
  KEY idx_liability_records_account_time (account_id, occurred_at),
  KEY idx_liability_records_account_status (account_id, status),
  CONSTRAINT fk_liability_records_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_liability_records_account
    FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB COMMENT='负债记录表：记录房贷、车贷等非联系人负债流水';
