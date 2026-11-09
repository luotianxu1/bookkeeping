-- 账号资料、一次性恢复码和可撤销登录会话；新库按编号初始化。
CREATE TABLE IF NOT EXISTS user_security (
  user_id BIGINT UNSIGNED NOT NULL,
  recovery_code_hash CHAR(64) NULL COMMENT '高熵恢复码的 SHA-256；只保存摘要',
  recovery_created_at DATETIME(3) NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_security_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_sessions (
  id CHAR(36) NOT NULL COMMENT 'JWT jti，会话标识',
  user_id BIGINT UNSIGNED NOT NULL,
  device_name VARCHAR(100) NOT NULL,
  ip_address VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  last_seen_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_user_sessions_user (user_id, revoked_at, expires_at),
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
