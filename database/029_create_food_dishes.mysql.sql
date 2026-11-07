-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/029_create_food_dishes.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS food_dishes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜品ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  category_id BIGINT UNSIGNED NOT NULL COMMENT '菜品分类ID',
  name VARCHAR(120) NOT NULL COMMENT '菜品名称',
  subtitle VARCHAR(160) NULL COMMENT '副标题',
  description VARCHAR(800) NULL COMMENT '菜品介绍',
  taste_tags VARCHAR(255) NULL COMMENT '口味标签，逗号分隔',
  highlight_tags VARCHAR(255) NULL COMMENT '亮点标签，逗号分隔',
  cook_minutes INT NOT NULL DEFAULT 0 COMMENT '预计烹饪分钟数',
  serving_count INT NOT NULL DEFAULT 1 COMMENT '适合份量',
  cover_tone VARCHAR(32) NOT NULL DEFAULT 'blue' COMMENT '封面色系',
  cover_text VARCHAR(32) NOT NULL DEFAULT '' COMMENT '封面角标文案',
  status ENUM('published', 'pending', 'draft') NOT NULL DEFAULT 'published' COMMENT '菜品状态',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_dishes_user_name (user_id, name),
  KEY idx_food_dishes_user_category_status (user_id, category_id, status, sort_order),
  CONSTRAINT fk_food_dishes_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_food_dishes_category
    FOREIGN KEY (category_id) REFERENCES food_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品表：记录菜品基本信息、分类与展示标签';

CREATE TABLE IF NOT EXISTS food_dish_ingredients (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜品食材关联ID',
  dish_id BIGINT UNSIGNED NOT NULL COMMENT '菜品ID',
  ingredient_id BIGINT UNSIGNED NULL COMMENT '食材ID',
  ingredient_name VARCHAR(80) NOT NULL COMMENT '展示用食材名称',
  amount VARCHAR(40) NOT NULL COMMENT '用量说明',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_dish_ingredients_dish_sort (dish_id, sort_order),
  KEY idx_food_dish_ingredients_dish (dish_id),
  CONSTRAINT fk_food_dish_ingredients_dish
    FOREIGN KEY (dish_id) REFERENCES food_dishes (id)
      ON DELETE CASCADE,
  CONSTRAINT fk_food_dish_ingredients_ingredient
    FOREIGN KEY (ingredient_id) REFERENCES food_ingredients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品食材关联表';

CREATE TABLE IF NOT EXISTS food_dish_steps (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜品步骤ID',
  dish_id BIGINT UNSIGNED NOT NULL COMMENT '菜品ID',
  step_no INT NOT NULL COMMENT '步骤序号',
  content VARCHAR(500) NOT NULL COMMENT '步骤内容',
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_dish_steps_dish_step (dish_id, step_no),
  KEY idx_food_dish_steps_dish (dish_id),
  CONSTRAINT fk_food_dish_steps_dish
    FOREIGN KEY (dish_id) REFERENCES food_dishes (id)
      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品步骤表';
