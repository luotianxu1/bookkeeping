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
  amount DECIMAL(18, 2) NOT NULL COMMENT '普通流水为正数，退款/报销到账为负支出',
  original_transaction_id BIGINT UNSIGNED NULL COMMENT '退款/报销关联的原支出',
  recovery_kind VARCHAR(20) NULL COMMENT 'refund退款 / reimbursement报销到账',
  reimbursement_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '计划报销总额，不影响余额',
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
  KEY idx_transaction_original (user_id, original_transaction_id, status),
  CONSTRAINT fk_transaction_original FOREIGN KEY (original_transaction_id) REFERENCES transactions(id),
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
  CONSTRAINT chk_transactions_recovery_amount CHECK (
    (original_transaction_id IS NULL AND recovery_kind IS NULL AND amount > 0)
    OR (original_transaction_id IS NOT NULL AND recovery_kind IS NOT NULL
      AND recovery_kind IN ('refund','reimbursement') AND type = 'expense' AND amount < 0 AND reimbursement_amount = 0)),
  CONSTRAINT chk_transaction_reimbursement CHECK (reimbursement_amount >= 0 AND
    (reimbursement_amount = 0 OR (type = 'expense' AND original_transaction_id IS NULL AND reimbursement_amount <= amount))),
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收支流水表：记录收入、支出和账户转账';

CREATE TABLE IF NOT EXISTS expense_recovery_followups (
  transaction_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  recipient VARCHAR(100) DEFAULT NULL,
  expected_date DATE DEFAULT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_recovery_followup_due (user_id, expected_date),
  CONSTRAINT fk_recovery_followup_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
  CONSTRAINT fk_recovery_followup_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报销对象与预计到账日期';

-- MySQL 5.7 不执行 CHECK；新建库时同时安装金额校验触发器。
DELIMITER $$
CREATE TRIGGER trg_transactions_insert BEFORE INSERT ON transactions
FOR EACH ROW
BEGIN
  IF CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 AND NOT (
    ((NEW.original_transaction_id IS NULL AND NEW.recovery_kind IS NULL AND NEW.amount > 0)
      OR (NEW.original_transaction_id IS NOT NULL AND NEW.recovery_kind IS NOT NULL
        AND NEW.recovery_kind IN ('refund','reimbursement') AND NEW.type = 'expense' AND NEW.amount < 0 AND NEW.reimbursement_amount = 0))
    AND NEW.reimbursement_amount >= 0
    AND (NEW.reimbursement_amount = 0 OR (NEW.type = 'expense' AND NEW.original_transaction_id IS NULL AND NEW.reimbursement_amount <= NEW.amount))
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid transactions amount or recovery fields';
  END IF;
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER trg_transactions_update BEFORE UPDATE ON transactions
FOR EACH ROW
BEGIN
  IF CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 AND NOT (
    ((NEW.original_transaction_id IS NULL AND NEW.recovery_kind IS NULL AND NEW.amount > 0)
      OR (NEW.original_transaction_id IS NOT NULL AND NEW.recovery_kind IS NOT NULL
        AND NEW.recovery_kind IN ('refund','reimbursement') AND NEW.type = 'expense' AND NEW.amount < 0 AND NEW.reimbursement_amount = 0))
    AND NEW.reimbursement_amount >= 0
    AND (NEW.reimbursement_amount = 0 OR (NEW.type = 'expense' AND NEW.original_transaction_id IS NULL AND NEW.reimbursement_amount <= NEW.amount))
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid transactions amount or recovery fields';
  END IF;
END$$
DELIMITER ;
