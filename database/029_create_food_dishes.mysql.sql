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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表：记录菜品基本信息、分类与展示标签';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品食材关联表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品步骤表';

INSERT INTO food_dishes (
  user_id,
  category_id,
  name,
  subtitle,
  description,
  taste_tags,
  highlight_tags,
  cook_minutes,
  serving_count,
  cover_tone,
  cover_text,
  status,
  sort_order
)
SELECT
  u.id,
  c.id,
  seed.name,
  seed.subtitle,
  seed.description,
  seed.taste_tags,
  seed.highlight_tags,
  seed.cook_minutes,
  seed.serving_count,
  seed.cover_tone,
  seed.cover_text,
  seed.status,
  seed.sort_order
FROM users u
JOIN food_categories c
  ON c.user_id = u.id
 AND c.category_type = 'dish'
JOIN (
  SELECT '主食' AS category_name, '番茄牛腩锅' AS name, '酸甜开胃，暖胃下饭' AS subtitle,
    '番茄牛腩锅口感浓郁，番茄酸甜解腻，牛腩软烂入味，适合两个人一起配米饭慢慢吃。' AS description,
    '酸甜,浓郁' AS taste_tags, '酸甜开胃,适合晚餐,下饭' AS highlight_tags, 35 AS cook_minutes, 2 AS serving_count,
    'sunset' AS cover_tone, '番茄牛腩' AS cover_text, 'published' AS status, 10 AS sort_order
  UNION ALL SELECT '小炒', '蒜香虾仁西兰花', '清爽低脂，一锅快炒',
    '虾仁和西兰花的组合很适合工作日晚餐，十几分钟就能上桌，口味清爽也很有满足感。',
    '鲜香,清爽', '低脂,快手,工作日晚餐', 18, 2, 'mint', '虾仁西兰花', 'published', 20
  UNION ALL SELECT '甜品饮品', '焦糖布丁', '饭后甜点，两人共享',
    '焦糖布丁细腻顺滑，冷热都好吃，做一份可以刚好分成两小杯。', '香甜,绵密', '甜品,共享,饭后', 40, 2, 'dessert', '焦糖布丁', 'published', 30
  UNION ALL SELECT '主食', '虾仁炒饭', '粒粒分明，厨房救星',
    '剩米饭加虾仁和鸡蛋快炒，是一顿高效又不敷衍的工作日晚餐。', '鲜香,家常', '主食,快手,炒饭', 15, 2, 'blue', '虾仁炒饭', 'published', 40
  UNION ALL SELECT '汤羹', '紫菜蛋花汤', '轻松补一碗热汤',
    '十分钟内就能完成的汤羹，适合搭配炒饭或小炒，让晚餐更完整。', '清淡,暖胃', '汤羹,快手,热汤', 10, 2, 'sky', '蛋花汤', 'published', 50
  UNION ALL SELECT '小炒', '凉拌黄瓜', '清爽解腻的配菜',
    '拍黄瓜简单爽口，适合和重口味热菜搭配，也能当夜宵配菜。', '清爽,酸辣', '凉菜,解腻,家常', 8, 2, 'green', '凉拌黄瓜', 'published', 60
  UNION ALL SELECT '主食', '奶油蘑菇意面', '奶香浓郁，适合周末',
    '意面煮好后和奶油蘑菇酱翻拌，浓郁有层次，适合想吃一点西式主食的时候。', '奶香,浓郁', '西式,意面,周末', 28, 2, 'cream', '奶油意面', 'pending', 70
  UNION ALL SELECT '主食', '照烧鸡腿饭', '一碗满足的盖饭',
    '鸡腿煎香后收照烧汁，搭配米饭和蔬菜就是完整的一餐。', '咸甜,下饭', '盖饭,便当,主食', 26, 2, 'amber', '照烧鸡腿', 'published', 80
  UNION ALL SELECT '主食', '土豆牛肉焖饭', '一锅出门的懒人主食',
    '米饭和土豆牛肉一起焖熟，肉汁渗透到米饭里，很适合做周末囤餐。', '浓郁,软糯', '焖饭,一锅出,囤餐', 32, 3, 'brown', '焖饭', 'published', 90
  UNION ALL SELECT '主食', '咖喱鸡肉饭', '香气足，超下饭',
    '鸡肉和土豆胡萝卜炖煮入味，配一碗热米饭就很满足。', '咖喱,浓香', '咖喱,主食,下饭', 30, 3, 'gold', '咖喱饭', 'draft', 100
  UNION ALL SELECT '小炒', '宫保鸡丁', '经典快炒，酸甜微辣',
    '鸡丁、花生和宫保酱汁搭配，口味层次丰富，很适合下饭。', '酸甜,微辣', '川味,快炒,下饭', 20, 2, 'red', '宫保鸡丁', 'published', 110
  UNION ALL SELECT '小炒', '青椒肉丝', '家常下饭标配',
    '青椒和瘦肉丝快炒，脆嫩爽口，适合搭配米饭。', '咸鲜,家常', '家常,快炒,下饭', 16, 2, 'jade', '青椒肉丝', 'published', 120
  UNION ALL SELECT '小炒', '蒜蓉生菜', '清爽解腻的快手素菜',
    '只需简单翻炒就能保留生菜脆感，适合与肉菜搭配。', '清淡,蒜香', '素菜,快手,搭配', 7, 2, 'green', '蒜蓉生菜', 'published', 130
  UNION ALL SELECT '小炒', '干煸四季豆', '香辣有嚼劲',
    '四季豆提前煸到起皱，和肉末一起炒香更下饭。', '香辣,干香', '家常,下饭,肉末', 18, 2, 'olive', '四季豆', 'pending', 140
  UNION ALL SELECT '汤羹', '菌菇鸡汤', '慢炖出来的鲜味',
    '菌菇和鸡肉一起炖煮，适合秋冬喝上一碗热汤。', '鲜香,暖胃', '炖汤,菌菇,秋冬', 45, 3, 'beige', '菌菇鸡汤', 'published', 150
  UNION ALL SELECT '汤羹', '玉米排骨汤', '甜润耐喝的家常汤',
    '排骨焯水后和玉米慢炖，汤色清亮，口味鲜甜。', '鲜甜,暖胃', '排骨汤,家常,慢炖', 50, 3, 'yellow', '排骨汤', 'published', 160
  UNION ALL SELECT '汤羹', '南瓜浓汤', '细腻顺滑的西式汤羹',
    '南瓜和牛奶打碎后煮成浓汤，口感细腻，适合搭配面包。', '香甜,顺滑', '浓汤,西式,早餐', 25, 2, 'pumpkin', '南瓜浓汤', 'draft', 170
  UNION ALL SELECT '甜品饮品', '柠檬气泡水', '饭后解腻小饮品',
    '加了柠檬片和冰块的气泡水，适合油腻餐后解腻。', '清爽,酸甜', '饮品,解腻,下午茶', 5, 2, 'lemon', '气泡水', 'published', 180
  UNION ALL SELECT '甜品饮品', '热可可', '奶香浓郁的暖心饮品',
    '可可粉和牛奶煮热后口感浓郁，适合晚上宅家。', '香甜,醇厚', '热饮,冬日,甜品', 12, 2, 'choco', '热可可', 'published', 190
) seed
  ON seed.category_name = c.name
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  subtitle = VALUES(subtitle),
  description = VALUES(description),
  taste_tags = VALUES(taste_tags),
  highlight_tags = VALUES(highlight_tags),
  cook_minutes = VALUES(cook_minutes),
  serving_count = VALUES(serving_count),
  cover_tone = VALUES(cover_tone),
  cover_text = VALUES(cover_text),
  status = VALUES(status),
  sort_order = VALUES(sort_order);

DELETE di
FROM food_dish_ingredients di
JOIN food_dishes d ON d.id = di.dish_id
JOIN users u ON u.id = d.user_id
WHERE u.username = 'admin';

INSERT INTO food_dish_ingredients (dish_id, ingredient_id, ingredient_name, amount, sort_order)
SELECT
  d.id,
  i.id,
  seed.ingredient_name,
  seed.amount,
  seed.sort_order
FROM food_dishes d
JOIN users u ON u.id = d.user_id
JOIN (
  SELECT '番茄牛腩锅' AS dish_name, '牛腩' AS ingredient_name, '500g' AS amount, 10 AS sort_order
  UNION ALL SELECT '番茄牛腩锅', '番茄', '3个', 20
  UNION ALL SELECT '番茄牛腩锅', '洋葱', '半个', 30
  UNION ALL SELECT '番茄牛腩锅', '土豆', '2个', 40
  UNION ALL SELECT '蒜香虾仁西兰花', '虾仁', '250g', 10
  UNION ALL SELECT '蒜香虾仁西兰花', '西兰花', '1朵', 20
  UNION ALL SELECT '蒜香虾仁西兰花', '黑胡椒', '少许', 30
  UNION ALL SELECT '焦糖布丁', '牛奶', '300ml', 10
  UNION ALL SELECT '焦糖布丁', '鸡蛋', '2个', 20
  UNION ALL SELECT '焦糖布丁', '砂糖', '40g', 30
  UNION ALL SELECT '虾仁炒饭', '虾仁', '180g', 10
  UNION ALL SELECT '虾仁炒饭', '鸡蛋', '2个', 20
  UNION ALL SELECT '虾仁炒饭', '米饭', '2碗', 30
  UNION ALL SELECT '紫菜蛋花汤', '鸡蛋', '1个', 10
  UNION ALL SELECT '紫菜蛋花汤', '紫菜', '1把', 20
  UNION ALL SELECT '凉拌黄瓜', '黄瓜', '2根', 10
) seed
  ON seed.dish_name = d.name
LEFT JOIN food_ingredients i
  ON i.user_id = d.user_id
 AND i.name = seed.ingredient_name
WHERE u.username = 'admin';

DELETE ds
FROM food_dish_steps ds
JOIN food_dishes d ON d.id = ds.dish_id
JOIN users u ON u.id = d.user_id
WHERE u.username = 'admin';

INSERT INTO food_dish_steps (dish_id, step_no, content)
SELECT
  d.id,
  seed.step_no,
  seed.content
FROM food_dishes d
JOIN users u ON u.id = d.user_id
JOIN (
  SELECT '番茄牛腩锅' AS dish_name, 1 AS step_no, '牛腩焯水后备用，番茄切块、土豆切丁。' AS content
  UNION ALL SELECT '番茄牛腩锅', 2, '热锅炒香洋葱，加入番茄炒出汁，再放入牛腩翻炒。'
  UNION ALL SELECT '番茄牛腩锅', 3, '加入热水和土豆，小火炖煮 25 分钟，出锅前调味。'
  UNION ALL SELECT '蒜香虾仁西兰花', 1, '虾仁用盐和黑胡椒抓匀，西兰花焯水断生。'
  UNION ALL SELECT '蒜香虾仁西兰花', 2, '蒜末下锅爆香，放入虾仁煎至变色。'
  UNION ALL SELECT '蒜香虾仁西兰花', 3, '加入西兰花快速翻炒，最后调味出锅。'
  UNION ALL SELECT '焦糖布丁', 1, '鸡蛋打散，牛奶与砂糖加热到微温后混合。'
  UNION ALL SELECT '焦糖布丁', 2, '布丁液过筛后倒入耐热容器，放入烤盘。'
  UNION ALL SELECT '焦糖布丁', 3, '烤好冷却后淋上焦糖液，冷藏口感更佳。'
  UNION ALL SELECT '虾仁炒饭', 1, '虾仁和鸡蛋分别炒散备用。'
  UNION ALL SELECT '虾仁炒饭', 2, '冷饭下锅炒松后加入虾仁鸡蛋和调味料。'
  UNION ALL SELECT '虾仁炒饭', 3, '翻炒均匀后撒葱花出锅。'
  UNION ALL SELECT '紫菜蛋花汤', 1, '锅中烧开水，加入紫菜煮 1 分钟。'
  UNION ALL SELECT '紫菜蛋花汤', 2, '淋入蛋液形成蛋花，调味后关火。'
  UNION ALL SELECT '凉拌黄瓜', 1, '黄瓜拍碎切段，加盐腌 10 分钟。'
  UNION ALL SELECT '凉拌黄瓜', 2, '倒掉多余水分，加入蒜末、醋和辣椒油拌匀。'
) seed
  ON seed.dish_name = d.name
WHERE u.username = 'admin';
