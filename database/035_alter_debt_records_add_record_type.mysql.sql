-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p bookkeeping_app < database/035_alter_debt_records_add_record_type.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

ALTER TABLE debt_records
  ADD COLUMN record_type ENUM('borrow', 'repayment') NOT NULL DEFAULT 'borrow' COMMENT '记录类型：借款/还款' AFTER direction;
