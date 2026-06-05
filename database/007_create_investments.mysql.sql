-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p < database/007_create_investments.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS investment_products (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '投资产品ID',
  product_type ENUM('stock', 'fund', 'bond', 'gold', 'other') NOT NULL COMMENT '产品类型：stock股票，fund基金，bond债券，gold黄金，other其他',
  market VARCHAR(32) NULL COMMENT '市场，例如 CN、HK、US、FUND',
  exchange_code VARCHAR(32) NULL COMMENT '交易所编码，例如 SSE、SZSE、HKEX、NASDAQ',
  symbol VARCHAR(64) NOT NULL COMMENT '产品代码，例如 600519、000001、AAPL',
  name VARCHAR(120) NOT NULL COMMENT '产品名称',
  short_name VARCHAR(80) NULL COMMENT '简称',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '计价币种',
  unit_name VARCHAR(16) NOT NULL DEFAULT '份' COMMENT '持仓单位，例如 股、份、克',
  price_precision TINYINT UNSIGNED NOT NULL DEFAULT 4 COMMENT '价格精度',
  is_stable_dividend TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为持续分红标的',
  predicted_annual_dividend_per_unit DECIMAL(18, 6) NOT NULL DEFAULT 0.000000 COMMENT '预测年化每单位分红',
  dividend_stable_years SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '近年持续分红年数',
  dividend_last_paid_date DATE NULL COMMENT '最近一次历史分红日期',
  dividend_data_source VARCHAR(80) NULL COMMENT '分红画像数据来源',
  dividend_evaluated_at DATETIME(3) NULL COMMENT '持续分红画像最近评估时间',
  status ENUM('active', 'disabled') NOT NULL DEFAULT 'active' COMMENT '状态',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_investment_products_type_symbol_market (product_type, symbol, market),
  KEY idx_investment_products_type_status (product_type, status),
  KEY idx_investment_products_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资产品表：股票、基金、债券、黄金等投资标的主数据';

CREATE TABLE IF NOT EXISTS investment_positions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '投资持仓ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '所属投资账户ID，关联accounts',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '投资产品ID',
  holding_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 COMMENT '当前持仓数量',
  available_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 COMMENT '可用数量',
  frozen_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 COMMENT '冻结数量',
  cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓成本金额',
  avg_cost_price DECIMAL(18, 6) NOT NULL DEFAULT 0.000000 COMMENT '持仓均价',
  current_price DECIMAL(18, 6) NOT NULL DEFAULT 0.000000 COMMENT '当前价格',
  market_value DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '当前市值',
  day_profit DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '今日盈亏',
  day_profit_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '今日盈亏率，百分比值',
  holding_profit DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓盈亏',
  holding_profit_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '持仓盈亏率，百分比值',
  cumulative_profit DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '累计盈亏，包含已清仓收益与分红',
  cumulative_profit_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '累计盈亏率，百分比值',
  include_in_net_worth TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否计入总资产',
  status ENUM('active', 'closed', 'disabled') NOT NULL DEFAULT 'active' COMMENT '持仓状态：active持仓中，closed已清仓',
  subscription_status ENUM('confirmed', 'pending') NOT NULL DEFAULT 'confirmed' COMMENT '基金申购状态：confirmed已确认，pending待确认',
  subscription_applied_date DATE NULL COMMENT '基金申购申请日',
  subscription_expected_confirm_date DATE NULL COMMENT '基金预计确认日期',
  subscription_confirmed_at DATETIME(3) NULL COMMENT '基金申购确认时间',
  last_synced_at DATETIME(3) NULL COMMENT '行情或估值最近同步时间',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_investment_positions_account_product_status (account_id, product_id, status),
  KEY idx_investment_positions_user_account (user_id, account_id, status),
  KEY idx_investment_positions_product (product_id),
  CONSTRAINT fk_investment_positions_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_investment_positions_account
    FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_investment_positions_product
    FOREIGN KEY (product_id) REFERENCES investment_products (id),
  CONSTRAINT chk_investment_positions_quantity_nonnegative
    CHECK (holding_quantity >= 0 AND available_quantity >= 0 AND frozen_quantity >= 0),
  CONSTRAINT chk_investment_positions_amount_nonnegative
    CHECK (cost_amount >= 0 AND market_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资持仓表：支撑投资账户列表、投资详情、收益预测与总市值统计';

CREATE TABLE IF NOT EXISTS investment_transactions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '投资交易流水ID',
  transaction_no VARCHAR(64) NOT NULL COMMENT '投资流水编号，用于幂等和展示',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '所属投资账户ID',
  position_id BIGINT UNSIGNED NULL COMMENT '关联持仓ID，建仓前可为空',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '投资产品ID',
  trade_type ENUM('buy', 'sell', 'add', 'reduce', 'dividend_reinvest', 'fee_adjust', 'split_adjust') NOT NULL COMMENT '交易类型：买入、卖出、加仓、减仓、分红再投、费用调整、拆分调整',
  quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 COMMENT '交易数量，买入/加仓为正，卖出/减仓也存正数',
  price DECIMAL(18, 6) NOT NULL DEFAULT 0.000000 COMMENT '成交价格',
  amount DECIMAL(18, 2) NOT NULL COMMENT '成交金额，统一存正数',
  fee_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '手续费',
  tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '税费',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  funding_account_id BIGINT UNSIGNED NULL COMMENT '关联资金账户ID',
  trade_at DATETIME(3) NOT NULL COMMENT '交易时间',
  status ENUM('normal', 'voided') NOT NULL DEFAULT 'normal' COMMENT '状态：normal正常，voided已作废',
  settlement_status ENUM('confirmed', 'pending') NOT NULL DEFAULT 'confirmed' COMMENT '基金交易结算状态：confirmed已确认，pending待确认',
  settlement_applied_date DATE NULL COMMENT '基金交易申请日',
  settlement_expected_date DATE NULL COMMENT '基金交易预计确认日',
  settlement_confirmed_at DATETIME(3) NULL COMMENT '基金交易确认时间',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_investment_transactions_user_no (user_id, transaction_no),
  KEY idx_investment_transactions_account_time (account_id, trade_at),
  KEY idx_investment_transactions_position_time (position_id, trade_at),
  KEY idx_investment_transactions_product_time (product_id, trade_at),
  KEY idx_investment_transactions_user_type_time (user_id, trade_type, trade_at),
  KEY idx_investment_transactions_funding_status (funding_account_id, settlement_status),
  KEY idx_investment_transactions_settlement_date (settlement_status, settlement_expected_date),
  CONSTRAINT fk_investment_transactions_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_investment_transactions_account
    FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_investment_transactions_position
    FOREIGN KEY (position_id) REFERENCES investment_positions (id),
  CONSTRAINT fk_investment_transactions_product
    FOREIGN KEY (product_id) REFERENCES investment_products (id),
  CONSTRAINT fk_investment_transactions_funding_account
    FOREIGN KEY (funding_account_id) REFERENCES accounts (id),
  CONSTRAINT chk_investment_transactions_amount_positive
    CHECK (amount > 0),
  CONSTRAINT chk_investment_transactions_quantity_nonnegative
    CHECK (quantity >= 0),
  CONSTRAINT chk_investment_transactions_fee_nonnegative
    CHECK (fee_amount >= 0 AND tax_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资交易流水表：记录建仓、加仓、减仓、分红再投等明细';

CREATE TABLE IF NOT EXISTS investment_price_quotes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '投资产品行情ID',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '投资产品ID',
  quote_date DATE NOT NULL COMMENT '行情日期',
  quote_time DATETIME(3) NULL COMMENT '行情时间',
  open_price DECIMAL(18, 6) NULL COMMENT '开盘价',
  high_price DECIMAL(18, 6) NULL COMMENT '最高价',
  low_price DECIMAL(18, 6) NULL COMMENT '最低价',
  close_price DECIMAL(18, 6) NULL COMMENT '收盘价',
  latest_price DECIMAL(18, 6) NOT NULL COMMENT '最新价或估值',
  pre_close_price DECIMAL(18, 6) NULL COMMENT '前收盘价',
  change_amount DECIMAL(18, 6) NOT NULL DEFAULT 0.000000 COMMENT '涨跌额',
  change_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '涨跌幅，百分比值',
  source VARCHAR(80) NULL COMMENT '行情来源',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_investment_price_quotes_product_date (product_id, quote_date),
  KEY idx_investment_price_quotes_product_time (product_id, quote_time),
  CONSTRAINT fk_investment_price_quotes_product
    FOREIGN KEY (product_id) REFERENCES investment_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资行情快照表：支撑市值同步、今日盈亏和收益预测';

CREATE TABLE IF NOT EXISTS investment_dividend_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分红计划ID',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '投资产品ID',
  dividend_year SMALLINT UNSIGNED NOT NULL COMMENT '分红年度',
  ex_dividend_date DATE NULL COMMENT '除权除息日',
  record_date DATE NULL COMMENT '股权登记日',
  pay_date DATE NULL COMMENT '派息日',
  dividend_per_unit DECIMAL(18, 6) NOT NULL COMMENT '每单位分红金额',
  tax_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '税率，百分比值',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  status ENUM('planned', 'confirmed', 'paid', 'cancelled') NOT NULL DEFAULT 'planned' COMMENT '状态',
  source VARCHAR(80) NULL COMMENT '数据来源',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_investment_dividend_plans_product_year_pay (product_id, dividend_year, pay_date),
  KEY idx_investment_dividend_plans_pay_date (pay_date, status),
  CONSTRAINT fk_investment_dividend_plans_product
    FOREIGN KEY (product_id) REFERENCES investment_products (id),
  CONSTRAINT chk_investment_dividend_plans_amount_positive
    CHECK (dividend_per_unit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资分红计划表：支撑攒股收息页面的预估分红';

CREATE TABLE IF NOT EXISTS investment_dividend_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分红记录ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '所属投资账户ID',
  position_id BIGINT UNSIGNED NULL COMMENT '关联持仓ID',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '投资产品ID',
  plan_id BIGINT UNSIGNED NULL COMMENT '关联分红计划ID',
  dividend_type ENUM('cash', 'reinvest') NOT NULL DEFAULT 'cash' COMMENT '分红类型：cash现金分红，reinvest分红再投',
  holding_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 COMMENT '分红时持仓数量',
  dividend_per_unit DECIMAL(18, 6) NOT NULL COMMENT '每单位分红金额',
  gross_amount DECIMAL(18, 2) NOT NULL COMMENT '税前分红金额',
  tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '税费',
  net_amount DECIMAL(18, 2) NOT NULL COMMENT '税后到账金额',
  reinvest_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 COMMENT '分红再投数量',
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种编码',
  paid_at DATETIME(3) NOT NULL COMMENT '到账或再投时间',
  status ENUM('normal', 'voided') NOT NULL DEFAULT 'normal' COMMENT '状态',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_investment_dividend_records_user_time (user_id, paid_at),
  KEY idx_investment_dividend_records_account_time (account_id, paid_at),
  KEY idx_investment_dividend_records_product_time (product_id, paid_at),
  KEY idx_investment_dividend_records_plan (plan_id),
  CONSTRAINT fk_investment_dividend_records_user
    FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_investment_dividend_records_account
    FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_investment_dividend_records_position
    FOREIGN KEY (position_id) REFERENCES investment_positions (id),
  CONSTRAINT fk_investment_dividend_records_product
    FOREIGN KEY (product_id) REFERENCES investment_products (id),
  CONSTRAINT fk_investment_dividend_records_plan
    FOREIGN KEY (plan_id) REFERENCES investment_dividend_plans (id),
  CONSTRAINT chk_investment_dividend_records_amount_nonnegative
    CHECK (gross_amount >= 0 AND tax_amount >= 0 AND net_amount >= 0),
  CONSTRAINT chk_investment_dividend_records_quantity_nonnegative
    CHECK (holding_quantity >= 0 AND reinvest_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资分红记录表：记录现金分红和分红再投，支撑累计收息统计';
