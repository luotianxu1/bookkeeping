-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/023_create_todo_items.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS todo_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '待办事项ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  title VARCHAR(120) NOT NULL COMMENT '事项标题',
  due_at DATETIME(3) NOT NULL COMMENT '截止时间',
  remark VARCHAR(500) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status ENUM('pending', 'completed') NOT NULL DEFAULT 'pending' COMMENT '状态：pending待处理，completed已完成',
  completed_at DATETIME(3) NULL COMMENT '完成时间',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_todo_items_user_title_due (user_id, title, due_at),
  KEY idx_todo_items_user_status_due (user_id, status, due_at),
  KEY idx_todo_items_user_due (user_id, due_at),
  CONSTRAINT fk_todo_items_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办事项表：记录个人待办、截止时间与完成状态';

INSERT INTO todo_items (
  user_id,
  title,
  due_at,
  remark,
  sort_order,
  status,
  completed_at
)
SELECT
  u.id,
  seed.title,
  seed.due_at,
  seed.remark,
  seed.sort_order,
  seed.status,
  seed.completed_at
FROM users u
JOIN (
  SELECT
    '下班后一起去超市买菜' AS title,
    TIMESTAMP('2026-05-26 19:00:00.000') AS due_at,
    '整理冰箱，把临期食材优先放前面' AS remark,
    10 AS sort_order,
    'completed' AS status,
    TIMESTAMP('2026-05-26 18:40:00.000') AS completed_at
  UNION ALL
  SELECT
    '整理冰箱，把临期食材优先放前面',
    TIMESTAMP('2026-05-26 21:00:00.000'),
    '把周末聚餐要用到的食材单独放出来',
    20,
    'pending',
    NULL
  UNION ALL
  SELECT
    '确认周末出行时间并预订餐厅',
    TIMESTAMP('2026-05-31 12:00:00.000'),
    '和朋友对齐集合时间，顺便确认停车位',
    30,
    'pending',
    NULL
) seed
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  remark = VALUES(remark),
  sort_order = VALUES(sort_order),
  status = VALUES(status),
  completed_at = VALUES(completed_at);
