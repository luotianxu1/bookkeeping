USE bookkeeping_app;

ALTER TABLE investment_transactions
  ADD COLUMN funding_account_id BIGINT UNSIGNED NULL COMMENT '关联资金账户ID' AFTER currency_code,
  ADD COLUMN settlement_status ENUM('confirmed', 'pending') NOT NULL DEFAULT 'confirmed' COMMENT '基金交易结算状态：confirmed已确认，pending待确认' AFTER status,
  ADD COLUMN settlement_applied_date DATE NULL COMMENT '基金交易申请日' AFTER settlement_status,
  ADD COLUMN settlement_expected_date DATE NULL COMMENT '基金交易预计确认日' AFTER settlement_applied_date,
  ADD COLUMN settlement_confirmed_at DATETIME(3) NULL COMMENT '基金交易确认时间' AFTER settlement_expected_date,
  ADD KEY idx_investment_transactions_funding_status (funding_account_id, settlement_status),
  ADD KEY idx_investment_transactions_settlement_date (settlement_status, settlement_expected_date),
  ADD CONSTRAINT fk_investment_transactions_funding_account
    FOREIGN KEY (funding_account_id) REFERENCES accounts (id);
