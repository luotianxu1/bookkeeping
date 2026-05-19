USE bookkeeping_app;

ALTER TABLE investment_positions
  DROP INDEX uk_investment_positions_account_product_status,
  ADD INDEX idx_investment_positions_account_product_status (account_id, product_id, status);
