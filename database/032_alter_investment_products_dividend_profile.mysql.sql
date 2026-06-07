USE bookkeeping_app;

ALTER TABLE investment_products
  ADD COLUMN IF NOT EXISTS is_stable_dividend TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为持续分红标的' AFTER price_precision,
  ADD COLUMN IF NOT EXISTS predicted_annual_dividend_per_unit DECIMAL(18, 6) NOT NULL DEFAULT 0.000000 COMMENT '预测年化每单位分红' AFTER is_stable_dividend,
  ADD COLUMN IF NOT EXISTS dividend_stable_years SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '近年持续分红年数' AFTER predicted_annual_dividend_per_unit,
  ADD COLUMN IF NOT EXISTS dividend_last_paid_date DATE NULL COMMENT '最近一次历史分红日期' AFTER dividend_stable_years,
  ADD COLUMN IF NOT EXISTS dividend_data_source VARCHAR(80) NULL COMMENT '分红画像数据来源' AFTER dividend_last_paid_date,
  ADD COLUMN IF NOT EXISTS dividend_evaluated_at DATETIME(3) NULL COMMENT '持续分红画像最近评估时间' AFTER dividend_data_source;
