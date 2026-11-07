-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/030_create_food_orders.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS food_orders (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单订单ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  title VARCHAR(120) NOT NULL COMMENT '菜单标题',
  planned_for DATE NOT NULL COMMENT '计划日期',
  remark VARCHAR(500) NULL COMMENT '备注',
  total_cook_minutes INT NOT NULL DEFAULT 0 COMMENT '总预计分钟数',
  serving_count INT NOT NULL DEFAULT 1 COMMENT '适合份量',
  status ENUM('planned', 'preparing', 'served') NOT NULL DEFAULT 'planned' COMMENT '菜单状态',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_orders_user_title_date (user_id, title, planned_for),
  KEY idx_food_orders_user_status_date (user_id, status, planned_for),
  CONSTRAINT fk_food_orders_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单订单表：记录每次晚餐/菜单组合';

CREATE TABLE IF NOT EXISTS food_order_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单订单项ID',
  order_id BIGINT UNSIGNED NOT NULL COMMENT '菜单订单ID',
  dish_id BIGINT UNSIGNED NOT NULL COMMENT '菜品ID',
  dish_name VARCHAR(120) NOT NULL COMMENT '菜品快照名称',
  category_name VARCHAR(80) NOT NULL COMMENT '分类快照名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_order_items_order_sort (order_id, sort_order),
  KEY idx_food_order_items_order (order_id),
  CONSTRAINT fk_food_order_items_order
    FOREIGN KEY (order_id) REFERENCES food_orders (id)
      ON DELETE CASCADE,
  CONSTRAINT fk_food_order_items_dish
    FOREIGN KEY (dish_id) REFERENCES food_dishes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单订单项表：记录一次菜单中的菜品组合';
