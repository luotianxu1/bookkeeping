-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p bookkeeping_app < database/036_create_asset_daily_snapshots.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS asset_daily_snapshots (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  account_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '账户ID，0表示总资产快照',
  snapshot_date DATE NOT NULL COMMENT '快照日期，表示当天24点收盘后的资产',
  total_assets DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '总资产金额',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_asset_daily_snapshots_user_account_date (user_id, account_id, snapshot_date),
  KEY idx_asset_daily_snapshots_date (snapshot_date),
  KEY idx_asset_daily_snapshots_user_date (user_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按天存储的资产快照';
