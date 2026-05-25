-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/022_create_photography_orders.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

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
  (NULL, '摄影收入', 'income', 'camera', '#1D4ED8', 1, 30, '摄影订单产生的订金与尾款收入')
ON DUPLICATE KEY UPDATE
  icon = VALUES(icon),
  color = VALUES(color),
  is_system = VALUES(is_system),
  sort_order = VALUES(sort_order),
  status = 'active',
  remark = VALUES(remark);

CREATE TABLE IF NOT EXISTS photography_orders (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '摄影订单ID',
  order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  customer_name VARCHAR(80) NOT NULL COMMENT '客户姓名',
  contact_info VARCHAR(120) NULL COMMENT '联系方式',
  order_type ENUM('first_birthday', 'hundred_days', 'engagement', 'thanks_banquet', 'wedding', 'graduation') NOT NULL COMMENT '订单类型',
  shoot_at DATETIME(3) NOT NULL COMMENT '拍摄时间',
  status ENUM('pending', 'shot') NOT NULL DEFAULT 'pending' COMMENT '订单状态：pending未拍摄，shot已拍摄',
  total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
  deposit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '订金金额',
  final_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '尾款金额',
  deposit_account_id BIGINT UNSIGNED NULL COMMENT '订金入账现金账户ID',
  deposit_transaction_id BIGINT UNSIGNED NULL COMMENT '订金关联流水ID',
  deposit_received_at DATETIME(3) NULL COMMENT '订金到账时间',
  final_account_id BIGINT UNSIGNED NULL COMMENT '尾款入账现金账户ID',
  final_transaction_id BIGINT UNSIGNED NULL COMMENT '尾款关联流水ID',
  final_received_at DATETIME(3) NULL COMMENT '尾款到账时间',
  address VARCHAR(255) NULL COMMENT '拍摄地址',
  remark VARCHAR(500) NULL COMMENT '备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_photography_orders_user_no (user_id, order_no),
  KEY idx_photography_orders_user_status_time (user_id, status, shoot_at),
  KEY idx_photography_orders_user_type_time (user_id, order_type, shoot_at),
  KEY idx_photography_orders_deposit_account (deposit_account_id),
  KEY idx_photography_orders_final_account (final_account_id),
  KEY idx_photography_orders_deposit_transaction (deposit_transaction_id),
  KEY idx_photography_orders_final_transaction (final_transaction_id),
  CONSTRAINT fk_photography_orders_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_photography_orders_deposit_account
    FOREIGN KEY (deposit_account_id) REFERENCES accounts (id),
  CONSTRAINT fk_photography_orders_final_account
    FOREIGN KEY (final_account_id) REFERENCES accounts (id),
  CONSTRAINT fk_photography_orders_deposit_transaction
    FOREIGN KEY (deposit_transaction_id) REFERENCES transactions (id),
  CONSTRAINT fk_photography_orders_final_transaction
    FOREIGN KEY (final_transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄影订单表：记录客户档期、订金、尾款与到账情况';

INSERT INTO photography_orders (
  order_no,
  user_id,
  customer_name,
  contact_info,
  order_type,
  shoot_at,
  status,
  total_amount,
  deposit_amount,
  final_amount,
  address,
  remark,
  sort_order
)
SELECT
  seed.order_no,
  u.id,
  seed.customer_name,
  seed.contact_info,
  seed.order_type,
  seed.shoot_at,
  seed.status,
  seed.total_amount,
  seed.deposit_amount,
  seed.final_amount,
  seed.address,
  seed.remark,
  seed.sort_order
FROM users u
JOIN (
  SELECT
    'PHOTO202605010001' AS order_no,
    '林小姐' AS customer_name,
    '138****2001 / 微信同号' AS contact_info,
    'wedding' AS order_type,
    TIMESTAMP('2026-06-08 10:30:00.000') AS shoot_at,
    'pending' AS status,
    8800.00 AS total_amount,
    3600.00 AS deposit_amount,
    5200.00 AS final_amount,
    '星河酒店宴会厅' AS address,
    '双机位 + 晚宴跟拍' AS remark,
    10 AS sort_order
  UNION ALL
  SELECT
    'PHOTO202605010002',
    '周宝宝',
    '138****2001 / 妈妈同号',
    'first_birthday',
    TIMESTAMP('2026-05-29 14:00:00.000'),
    'pending',
    2600.00,
    800.00,
    1800.00,
    '橙子摄影棚',
    '棚拍 + 家庭合影',
    20
) seed
WHERE u.username = 'admin'
ON DUPLICATE KEY UPDATE
  customer_name = VALUES(customer_name),
  contact_info = VALUES(contact_info),
  order_type = VALUES(order_type),
  shoot_at = VALUES(shoot_at),
  status = VALUES(status),
  total_amount = VALUES(total_amount),
  deposit_amount = VALUES(deposit_amount),
  final_amount = VALUES(final_amount),
  address = VALUES(address),
  remark = VALUES(remark),
  sort_order = VALUES(sort_order);
