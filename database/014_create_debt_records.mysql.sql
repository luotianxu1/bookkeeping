-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/014_create_debt_records.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS debt_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '债务记录ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '债务账户ID',
  funding_account_id BIGINT UNSIGNED NULL COMMENT '关联现金账户ID',
  direction ENUM('payable', 'receivable') NOT NULL COMMENT '债务方向：借入/借出',
  amount DECIMAL(18, 2) NOT NULL COMMENT '债务金额，统一存正数',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  remark VARCHAR(255) NULL COMMENT '备注',
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
  status ENUM('active', 'voided') NOT NULL DEFAULT 'active' COMMENT '状态',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_debt_records_user_time (user_id, occurred_at),
  KEY idx_debt_records_account_time (account_id, occurred_at),
  KEY idx_debt_records_account_status (account_id, status),
  KEY idx_debt_records_funding_account_status (funding_account_id, status),
  CONSTRAINT fk_debt_records_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_debt_records_account
    FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='债务记录表：记录某个债务账户下的借入和借出明细';

INSERT INTO debt_records (
  user_id,
  account_id,
  direction,
  amount,
  currency_code,
  remark,
  occurred_at,
  status
)
SELECT
  a.user_id,
  a.id,
  CASE
    WHEN LOWER(CONCAT_WS(' ', a.name, IFNULL(a.remark, ''))) LIKE '%借出%'
      OR LOWER(CONCAT_WS(' ', a.name, IFNULL(a.remark, ''))) LIKE '%待收%'
      OR LOWER(CONCAT_WS(' ', a.name, IFNULL(a.remark, ''))) LIKE '%收回%'
      OR LOWER(CONCAT_WS(' ', a.name, IFNULL(a.remark, ''))) LIKE '%垫付%'
      OR LOWER(CONCAT_WS(' ', a.name, IFNULL(a.remark, ''))) LIKE '%回款%'
      OR LOWER(CONCAT_WS(' ', a.name, IFNULL(a.remark, ''))) LIKE '%催收%'
      THEN 'receivable'
    ELSE 'payable'
  END,
  a.current_balance,
  COALESCE(a.currency_code, 'CNY'),
  a.remark,
  COALESCE(a.updated_at, a.created_at, CURRENT_TIMESTAMP(3)),
  'active'
FROM accounts a
JOIN account_types t ON t.id = a.account_type_id
LEFT JOIN debt_records dr ON dr.account_id = a.id
WHERE t.code = 'debt'
  AND a.current_balance > 0
  AND dr.id IS NULL;
