-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/003_create_accounts.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS accounts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_type_id BIGINT UNSIGNED NOT NULL COMMENT '账户类型ID',
  name VARCHAR(80) NOT NULL COMMENT '账户名称，例如 钱包、招商银行卡、基金账户',
  icon VARCHAR(32) NULL COMMENT '账户图标字符串编码',
  color VARCHAR(32) NULL COMMENT '账户颜色',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  current_balance DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '当前余额',
  include_in_net_worth TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否计入总资产',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status ENUM('active', 'archived', 'disabled') NOT NULL DEFAULT 'active' COMMENT '账户状态',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_accounts_user_name (user_id, name),
  KEY idx_accounts_user_status_sort (user_id, status, sort_order),
  KEY idx_accounts_type (account_type_id),
  CONSTRAINT fk_accounts_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_accounts_type
    FOREIGN KEY (account_type_id) REFERENCES account_types (id)
) ENGINE=InnoDB COMMENT='账户表：记录用户的现金、投资、负债、人情等账户';

INSERT INTO accounts (
  user_id,
  account_type_id,
  name,
  icon,
  currency_code,
  current_balance,
  include_in_net_worth,
  sort_order,
  remark
)
SELECT
  u.id,
  t.id,
  seed.name,
  seed.icon,
  'CNY',
  seed.current_balance,
  t.include_in_net_worth_default,
  seed.sort_order,
  seed.remark
FROM users u
JOIN (
  SELECT 'cash' AS type_code, '钱包' AS name, 'wallet' AS icon, 2300.00 AS current_balance, 10 AS sort_order, '日常零用' AS remark
  UNION ALL
  SELECT 'cash', '招商银行卡', 'bank-card', 16800.00, 20, '储蓄卡'
  UNION ALL
  SELECT 'cash', '支付宝', 'alipay', 3200.00, 30, '第三方钱包'
  UNION ALL
  SELECT 'cash', '备用金', 'reserve-fund', 5000.00, 40, '紧急使用'
  UNION ALL
  SELECT 'investment', '基金账户', 'fund', 72100.00, 50, '基金持仓账户'
  UNION ALL
  SELECT 'gold', '黄金账户', 'gold', 5088.60, 60, '黄金持仓账户'
  UNION ALL
  SELECT 'investment', '股票账户', 'stock', 35800.00, 70, '股票持仓账户'
) seed
JOIN account_types t ON t.code = seed.type_code
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  account_type_id = VALUES(account_type_id),
  icon = VALUES(icon),
  currency_code = VALUES(currency_code),
  current_balance = VALUES(current_balance),
  include_in_net_worth = VALUES(include_in_net_worth),
  sort_order = VALUES(sort_order),
  status = 'active',
  remark = VALUES(remark);
