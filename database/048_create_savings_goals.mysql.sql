-- 目标存入占用账户可分配余额，取出释放预留；记账关联与现金变更同事务保存。
USE bookkeeping_app;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS savings_goals (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  account_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  target_amount DECIMAL(18,2) NOT NULL,
  currency_code CHAR(3) NOT NULL,
  target_date DATE NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_savings_goal_account (account_id),
  KEY idx_savings_goal_user_date (user_id, target_date),
  CONSTRAINT fk_savings_goal_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_savings_goal_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT chk_savings_goal_target CHECK (target_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关联现金账户的储蓄目标';

CREATE TABLE IF NOT EXISTS savings_goal_entries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  goal_id BIGINT UNSIGNED NOT NULL,
  transaction_id BIGINT UNSIGNED NULL,
  type ENUM('deposit','withdrawal') NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  occurred_on DATE NOT NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_savings_goal_entry_goal (goal_id, id),
  UNIQUE KEY uk_savings_goal_transaction (transaction_id, goal_id),
  CONSTRAINT fk_savings_goal_entry_goal FOREIGN KEY (goal_id) REFERENCES savings_goals(id),
  CONSTRAINT fk_savings_goal_entry_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
  CONSTRAINT chk_savings_goal_entry_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储蓄目标独立存取记录';
