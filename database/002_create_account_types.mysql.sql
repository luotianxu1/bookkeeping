-- MySQL 5.7+/8.0+
-- Run this file with:
--   mysql -u root -p < database/002_create_account_types.mysql.sql

USE bookkeeping_app;

CREATE TABLE IF NOT EXISTS account_types (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '账户类型ID',
  code VARCHAR(64) NOT NULL COMMENT '账户类型编码，程序中使用，例如 cash、investment、gold',
  name VARCHAR(64) NOT NULL COMMENT '账户类型名称，页面展示使用',
  category ENUM('asset', 'liability', 'relation', 'other') NOT NULL DEFAULT 'asset' COMMENT '账户大类：资产、负债、人情、其他',
  balance_direction ENUM('debit', 'credit') NOT NULL DEFAULT 'debit' COMMENT '余额方向：debit资产增加，credit负债增加',
  include_in_net_worth_default TINYINT(1) NOT NULL DEFAULT 1 COMMENT '新增账户时是否默认计入总资产',
  allow_overdraft TINYINT(1) NOT NULL DEFAULT 0 COMMENT '该账户类型是否允许透支',
  is_system TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否系统内置类型',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status ENUM('active', 'disabled') NOT NULL DEFAULT 'active' COMMENT '状态',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_account_types_code (code),
  KEY idx_account_types_category (category),
  KEY idx_account_types_status_sort (status, sort_order)
) ENGINE=InnoDB COMMENT='账户类型表：支撑账户管理中的账户类型配置';

INSERT INTO account_types (
  code,
  name,
  category,
  balance_direction,
  include_in_net_worth_default,
  allow_overdraft,
  is_system,
  sort_order,
  remark
) VALUES
  ('cash', '现金', 'asset', 'debit', 1, 0, 1, 10, '钱包、现金、储蓄卡、第三方钱包等流动资产'),
  ('investment', '投资', 'asset', 'debit', 1, 0, 1, 20, '基金、股票等投资账户'),
  ('gold', '黄金', 'asset', 'debit', 1, 0, 1, 30, '实物黄金、积存金、纸黄金等黄金账户'),
  ('credit_card', '信用卡', 'liability', 'credit', 1, 1, 1, 40, '信用卡、花呗等可透支账户'),
  ('debt', '债务', 'liability', 'credit', 1, 0, 1, 50, '联系人之间的借入借出往来账户'),
  ('liability', '负债', 'liability', 'credit', 1, 0, 1, 60, '房贷、车贷等长期待还负债账户'),
  ('human_relation', '人情', 'relation', 'debit', 0, 0, 1, 70, '红包、礼金、人情往来'),
  ('other_asset', '其他资产', 'asset', 'debit', 1, 0, 1, 80, '不属于现金、投资或黄金的其他资产'),
  ('other_liability', '其他负债', 'liability', 'credit', 1, 0, 1, 90, '不属于借款或信用卡的其他负债'),
  ('other', '其他', 'other', 'debit', 0, 0, 1, 100, '无法归类的账户类型')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  category = VALUES(category),
  balance_direction = VALUES(balance_direction),
  include_in_net_worth_default = VALUES(include_in_net_worth_default),
  allow_overdraft = VALUES(allow_overdraft),
  is_system = VALUES(is_system),
  sort_order = VALUES(sort_order),
  status = 'active',
  remark = VALUES(remark);
