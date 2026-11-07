-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/028_create_food_ingredients.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS food_ingredients (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '食材ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  category_id BIGINT UNSIGNED NOT NULL COMMENT '食材分类ID',
  name VARCHAR(80) NOT NULL COMMENT '食材名称',
  stock_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '库存数量',
  unit VARCHAR(20) NOT NULL COMMENT '计量单位',
  reorder_level DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '补货阈值',
  storage_location VARCHAR(120) NULL COMMENT '收纳位置',
  status ENUM('enough', 'low', 'urgent') NOT NULL DEFAULT 'enough' COMMENT '库存状态',
  note VARCHAR(255) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_ingredients_user_category_name (user_id, category_id, name),
  KEY idx_food_ingredients_user_status (user_id, status, sort_order),
  KEY idx_food_ingredients_user_category (user_id, category_id),
  CONSTRAINT fk_food_ingredients_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_food_ingredients_category
    FOREIGN KEY (category_id) REFERENCES food_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='食材表：记录食材库存、分类与补货状态';
