-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/008_migrate_gold_account_type.mysql.sql

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
  'gold',
  '黄金',
  'asset',
  'debit',
  1,
  0,
  1,
  30,
  '实物黄金、积存金、纸黄金等黄金账户'
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

UPDATE account_types
SET
  remark = '基金、股票等投资账户',
  sort_order = 20,
  updated_at = CURRENT_TIMESTAMP(3)
WHERE code = 'investment';

UPDATE accounts a
JOIN account_types current_type ON current_type.id = a.account_type_id
JOIN account_types gold_type ON gold_type.code = 'gold'
SET
  a.account_type_id = gold_type.id,
  a.updated_at = CURRENT_TIMESTAMP(3)
WHERE current_type.code = 'investment'
  AND a.name = '黄金账户';
