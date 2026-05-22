-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/012_add_contact_id_to_accounts.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

ALTER TABLE accounts
  ADD COLUMN contact_id BIGINT UNSIGNED NULL COMMENT '关联联系人ID' AFTER account_type_id,
  ADD KEY idx_accounts_contact (contact_id);
