-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/015_add_funding_account_id_to_debt_records.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

ALTER TABLE debt_records
  ADD COLUMN funding_account_id BIGINT UNSIGNED NULL COMMENT '关联现金账户ID' AFTER account_id,
  ADD KEY idx_debt_records_funding_account_status (funding_account_id, status);

-- 旧数据不会自动分配现金账户，请在页面中逐条补充或编辑后保存。
