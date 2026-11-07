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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待办事项表：记录个人待办、截止时间与完成状态';
