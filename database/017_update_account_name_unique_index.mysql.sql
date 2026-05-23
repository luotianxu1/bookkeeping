-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/017_update_account_name_unique_index.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

ALTER TABLE accounts
  DROP INDEX uk_accounts_user_name,
  ADD UNIQUE KEY uk_accounts_user_type_name (user_id, account_type_id, name);
