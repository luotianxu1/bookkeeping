-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/011_migrate_debt_account_type.mysql.sql

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
  'debt',
  '债务',
  'liability',
  'credit',
  1,
  0,
  1,
  50,
  '统一记录个人债务账户'
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

UPDATE accounts a
JOIN account_types current_type ON current_type.id = a.account_type_id
JOIN account_types debt_type ON debt_type.code = 'debt'
SET
  a.account_type_id = debt_type.id,
  a.icon = CASE
    WHEN a.icon IN ('loan_receivable', 'loan_payable') THEN 'debt'
    ELSE a.icon
  END,
  a.updated_at = CURRENT_TIMESTAMP(3)
WHERE current_type.code IN ('loan_receivable', 'loan_payable');

UPDATE account_types
SET
  status = 'disabled',
  updated_at = CURRENT_TIMESTAMP(3)
WHERE code IN ('loan_receivable', 'loan_payable');
