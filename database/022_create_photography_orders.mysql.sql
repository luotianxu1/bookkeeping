-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/022_create_photography_orders.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS photography_orders (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '摄影订单ID',
  order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  customer_name VARCHAR(80) NOT NULL COMMENT '客户姓名',
  contact_info VARCHAR(120) NULL COMMENT '联系方式',
  order_type ENUM('first_birthday', 'hundred_days', 'engagement', 'thanks_banquet', 'wedding', 'marriage_registration', 'graduation') NOT NULL COMMENT '订单类型',
  shoot_at DATETIME(3) NOT NULL COMMENT '拍摄时间',
  status ENUM('pending', 'shot', 'cancelled') NOT NULL DEFAULT 'pending' COMMENT '订单状态：pending未拍摄，shot已拍摄，cancelled已取消',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='摄影订单表：记录客户档期、订金、尾款与到账情况';
