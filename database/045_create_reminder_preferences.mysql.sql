-- 站内统一提醒：业务记录实时生成提醒，仅持久化个人设置和每期处理状态。
USE bookkeeping_app;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS reminder_settings (
  user_id BIGINT UNSIGNED NOT NULL,
  advance_days INT NOT NULL DEFAULT 3,
  todo_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  anniversary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  renewal_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  liability_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  investment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  budget_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  reimbursement_enabled TINYINT(1) NOT NULL DEFAULT 1,
  term_deposit_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_reminder_settings_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT chk_reminder_advance_days CHECK (advance_days BETWEEN 0 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人站内提醒设置';

CREATE TABLE IF NOT EXISTS reminder_states (
  user_id BIGINT UNSIGNED NOT NULL,
  reminder_key VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源、记录、期次和提醒阶段',
  read_at DATETIME(3) NULL,
  snoozed_until DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, reminder_key),
  CONSTRAINT fk_reminder_states_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按用户隔离的提醒处理状态';
