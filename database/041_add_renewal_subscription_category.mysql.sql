-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/041_add_renewal_subscription_category.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_renewal_subscription_category;

DELIMITER //

CREATE PROCEDURE migrate_renewal_subscription_category()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'renewal_subscriptions'
      AND COLUMN_NAME = 'category_id'
  ) THEN
    ALTER TABLE renewal_subscriptions
      ADD COLUMN category_id BIGINT UNSIGNED NULL COMMENT '扣款时使用的支出分类ID' AFTER funding_account_id;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'renewal_subscriptions'
      AND INDEX_NAME = 'idx_renewals_category'
  ) THEN
    ALTER TABLE renewal_subscriptions
      ADD KEY idx_renewals_category (category_id);
  END IF;

  ALTER TABLE renewal_subscriptions
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '固定支出ID',
    MODIFY COLUMN name VARCHAR(80) NOT NULL COMMENT '固定支出名称，例如 房租、会员、保险',
    MODIFY COLUMN provider_name VARCHAR(80) NULL COMMENT '收款方名称',
    MODIFY COLUMN amount DECIMAL(18, 2) NOT NULL COMMENT '每期支出金额',
    MODIFY COLUMN funding_account_id BIGINT UNSIGNED NOT NULL COMMENT '扣款资金账户ID，必须为现金账户',
    MODIFY COLUMN billing_day TINYINT UNSIGNED NOT NULL COMMENT '每月支出日，取值1到31',
    MODIFY COLUMN billing_cycle ENUM('monthly', 'quarterly', 'yearly') NOT NULL DEFAULT 'monthly' COMMENT '支出周期：monthly按月，quarterly按季，yearly按年',
    MODIFY COLUMN next_billing_date DATE NOT NULL COMMENT '下次支出日期',
    MODIFY COLUMN last_charged_at DATETIME(3) NULL COMMENT '最近一次成功扣款时间',
    MODIFY COLUMN last_transaction_id BIGINT UNSIGNED NULL COMMENT '最近一次扣款生成的流水ID',
    MODIFY COLUMN last_charge_status ENUM('idle', 'success', 'failed') NOT NULL DEFAULT 'idle' COMMENT '最近一次扣款状态',
    MODIFY COLUMN last_charge_message VARCHAR(255) NULL COMMENT '最近一次扣款结果描述',
    MODIFY COLUMN status ENUM('active', 'paused', 'cancelled') NOT NULL DEFAULT 'active' COMMENT '固定支出状态';

  ALTER TABLE renewal_subscriptions
    COMMENT = '固定支出表：记录房租、会员、保险等周期性自动扣款项目';

  ALTER TABLE renewal_subscriptions
    MODIFY COLUMN category_id BIGINT UNSIGNED NULL COMMENT '扣款时使用的支出分类ID，新增和修改时必填';

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'renewal_subscriptions'
      AND CONSTRAINT_NAME = 'fk_renewals_category'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  ) THEN
    ALTER TABLE renewal_subscriptions
      ADD CONSTRAINT fk_renewals_category
        FOREIGN KEY (category_id) REFERENCES categories (id);
  END IF;

  UPDATE renewal_subscriptions renewal_main
  JOIN users user_main
    ON user_main.id = renewal_main.user_id
    AND user_main.username = 'admin'
  SET renewal_main.name = '房租',
      renewal_main.provider_name = '房东',
      renewal_main.amount = 3500.00,
      renewal_main.remark = '每月固定房租'
  WHERE renewal_main.name = '腾讯视频会员'
    AND renewal_main.provider_name = '腾讯视频'
    AND renewal_main.amount = 25.00;

  UPDATE renewal_subscriptions renewal_main
  JOIN users user_main
    ON user_main.id = renewal_main.user_id
    AND user_main.username = 'admin'
  SET renewal_main.name = '手机话费',
      renewal_main.provider_name = '运营商',
      renewal_main.amount = 99.00,
      renewal_main.remark = '固定套餐月费'
  WHERE renewal_main.name = 'iCloud+ 200GB'
    AND renewal_main.provider_name = 'Apple'
    AND renewal_main.amount = 21.00;

  UPDATE renewal_subscriptions renewal_main
  JOIN users user_main
    ON user_main.id = renewal_main.user_id
    AND user_main.username = 'admin'
  SET renewal_main.remark = '暂停中的按月固定支出'
  WHERE renewal_main.name = '健身会员'
    AND renewal_main.provider_name = '超级猩猩'
    AND renewal_main.amount = 39.00
    AND renewal_main.remark = '暂停中的按月续费';
END//

DELIMITER ;

CALL migrate_renewal_subscription_category();

DROP PROCEDURE IF EXISTS migrate_renewal_subscription_category;
