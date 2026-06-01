-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/026_create_renewal_subscriptions.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS renewal_subscriptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '续费订阅ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  name VARCHAR(80) NOT NULL COMMENT '续费名称，例如 网易云会员、iCloud+',
  provider_name VARCHAR(80) NULL COMMENT '服务提供方名称',
  amount DECIMAL(18, 2) NOT NULL COMMENT '每期扣费金额',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  funding_account_id BIGINT UNSIGNED NOT NULL COMMENT '扣费资金账户ID，必须为现金账户',
  billing_day TINYINT UNSIGNED NOT NULL COMMENT '每月扣费日，取值1到31',
  billing_cycle ENUM('monthly', 'quarterly', 'yearly') NOT NULL DEFAULT 'monthly' COMMENT '扣费周期：monthly按月，quarterly按季，yearly按年',
  next_billing_date DATE NOT NULL COMMENT '下次扣费日期',
  last_charged_at DATETIME(3) NULL COMMENT '最近一次成功扣费时间',
  last_transaction_id BIGINT UNSIGNED NULL COMMENT '最近一次扣费生成的流水ID',
  last_charge_status ENUM('idle', 'success', 'failed') NOT NULL DEFAULT 'idle' COMMENT '最近一次扣费状态',
  last_charge_message VARCHAR(255) NULL COMMENT '最近一次扣费结果描述',
  status ENUM('active', 'paused', 'cancelled') NOT NULL DEFAULT 'active' COMMENT '订阅状态',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_renewals_user_status_next_date (user_id, status, next_billing_date),
  KEY idx_renewals_funding_account (funding_account_id),
  KEY idx_renewals_last_transaction (last_transaction_id),
  CONSTRAINT fk_renewals_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_renewals_funding_account
    FOREIGN KEY (funding_account_id) REFERENCES accounts (id),
  CONSTRAINT fk_renewals_last_transaction
    FOREIGN KEY (last_transaction_id) REFERENCES transactions (id),
  CONSTRAINT chk_renewals_amount_positive
    CHECK (amount > 0),
  CONSTRAINT chk_renewals_billing_day
    CHECK (billing_day BETWEEN 1 AND 31)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动续费订阅表：记录每月自动扣费的会员与服务';

INSERT INTO categories (
  user_id,
  name,
  type,
  icon,
  color,
  is_system,
  sort_order,
  remark
) VALUES
  (NULL, '会员续费', 'expense', 'subscription', '#0F766E', 1, 95, '自动续费与会员订阅支出')
ON DUPLICATE KEY UPDATE
  icon = VALUES(icon),
  color = VALUES(color),
  is_system = VALUES(is_system),
  sort_order = VALUES(sort_order),
  status = 'active',
  remark = VALUES(remark);

INSERT INTO renewal_subscriptions (
  user_id,
  name,
  provider_name,
  amount,
  currency_code,
  funding_account_id,
  billing_day,
  billing_cycle,
  next_billing_date,
  last_charge_status,
  status,
  remark
)
SELECT
  u.id,
  seed.name,
  seed.provider_name,
  seed.amount,
  'CNY',
  account_main.id,
  seed.billing_day,
  seed.billing_cycle,
  seed.next_billing_date,
  seed.last_charge_status,
  seed.status,
  seed.remark
FROM users u
JOIN (
  SELECT
    '腾讯视频会员' AS name,
    '腾讯视频' AS provider_name,
    25.00 AS amount,
    '支付宝' AS funding_account_name,
    5 AS billing_day,
    'monthly' AS billing_cycle,
    DATE('2026-06-05') AS next_billing_date,
    'idle' AS last_charge_status,
    'active' AS status,
    '家庭影音服务' AS remark
  UNION ALL
  SELECT
    'iCloud+ 200GB',
    'Apple',
    21.00,
    '招商银行卡',
    18,
    'monthly',
    DATE('2026-06-18'),
    'idle',
    'active',
    '照片与文稿云空间'
  UNION ALL
  SELECT
    '健身会员',
    '超级猩猩',
    39.00,
    '支付宝',
    12,
    'monthly',
    DATE('2026-06-12'),
    'idle',
    'paused',
    '暂停中的按月续费'
) seed
JOIN accounts account_main
  ON account_main.user_id = u.id
  AND account_main.name = seed.funding_account_name
WHERE u.username = 'admin'
  AND NOT EXISTS (
    SELECT 1
    FROM renewal_subscriptions existing
    WHERE existing.user_id = u.id
      AND existing.name = seed.name
      AND existing.status <> 'cancelled'
  );
