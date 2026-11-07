-- 同一次业务提交在超时重试后只入账一次。与业务流水同事务写入，保留原始响应。
CREATE TABLE IF NOT EXISTS accounting_requests (
  user_id BIGINT UNSIGNED NOT NULL,
  operation VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  response_json LONGTEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, operation, request_id),
  CONSTRAINT fk_accounting_requests_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
