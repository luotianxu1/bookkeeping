USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS investment_auto_invest_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '定投计划ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '投资账户ID',
  position_id BIGINT UNSIGNED NOT NULL COMMENT '投资持仓ID',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '投资产品ID',
  funding_account_id BIGINT UNSIGNED NOT NULL COMMENT '扣款资金账户ID',
  frequency ENUM('daily', 'weekly', 'monthly') NOT NULL COMMENT '定投周期',
  amount DECIMAL(18, 2) NOT NULL COMMENT '定投金额',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  next_execute_date DATE NOT NULL COMMENT '下次执行日期',
  last_executed_at DATETIME(3) NULL COMMENT '最近执行时间',
  status ENUM('active', 'paused', 'cancelled') NOT NULL DEFAULT 'active' COMMENT '状态',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_investment_auto_invest_plans_user_status (user_id, status),
  KEY idx_investment_auto_invest_plans_position_status (position_id, status),
  KEY idx_investment_auto_invest_plans_execute_date (next_execute_date, status),
  CONSTRAINT fk_investment_auto_invest_plans_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_investment_auto_invest_plans_account
    FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_investment_auto_invest_plans_position
    FOREIGN KEY (position_id) REFERENCES investment_positions (id),
  CONSTRAINT fk_investment_auto_invest_plans_product
    FOREIGN KEY (product_id) REFERENCES investment_products (id),
  CONSTRAINT fk_investment_auto_invest_plans_funding_account
    FOREIGN KEY (funding_account_id) REFERENCES accounts (id),
  CONSTRAINT chk_investment_auto_invest_plans_amount_positive
    CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金定投计划表：支持周期性自动申购';
