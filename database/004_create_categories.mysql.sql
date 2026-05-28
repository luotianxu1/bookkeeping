-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/004_create_categories.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  user_id BIGINT UNSIGNED NULL COMMENT '所属用户ID，NULL表示系统内置分类',
  name VARCHAR(64) NOT NULL COMMENT '分类名称，例如：餐饮、工资',
  type ENUM('expense', 'income') NOT NULL COMMENT '分类类型：expense支出，income收入',
  icon VARCHAR(64) NOT NULL COMMENT '图标字符串编码，例如 food、salary、shopping',
  color VARCHAR(32) NULL COMMENT '分类颜色',
  parent_id BIGINT NULL COMMENT '上级分类ID，NULL表示一级分类',
  parent_key BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(parent_id, 0)) STORED COMMENT '唯一索引用父分类ID，一级分类固定为0',
  level INT NOT NULL DEFAULT 1 COMMENT '分类层级：1一级分类，2二级分类',
  is_system TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统内置分类',
  user_key BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(user_id, 0)) STORED COMMENT '唯一索引用用户ID，系统分类固定为0',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status ENUM('active', 'disabled') NOT NULL DEFAULT 'active' COMMENT '状态',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_user_type_parent_name (user_key, type, parent_key, name),
  KEY idx_categories_user_type_sort (user_id, type, status, sort_order),
  KEY idx_categories_type_status_sort (type, status, sort_order),
  KEY idx_categories_parent_id (parent_id),
  KEY idx_categories_user_type_parent_name (user_id, type, parent_id, name),
  CONSTRAINT fk_categories_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收支分类表';

INSERT INTO categories (
  user_id,
  name,
  type,
  icon,
  color,
  is_system,
  sort_order,
  remark
) VALUES
  (NULL, '餐饮', 'expense', 'food', '#1D4ED8', 1, 10, '餐饮支出'),
  (NULL, '日用', 'expense', 'daily', '#334155', 1, 20, '日用品支出'),
  (NULL, '交通', 'expense', 'transport', '#334155', 1, 30, '交通出行'),
  (NULL, '娱乐', 'expense', 'entertainment', '#334155', 1, 40, '娱乐消费'),
  (NULL, '购物', 'expense', 'shopping', '#334155', 1, 50, '购物消费'),
  (NULL, '其他', 'expense', 'other', '#334155', 1, 90, '其他支出'),
  (NULL, '工资', 'income', 'salary', '#334155', 1, 10, '工资收入'),
  (NULL, '理财', 'income', 'investment-income', '#334155', 1, 20, '理财收入')
ON DUPLICATE KEY UPDATE
  icon = VALUES(icon),
  color = VALUES(color),
  is_system = VALUES(is_system),
  sort_order = VALUES(sort_order),
  status = 'active',
  remark = VALUES(remark);
