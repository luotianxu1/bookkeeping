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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单订单表：记录每次晚餐/菜单组合';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单订单项表：记录一次菜单中的菜品组合';

INSERT INTO food_orders (
  user_id,
  title,
  planned_for,
  remark,
  total_cook_minutes,
  serving_count,
  status
)
SELECT
  u.id,
  seed.title,
  seed.planned_for,
  seed.remark,
  seed.total_cook_minutes,
  seed.serving_count,
  seed.status
FROM users u
JOIN (
  SELECT '周二双人晚餐' AS title, DATE('2026-06-02') AS planned_for, '番茄牛腩锅配一点甜口收尾' AS remark, 55 AS total_cook_minutes, 2 AS serving_count, 'served' AS status
  UNION ALL SELECT '周一工作日晚餐', DATE('2026-06-01'), '一荤一素一汤的快手组合', 43, 2, 'served'
  UNION ALL SELECT '周三轻食午餐', DATE('2026-06-03'), '低脂快手菜，适合午休准备', 18, 2, 'preparing'
  UNION ALL SELECT '周末家庭晚餐', DATE('2026-06-07'), '准备做两道主菜加一份汤', 76, 3, 'planned'
) seed
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  remark = VALUES(remark),
  total_cook_minutes = VALUES(total_cook_minutes),
  serving_count = VALUES(serving_count),
  status = VALUES(status);

DELETE oi
FROM food_order_items oi
JOIN food_orders o ON o.id = oi.order_id
JOIN users u ON u.id = o.user_id
WHERE u.username = 'admin';

INSERT INTO food_order_items (order_id, dish_id, dish_name, category_name, sort_order)
SELECT
  o.id,
  d.id,
  d.name,
  c.name,
  seed.sort_order
FROM food_orders o
JOIN users u ON u.id = o.user_id
JOIN (
  SELECT '周二双人晚餐' AS order_title, '番茄牛腩锅' AS dish_name, 10 AS sort_order
  UNION ALL SELECT '周二双人晚餐', '蒜香虾仁西兰花', 20
  UNION ALL SELECT '周二双人晚餐', '焦糖布丁', 30
  UNION ALL SELECT '周一工作日晚餐', '虾仁炒饭', 10
  UNION ALL SELECT '周一工作日晚餐', '紫菜蛋花汤', 20
  UNION ALL SELECT '周一工作日晚餐', '凉拌黄瓜', 30
  UNION ALL SELECT '周三轻食午餐', '蒜香虾仁西兰花', 10
  UNION ALL SELECT '周末家庭晚餐', '照烧鸡腿饭', 10
  UNION ALL SELECT '周末家庭晚餐', '菌菇鸡汤', 20
  UNION ALL SELECT '周末家庭晚餐', '焦糖布丁', 30
) seed
  ON seed.order_title = o.title
JOIN food_dishes d
  ON d.user_id = o.user_id
 AND d.name = seed.dish_name
JOIN food_categories c
  ON c.id = d.category_id
WHERE u.username = 'admin';
