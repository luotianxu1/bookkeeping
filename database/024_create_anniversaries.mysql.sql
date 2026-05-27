-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/024_create_anniversaries.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS anniversaries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '纪念日ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  title VARCHAR(120) NOT NULL COMMENT '纪念日名称',
  anniversary_date DATE NOT NULL COMMENT '纪念日期',
  remark VARCHAR(500) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_anniversaries_user_title_date (user_id, title, anniversary_date),
  KEY idx_anniversaries_user_date (user_id, anniversary_date),
  KEY idx_anniversaries_user_sort (user_id, sort_order),
  CONSTRAINT fk_anniversaries_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='纪念日表：记录每年循环提醒的重要日期';

INSERT INTO anniversaries (
  user_id,
  title,
  anniversary_date,
  remark,
  sort_order
)
SELECT
  u.id,
  seed.title,
  seed.anniversary_date,
  seed.remark,
  seed.sort_order
FROM users u
JOIN (
  SELECT
    '恋爱纪念日' AS title,
    DATE('2026-04-14') AS anniversary_date,
    '提前订花，晚上一起吃顿喜欢的餐厅' AS remark,
    10 AS sort_order
  UNION ALL
  SELECT
    '第一次旅行纪念日',
    DATE('2026-04-30'),
    '把照片洗出来，顺便定下一次短途旅行',
    20
  UNION ALL
  SELECT
    '她的生日',
    DATE('2026-06-06'),
    '蛋糕和礼物都要提前一周准备',
    30
) seed
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  remark = VALUES(remark),
  sort_order = VALUES(sort_order);
