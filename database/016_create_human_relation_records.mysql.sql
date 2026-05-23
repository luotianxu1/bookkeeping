-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/016_create_human_relation_records.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS human_relation_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '人情记录ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '人情账户ID',
  funding_account_id BIGINT UNSIGNED NULL COMMENT '关联现金账户ID',
  direction ENUM('outgoing', 'incoming') NOT NULL COMMENT '人情方向：送出/收到',
  amount DECIMAL(18, 2) NOT NULL COMMENT '人情金额，统一存正数',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  remark VARCHAR(255) NULL COMMENT '备注',
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
  status ENUM('active', 'voided') NOT NULL DEFAULT 'active' COMMENT '状态',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_human_relation_records_user_time (user_id, occurred_at),
  KEY idx_human_relation_records_account_time (account_id, occurred_at),
  KEY idx_human_relation_records_funding_status (funding_account_id, status),
  KEY idx_human_relation_records_account_status (account_id, status),
  CONSTRAINT fk_human_relation_records_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_human_relation_records_account
    FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_human_relation_records_funding_account
    FOREIGN KEY (funding_account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人情记录表：记录联系人之间的红包、礼金、人情往来';
