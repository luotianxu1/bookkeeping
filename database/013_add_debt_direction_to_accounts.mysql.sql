-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/013_add_debt_direction_to_accounts.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

ALTER TABLE accounts
  ADD COLUMN debt_direction ENUM('payable', 'receivable') NULL COMMENT '债务方向：借入/借出' AFTER contact_id,
  ADD KEY idx_accounts_debt_direction (debt_direction);

UPDATE accounts
SET debt_direction = CASE
  WHEN LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%借出%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%待收%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%收回%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%垫付%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%回款%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%催收%'
    THEN 'receivable'
  WHEN LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%借入%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%待还%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%归还%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%还款%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%尾款%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%分期%'
    OR LOWER(CONCAT_WS(' ', name, IFNULL(remark, ''))) LIKE '%催还%'
    THEN 'payable'
  ELSE 'payable'
END
WHERE account_type_id IN (
  SELECT id
  FROM account_types
  WHERE code = 'debt'
)
AND debt_direction IS NULL;
