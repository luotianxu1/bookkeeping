-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/018_make_human_relation_funding_account_nullable.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

ALTER TABLE human_relation_records
  MODIFY COLUMN funding_account_id BIGINT UNSIGNED NULL COMMENT '关联现金账户ID';
