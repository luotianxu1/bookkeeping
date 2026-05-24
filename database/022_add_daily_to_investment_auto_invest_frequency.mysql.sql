ALTER TABLE investment_auto_invest_plans
  MODIFY COLUMN frequency ENUM('daily', 'weekly', 'monthly') NOT NULL COMMENT '定投周期';
