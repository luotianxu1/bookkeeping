USE bookkeeping_app;

ALTER TABLE investment_positions
  ADD COLUMN subscription_status ENUM('confirmed', 'pending') NOT NULL DEFAULT 'confirmed' COMMENT '基金申购状态：confirmed已确认，pending待确认' AFTER status,
  ADD COLUMN subscription_applied_date DATE NULL COMMENT '基金申购申请日' AFTER subscription_status,
  ADD COLUMN subscription_expected_confirm_date DATE NULL COMMENT '基金预计确认日期' AFTER subscription_applied_date,
  ADD COLUMN subscription_confirmed_at DATETIME(3) NULL COMMENT '基金申购确认时间' AFTER subscription_expected_confirm_date;
