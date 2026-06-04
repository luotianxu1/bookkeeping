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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐饮分类表：统一管理菜品分类与食材分类';

INSERT INTO food_categories (
  user_id,
  category_type,
  name,
  icon_text,
  icon_tone,
  description,
  sort_order,
  status
)
SELECT
  u.id,
  seed.category_type,
  seed.name,
  seed.icon_text,
  seed.icon_tone,
  seed.description,
  seed.sort_order,
  'active'
FROM users u
JOIN (
  SELECT 'dish' AS category_type, '主食' AS name, '主' AS icon_text, 'blue' AS icon_tone, '米饭、面食、盖饭' AS description, 10 AS sort_order
  UNION ALL SELECT 'dish', '小炒', '炒', 'purple', '快手、下饭、家常', 20
  UNION ALL SELECT 'dish', '汤羹', '汤', 'orange', '炖汤、浓汤、暖胃', 30
  UNION ALL SELECT 'dish', '甜品饮品', '甜', 'sky', '布丁、饮品、下午茶', 40
  UNION ALL SELECT 'ingredient', '肉类蛋白', '肉', 'orange', '牛肉、鸡蛋、虾仁等常备主料', 110
  UNION ALL SELECT 'ingredient', '蔬菜水果', '蔬', 'green', '番茄、西兰花、洋葱等清爽配菜', 120
  UNION ALL SELECT 'ingredient', '调味干货', '调', 'purple', '盐、黑胡椒、香料与面包糠', 130
) seed
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  icon_text = VALUES(icon_text),
  icon_tone = VALUES(icon_tone),
  description = VALUES(description),
  sort_order = VALUES(sort_order),
  status = VALUES(status);
