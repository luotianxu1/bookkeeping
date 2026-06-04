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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食材表：记录食材库存、分类与补货状态';

INSERT INTO food_ingredients (
  user_id,
  category_id,
  name,
  stock_amount,
  unit,
  reorder_level,
  storage_location,
  status,
  note,
  sort_order
)
SELECT
  u.id,
  c.id,
  seed.name,
  seed.stock_amount,
  seed.unit,
  seed.reorder_level,
  seed.storage_location,
  seed.status,
  seed.note,
  seed.sort_order
FROM users u
JOIN food_categories c
  ON c.user_id = u.id
 AND c.category_type = 'ingredient'
JOIN (
  SELECT '肉类蛋白' AS category_name, '牛腩' AS name, 1.50 AS stock_amount, 'kg' AS unit, 0.50 AS reorder_level, '冷冻层' AS storage_location, 'enough' AS status, '适合番茄牛腩锅' AS note, 10 AS sort_order
  UNION ALL SELECT '肉类蛋白', '虾仁', 0.20, 'kg', 0.30, '冷冻层', 'low', '适合小炒与快手菜', 20
  UNION ALL SELECT '肉类蛋白', '鸡蛋', 4.00, '个', 6.00, '冷藏门架', 'urgent', '布丁和蛋花汤都要用', 30
  UNION ALL SELECT '蔬菜水果', '番茄', 6.00, '个', 4.00, '冷藏抽屉', 'enough', '优先消耗熟透番茄', 40
  UNION ALL SELECT '蔬菜水果', '西兰花', 1.00, '朵', 2.00, '冷藏抽屉', 'low', '适合搭配虾仁快炒', 50
  UNION ALL SELECT '蔬菜水果', '洋葱', 3.00, '个', 2.00, '常温篮', 'enough', '可做炒菜和炖煮底味', 60
  UNION ALL SELECT '调味干货', '黑胡椒', 1.00, '瓶', 1.00, '调料架', 'low', '快见底了', 70
  UNION ALL SELECT '调味干货', '砂糖', 0.30, 'kg', 0.20, '调料架', 'enough', '布丁和饮品常用', 80
  UNION ALL SELECT '调味干货', '面包糠', 0.10, 'kg', 0.15, '干货柜', 'urgent', '下次做炸物前要补货', 90
) seed
  ON seed.category_name = c.name
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  stock_amount = VALUES(stock_amount),
  unit = VALUES(unit),
  reorder_level = VALUES(reorder_level),
  storage_location = VALUES(storage_location),
  status = VALUES(status),
  note = VALUES(note),
  sort_order = VALUES(sort_order);
