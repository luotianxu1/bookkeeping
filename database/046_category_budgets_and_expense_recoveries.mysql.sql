-- 分类预算与退款/报销：兼容 MySQL 5.7 / 8.0.16+；先执行 045。
-- 先选择目标库并暂停账务写入。只补结构，不删除/改写历史流水或余额；支持部分失败后重跑。
-- 5.7 不执行 CHECK，因此使用触发器执行同样的金额约束；项目完整数据库支持仍为 MySQL 8。
-- mysql -u root -p bookkeeping_app < database/046_category_budgets_and_expense_recoveries.mysql.sql
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_046_expense_recoveries;
DELIMITER $$
CREATE PROCEDURE migrate_046_expense_recoveries()
BEGIN
  DECLARE server_major INT DEFAULT CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED);
  DECLARE changes_sql TEXT DEFAULT '';
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the target database before running migration 046';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'reminder_settings') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Run migration 045 before migration 046';
  END IF;

  CREATE TABLE IF NOT EXISTS category_budgets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    budget_month DATE NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'CNY',
    UNIQUE KEY uk_category_budget_month (user_id, budget_month, category_id),
    CONSTRAINT fk_category_budget_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_category_budget_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_category_budget_amount CHECK (amount > 0)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'original_transaction_id') THEN
    SET changes_sql = CONCAT(changes_sql, 'ADD COLUMN original_transaction_id BIGINT UNSIGNED NULL,');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'recovery_kind') THEN
    SET changes_sql = CONCAT(changes_sql, 'ADD COLUMN recovery_kind VARCHAR(20) NULL COMMENT ''refund退款 / reimbursement报销到账'',');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'reimbursement_amount') THEN
    SET changes_sql = CONCAT(changes_sql, 'ADD COLUMN reimbursement_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''计划报销总额，不影响余额'',');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'transactions' AND index_name = 'idx_transaction_original') THEN
    SET changes_sql = CONCAT(changes_sql, 'ADD KEY idx_transaction_original (user_id, original_transaction_id, status),');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'transactions' AND constraint_name = 'fk_transaction_original' AND constraint_type = 'FOREIGN KEY') THEN
    SET changes_sql = CONCAT(changes_sql, 'ADD CONSTRAINT fk_transaction_original FOREIGN KEY (original_transaction_id) REFERENCES transactions(id),');
  END IF;

  -- DROP CHECK 必须放在动态 SQL 中，否则 MySQL 5.7 在创建存储过程时就会报语法错误。
  IF server_major >= 8 THEN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'transactions' AND constraint_name = 'chk_transactions_amount_positive' AND constraint_type = 'CHECK') THEN
      SET changes_sql = CONCAT(changes_sql, 'DROP CHECK chk_transactions_amount_positive,');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'transactions' AND constraint_name = 'chk_transactions_recovery_amount' AND constraint_type = 'CHECK') THEN
      SET changes_sql = CONCAT(changes_sql, 'ADD CONSTRAINT chk_transactions_recovery_amount CHECK (
        (original_transaction_id IS NULL AND recovery_kind IS NULL AND amount > 0)
        OR (original_transaction_id IS NOT NULL AND recovery_kind IS NOT NULL
          AND recovery_kind IN (''refund'',''reimbursement'') AND type = ''expense'' AND amount < 0 AND reimbursement_amount = 0)),');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'transactions' AND constraint_name = 'chk_transaction_reimbursement' AND constraint_type = 'CHECK') THEN
      SET changes_sql = CONCAT(changes_sql, 'ADD CONSTRAINT chk_transaction_reimbursement CHECK (reimbursement_amount >= 0 AND
        (reimbursement_amount = 0 OR (type = ''expense'' AND original_transaction_id IS NULL AND reimbursement_amount <= amount))),');
    END IF;
  END IF;
  IF changes_sql <> '' THEN
    SET @migration_046_ddl = CONCAT('ALTER TABLE transactions ', changes_sql,
      'MODIFY COLUMN amount DECIMAL(18,2) NOT NULL');
    PREPARE migration_046_statement FROM @migration_046_ddl;
    EXECUTE migration_046_statement;
    DEALLOCATE PREPARE migration_046_statement;
    SET @migration_046_ddl = NULL;
  END IF;

  -- 5.7 的 CHECK 仅解析不执行，安装触发器前也检查历史数据，发现异常直接停止。
  IF EXISTS (SELECT 1 FROM transactions WHERE NOT (
((original_transaction_id IS NULL AND recovery_kind IS NULL AND amount > 0)
      OR (original_transaction_id IS NOT NULL AND recovery_kind IS NOT NULL
        AND recovery_kind IN ('refund','reimbursement') AND type = 'expense' AND amount < 0 AND reimbursement_amount = 0))
    AND reimbursement_amount >= 0
    AND (reimbursement_amount = 0 OR (type = 'expense' AND original_transaction_id IS NULL AND reimbursement_amount <= amount))
  )) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid transaction amounts: review existing rows before retrying migration 046';
  END IF;
  IF EXISTS (SELECT 1 FROM category_budgets WHERE amount <= 0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid category budget amounts: review existing rows before retrying migration 046';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reminder_settings' AND column_name = 'budget_enabled') THEN
    ALTER TABLE reminder_settings ADD COLUMN budget_enabled BOOLEAN NOT NULL DEFAULT TRUE;
  END IF;
END$$
DELIMITER ;
CALL migrate_046_expense_recoveries();
DROP PROCEDURE migrate_046_expense_recoveries;

-- MySQL 8 由 CHECK 强制约束；5.7 的 INSERT 和 UPDATE 由这组触发器提供同等金额校验。
-- 只替换本迁移自己的触发器，可重跑；执行期间保持账务写入暂停。
DROP TRIGGER IF EXISTS trg_046_transactions_insert;
DELIMITER $$
CREATE TRIGGER trg_046_transactions_insert BEFORE INSERT ON transactions
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

DROP TRIGGER IF EXISTS trg_046_transactions_update;
DELIMITER $$
CREATE TRIGGER trg_046_transactions_update BEFORE UPDATE ON transactions
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

DROP TRIGGER IF EXISTS trg_046_category_budgets_insert;
DELIMITER $$
CREATE TRIGGER trg_046_category_budgets_insert BEFORE INSERT ON category_budgets
FOR EACH ROW
BEGIN
  IF CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 AND NOT (
    NEW.amount > 0
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid category_budgets amount or recovery fields';
  END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_046_category_budgets_update;
DELIMITER $$
CREATE TRIGGER trg_046_category_budgets_update BEFORE UPDATE ON category_budgets
FOR EACH ROW
BEGIN
  IF CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 AND NOT (
    NEW.amount > 0
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid category_budgets amount or recovery fields';
  END IF;
END$$
DELIMITER ;

