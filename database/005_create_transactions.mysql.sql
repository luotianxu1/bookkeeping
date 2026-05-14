-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/005_create_transactions.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS transactions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  transaction_no VARCHAR(64) NOT NULL COMMENT '流水编号，用于幂等和外部展示',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  type ENUM('expense', 'income', 'transfer') NOT NULL COMMENT '流水类型：expense支出，income收入，transfer转账',
  amount DECIMAL(18, 2) NOT NULL COMMENT '交易金额，统一存正数',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  account_id BIGINT UNSIGNED NULL COMMENT '收支账户ID；收入/支出时必填，关联现金账户',
  category_id BIGINT UNSIGNED NULL COMMENT '收支分类ID；收入/支出时必填',
  from_account_id BIGINT UNSIGNED NULL COMMENT '转出账户ID；转账时必填',
  to_account_id BIGINT UNSIGNED NULL COMMENT '转入账户ID；转账时必填',
  title VARCHAR(120) NOT NULL COMMENT '流水标题，例如 午餐、工资入账、账户转账',
  remark VARCHAR(500) NULL COMMENT '备注',
  occurred_at DATETIME(3) NOT NULL COMMENT '发生时间',
  status ENUM('normal', 'voided') NOT NULL DEFAULT 'normal' COMMENT '流水状态：normal正常，voided已作废',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_transactions_user_no (user_id, transaction_no),
  KEY idx_transactions_user_time (user_id, occurred_at),
  KEY idx_transactions_user_type_time (user_id, type, occurred_at),
  KEY idx_transactions_account_time (account_id, occurred_at),
  KEY idx_transactions_category_time (category_id, occurred_at),
  KEY idx_transactions_from_account_time (from_account_id, occurred_at),
  KEY idx_transactions_to_account_time (to_account_id, occurred_at),
  CONSTRAINT fk_transactions_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_transactions_account
    FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_transactions_category
    FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT fk_transactions_from_account
    FOREIGN KEY (from_account_id) REFERENCES accounts (id),
  CONSTRAINT fk_transactions_to_account
    FOREIGN KEY (to_account_id) REFERENCES accounts (id),
  CONSTRAINT chk_transactions_amount_positive
    CHECK (amount > 0),
  CONSTRAINT chk_transactions_income_expense_required
    CHECK (
      (type IN ('expense', 'income')
        AND account_id IS NOT NULL
        AND category_id IS NOT NULL
        AND from_account_id IS NULL
        AND to_account_id IS NULL)
      OR
      (type = 'transfer'
        AND account_id IS NULL
        AND category_id IS NULL
        AND from_account_id IS NOT NULL
        AND to_account_id IS NOT NULL
        AND from_account_id <> to_account_id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收支流水表：记录收入、支出和账户转账';

INSERT INTO transactions (
  transaction_no,
  user_id,
  type,
  amount,
  currency_code,
  account_id,
  category_id,
  from_account_id,
  to_account_id,
  title,
  remark,
  occurred_at
)
SELECT
  seed.transaction_no,
  u.id,
  seed.type,
  seed.amount,
  'CNY',
  account_main.id,
  category_main.id,
  account_from.id,
  account_to.id,
  seed.title,
  seed.remark,
  seed.occurred_at
FROM users u
JOIN (
  SELECT
    'TX202603210001' AS transaction_no,
    'expense' AS type,
    48.00 AS amount,
    '钱包' AS account_name,
    '餐饮' AS category_name,
    NULL AS from_account_name,
    NULL AS to_account_name,
    '午餐' AS title,
    '今天一起做饭买菜' AS remark,
    TIMESTAMP('2026-03-21 12:20:00.000') AS occurred_at
  UNION ALL
  SELECT 'TX202603210002', 'income', 3200.00, '招商银行卡', '工资', NULL, NULL, '工资入账', NULL, TIMESTAMP('2026-03-21 09:00:00.000')
  UNION ALL
  SELECT 'TX202603200001', 'expense', 126.50, '支付宝', '购物', NULL, NULL, '超市购物', NULL, TIMESTAMP('2026-03-20 18:42:00.000')
  UNION ALL
  SELECT 'TX202603200002', 'income', 86.00, '支付宝', '理财', NULL, NULL, '退款到账', NULL, TIMESTAMP('2026-03-20 16:12:00.000')
  UNION ALL
  SELECT 'TX202603190001', 'expense', 22.00, '钱包', '餐饮', NULL, NULL, '咖啡', NULL, TIMESTAMP('2026-03-19 08:35:00.000')
  UNION ALL
  SELECT 'TX202603190002', 'income', 1500.00, '招商银行卡', '理财', NULL, NULL, '项目奖金', NULL, TIMESTAMP('2026-03-19 20:10:00.000')
  UNION ALL
  SELECT 'TX202603180001', 'transfer', 1000.00, NULL, NULL, '钱包', '基金账户', '账户转账', '现金账户转入投资账户', TIMESTAMP('2026-03-18 14:30:00.000')
) seed
LEFT JOIN accounts account_main
  ON account_main.user_id = u.id
  AND account_main.name = seed.account_name
LEFT JOIN categories category_main
  ON category_main.name = seed.category_name
  AND category_main.type = seed.type
  AND (category_main.user_id = u.id OR category_main.user_id IS NULL)
LEFT JOIN accounts account_from
  ON account_from.user_id = u.id
  AND account_from.name = seed.from_account_name
LEFT JOIN accounts account_to
  ON account_to.user_id = u.id
  AND account_to.name = seed.to_account_name
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  type = VALUES(type),
  amount = VALUES(amount),
  currency_code = VALUES(currency_code),
  account_id = VALUES(account_id),
  category_id = VALUES(category_id),
  from_account_id = VALUES(from_account_id),
  to_account_id = VALUES(to_account_id),
  title = VALUES(title),
  remark = VALUES(remark),
  occurred_at = VALUES(occurred_at),
  status = 'normal';
