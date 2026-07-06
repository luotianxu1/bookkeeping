-- MySQL 8.0+
-- Run this file with:
--   mysql -u root -p bookkeeping_app < database/037_create_salary_module.mysql.sql

USE bookkeeping_app;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS salary_profiles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工资配置ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  monthly_gross_salary DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '月度税前工资',
  transport_subsidy DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '交通补贴',
  meal_subsidy DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '餐补',
  annual_bonus DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '年内累计奖金',
  pay_day TINYINT UNSIGNED NOT NULL DEFAULT 15 COMMENT '发薪日',
  social_security_base DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '社保基数',
  housing_fund_base DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '公积金基数',
  housing_fund_personal_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '公积金个人比例',
  housing_fund_company_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '公积金单位比例',
  pension_personal_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '养老个人比例',
  pension_company_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '养老单位比例',
  medical_personal_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '医保个人比例',
  medical_company_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '医保单位比例',
  medical_fixed_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '医保固定金额',
  unemployment_personal_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '失业个人比例',
  unemployment_company_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '失业单位比例',
  tax_free_threshold DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '个税起征点',
  status ENUM('active', 'archived') NOT NULL DEFAULT 'active' COMMENT '配置状态',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_salary_profiles_user (user_id),
  KEY idx_salary_profiles_status (status),
  CONSTRAINT fk_salary_profiles_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资配置表：记录工资测算基础参数';

CREATE TABLE IF NOT EXISTS salary_special_deductions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '专项附加扣除ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  tax_year INT NOT NULL COMMENT '纳税年度',
  child_education DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '子女教育',
  continuing_education DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '继续教育',
  housing_loan DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '住房贷款利息',
  housing_rent DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '住房租金',
  elderly_care DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '赡养老人',
  serious_medical DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '大病医疗',
  other_deduction DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '其他扣除',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_salary_deductions_user_year (user_id, tax_year),
  KEY idx_salary_deductions_year (tax_year),
  CONSTRAINT fk_salary_deductions_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资专项附加扣除表';

CREATE TABLE IF NOT EXISTS salary_account_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工资账户流水ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  account_type ENUM('social_security', 'housing_fund', 'medical') NOT NULL COMMENT '账户类型',
  record_type ENUM('initial', 'auto', 'manual') NOT NULL DEFAULT 'manual' COMMENT '记录类型',
  record_month DATE NOT NULL COMMENT '记录月份，统一存每月1日',
  amount DECIMAL(18, 2) NOT NULL COMMENT '本次变动金额',
  personal_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '个人部分',
  company_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '单位部分',
  balance_after DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '计算后的余额',
  sync_to_current TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否同步影响当前余额',
  note VARCHAR(500) NULL COMMENT '备注说明',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_salary_account_records_user_type_month (user_id, account_type, record_month),
  KEY idx_salary_account_records_record_type (record_type),
  CONSTRAINT fk_salary_account_records_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资相关账户流水表：初始值、自动累计、手动调账';
