-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/001_create_database_and_users.mysql.sql

CREATE DATABASE IF NOT EXISTS bookkeeping_app
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE bookkeeping_app;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录用户名；后台用户管理列表展示与查询字段',
  phone VARCHAR(32) NULL COMMENT '手机号；移动端验证码登录使用',
  email VARCHAR(255) NULL COMMENT '邮箱',
  password_hash VARCHAR(100) NULL COMMENT '密码哈希；不存储明文密码',
  display_name VARCHAR(80) NOT NULL COMMENT '展示昵称，例如个人页中的用户姓名',
  avatar_url VARCHAR(512) NULL COMMENT '头像地址',
  status ENUM('active', 'disabled', 'locked') NOT NULL DEFAULT 'active' COMMENT '用户状态',
  default_login_provider ENUM('password', 'sms_code', 'wechat') NOT NULL DEFAULT 'password' COMMENT '默认登录方式',
  role_name VARCHAR(64) NOT NULL DEFAULT 'user' COMMENT '当前先用角色名支撑后台列表；后续可拆为 roles/user_roles',
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '用户时区',
  locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '用户语言',
  last_login_at DATETIME(3) NULL COMMENT '最近登录时间',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_phone (phone),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_status (status),
  KEY idx_users_role_name (role_name),
  CONSTRAINT chk_users_login_identifier
    CHECK (username <> '' OR phone IS NOT NULL OR email IS NOT NULL),
  CONSTRAINT chk_users_password_for_password_login
    CHECK (default_login_provider <> 'password' OR password_hash IS NOT NULL)
) ENGINE=InnoDB COMMENT='用户表：支撑移动端登录、个人中心与PC后台用户管理';

INSERT INTO users (
  username,
  phone,
  password_hash,
  display_name,
  role_name
) VALUES (
  'admin',
  '13800000000',
  '$2y$10$gmOiPZ68ELEqP7gu3ilHHezvfFsqlOohmzJYD0xejorkGR/pz/PRy',
  '罗天旭',
  'super_admin'
) ON DUPLICATE KEY UPDATE
  phone = VALUES(phone),
  display_name = VALUES(display_name),
  role_name = VALUES(role_name);
