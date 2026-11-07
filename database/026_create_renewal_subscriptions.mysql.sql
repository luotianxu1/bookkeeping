-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/026_create_renewal_subscriptions.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS renewal_subscriptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '固定支出ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  name VARCHAR(80) NOT NULL COMMENT '固定支出名称，例如 房租、会员、保险',
  provider_name VARCHAR(80) NULL COMMENT '收款方名称',
  amount DECIMAL(18, 2) NOT NULL COMMENT '每期支出金额',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  funding_account_id BIGINT UNSIGNED NOT NULL COMMENT '扣款资金账户ID，必须为现金账户',
  category_id BIGINT UNSIGNED NULL COMMENT '扣款时使用的支出分类ID，新增和修改时必填',
  billing_day TINYINT UNSIGNED NOT NULL COMMENT '每月支出日，取值1到31',
  billing_cycle ENUM('monthly', 'quarterly', 'yearly') NOT NULL DEFAULT 'monthly' COMMENT '支出周期：monthly按月，quarterly按季，yearly按年',
  next_billing_date DATE NOT NULL COMMENT '下次支出日期',
  last_charged_at DATETIME(3) NULL COMMENT '最近一次成功扣款时间',
  last_transaction_id BIGINT UNSIGNED NULL COMMENT '最近一次扣款生成的流水ID',
  last_charge_status ENUM('idle', 'success', 'failed') NOT NULL DEFAULT 'idle' COMMENT '最近一次扣款状态',
  last_charge_message VARCHAR(255) NULL COMMENT '最近一次扣款结果描述',
  status ENUM('active', 'paused', 'cancelled') NOT NULL DEFAULT 'active' COMMENT '固定支出状态',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_renewals_user_status_next_date (user_id, status, next_billing_date),
  KEY idx_renewals_funding_account (funding_account_id),
  KEY idx_renewals_category (category_id),
  KEY idx_renewals_last_transaction (last_transaction_id),
  CONSTRAINT fk_renewals_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_renewals_funding_account
    FOREIGN KEY (funding_account_id) REFERENCES accounts (id),
  CONSTRAINT fk_renewals_category
    FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT fk_renewals_last_transaction
    FOREIGN KEY (last_transaction_id) REFERENCES transactions (id),
  CONSTRAINT chk_renewals_amount_positive
    CHECK (amount > 0),
  CONSTRAINT chk_renewals_billing_day
    CHECK (billing_day BETWEEN 1 AND 31)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='固定支出表：记录房租、会员、保险等周期性自动扣款项目';
