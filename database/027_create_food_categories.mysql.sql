-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/027_create_food_categories.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS food_categories (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '餐饮分类ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  category_type ENUM('dish', 'ingredient') NOT NULL COMMENT '分类类型：dish菜品，ingredient食材',
  name VARCHAR(80) NOT NULL COMMENT '分类名称',
  icon_text VARCHAR(12) NOT NULL COMMENT '分类图标文案',
  icon_tone VARCHAR(32) NOT NULL DEFAULT 'blue' COMMENT '图标色系',
  description VARCHAR(255) NULL COMMENT '分类说明',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status ENUM('active', 'archived') NOT NULL DEFAULT 'active' COMMENT '状态',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_categories_user_type_name (user_id, category_type, name),
  KEY idx_food_categories_user_type_status (user_id, category_type, status, sort_order),
  CONSTRAINT fk_food_categories_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='餐饮分类表：统一管理菜品分类与食材分类';
