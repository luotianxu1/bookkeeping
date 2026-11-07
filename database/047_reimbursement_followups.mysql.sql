-- 先执行 046。显式选择目标库；兼容 MySQL 5.7 / 8，可重复执行。
-- 只新增跟进信息与提醒开关，不修改账务金额或历史记录。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS expense_recovery_followups (
  transaction_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  recipient VARCHAR(100) DEFAULT NULL,
  expected_date DATE DEFAULT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_recovery_followup_due (user_id, expected_date),
  CONSTRAINT fk_recovery_followup_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
  CONSTRAINT fk_recovery_followup_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS migrate_047_reimbursement_followups;
DELIMITER $$
CREATE PROCEDURE migrate_047_reimbursement_followups()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='reminder_settings' AND column_name='reimbursement_enabled') THEN
    ALTER TABLE reminder_settings ADD COLUMN reimbursement_enabled TINYINT(1) NOT NULL DEFAULT 1;
  END IF;
END$$
DELIMITER ;
CALL migrate_047_reimbursement_followups();
DROP PROCEDURE migrate_047_reimbursement_followups;
