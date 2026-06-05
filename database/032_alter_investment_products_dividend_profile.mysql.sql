USE bookkeeping_app;

SET @ddl = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bookkeeping_app'
      AND TABLE_NAME = 'investment_products'
      AND COLUMN_NAME = 'is_stable_dividend'
  ),
  'SELECT 1',
  'ALTER TABLE investment_products ADD COLUMN is_stable_dividend TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否为持续分红标的'' AFTER price_precision'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bookkeeping_app'
      AND TABLE_NAME = 'investment_products'
      AND COLUMN_NAME = 'predicted_annual_dividend_per_unit'
  ),
  'SELECT 1',
  'ALTER TABLE investment_products ADD COLUMN predicted_annual_dividend_per_unit DECIMAL(18, 6) NOT NULL DEFAULT 0.000000 COMMENT ''预测年化每单位分红'' AFTER is_stable_dividend'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bookkeeping_app'
      AND TABLE_NAME = 'investment_products'
      AND COLUMN_NAME = 'dividend_stable_years'
  ),
  'SELECT 1',
  'ALTER TABLE investment_products ADD COLUMN dividend_stable_years SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''近年持续分红年数'' AFTER predicted_annual_dividend_per_unit'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bookkeeping_app'
      AND TABLE_NAME = 'investment_products'
      AND COLUMN_NAME = 'dividend_last_paid_date'
  ),
  'SELECT 1',
  'ALTER TABLE investment_products ADD COLUMN dividend_last_paid_date DATE NULL COMMENT ''最近一次历史分红日期'' AFTER dividend_stable_years'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bookkeeping_app'
      AND TABLE_NAME = 'investment_products'
      AND COLUMN_NAME = 'dividend_data_source'
  ),
  'SELECT 1',
  'ALTER TABLE investment_products ADD COLUMN dividend_data_source VARCHAR(80) NULL COMMENT ''分红画像数据来源'' AFTER dividend_last_paid_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bookkeeping_app'
      AND TABLE_NAME = 'investment_products'
      AND COLUMN_NAME = 'dividend_evaluated_at'
  ),
  'SELECT 1',
  'ALTER TABLE investment_products ADD COLUMN dividend_evaluated_at DATETIME(3) NULL COMMENT ''持续分红画像最近评估时间'' AFTER dividend_data_source'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
