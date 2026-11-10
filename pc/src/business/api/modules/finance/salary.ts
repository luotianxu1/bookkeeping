// 工资模块接口（由 finance.ts 拆出；类型定义见 ./types）
import {
  financeRequest,
  requestDelete,
  requestGet,
  requestPost,
  requestPut,
} from '@/business/api/request'

export type SalaryAccountType = 'social_security' | 'housing_fund' | 'medical'
export type SalaryLinkedAccountType = SalaryAccountType | 'cash'
export type SalaryRecordType = 'auto' | 'manual'

export interface SalaryMetricItem {
  label: string
  value: number
}

export interface SalaryDetailItem {
  label: string
  value: number
  detail?: string
}

export interface SalaryLinkedAccount {
  accountType: SalaryLinkedAccountType
  title: string
  currentBalance: number
  monthlyDeposit: number
  routePath: string
}

export interface SalaryTaxSummary {
  currentMonthTax: number
  annualTax: number
  annualIncome: number
  routePath: string
}

export interface SalaryOverview {
  monthKey: string
  payDay: number
  paidMonths: number
  grossIncome: number
  netIncome: number
  totalDeduction: number
  taxAmount: number
  annualIncome: number
  netRate: number
  metrics: SalaryMetricItem[]
  details: SalaryDetailItem[]
  linkedAccounts: SalaryLinkedAccount[]
  taxSummary: SalaryTaxSummary
}

export interface SalarySettings {
  id: number
  userId: number
  monthlyGrossSalary: number
  transportSubsidy: number
  mealSubsidy: number
  annualBonus: number
  payDay: number
  socialSecurityBase: number
  housingFundBase: number
  housingFundPersonalRate: number
  housingFundCompanyRate: number
  pensionPersonalRate: number
  pensionCompanyRate: number
  medicalPersonalRate: number
  medicalCompanyRate: number
  medicalFixedAmount: number
  unemploymentPersonalRate: number
  unemploymentCompanyRate: number
  taxFreeThreshold: number
  taxYear: number
  childEducation: number
  continuingEducation: number
  housingLoan: number
  housingRent: number
  elderlyCare: number
  seriousMedical: number
  otherDeduction: number
  remark?: string | null
  monthlyTakeHome: number
  monthlyTax: number
  monthlySpecialDeductionTotal: number
  updatedAt: string
}

export interface SalaryMonthMetricItem {
  label: string
  value: number
}

export interface SalaryMonthRecordItem {
  id: number
  monthKey: string
  monthLabel: string
  grossSalary: number
  note?: string | null
  editable: boolean
}

export interface SalaryMonthPage {
  year: number
  defaultMonthlyGrossSalary: number
  recordedMonths: number
  recordedGrossIncome: number
  estimatedAnnualGrossIncome: number
  metrics: SalaryMonthMetricItem[]
  records: SalaryMonthRecordItem[]
  updatedAt?: string | null
}

export interface SaveSalaryMonthRecordParams {
  userId: number
  salaryMonth: string
  grossSalary: number
  note?: string | null
}

export interface SaveSalarySettingsParams {
  userId: number
  monthlyGrossSalary: number
  transportSubsidy: number
  mealSubsidy: number
  annualBonus: number
  payDay: number
  socialSecurityBase: number
  housingFundBase: number
  housingFundPersonalRate: number
  housingFundCompanyRate: number
  pensionPersonalRate: number
  pensionCompanyRate: number
  medicalPersonalRate: number
  medicalCompanyRate: number
  medicalFixedAmount: number
  unemploymentPersonalRate: number
  unemploymentCompanyRate: number
  taxFreeThreshold: number
  taxYear: number
  childEducation: number
  continuingEducation: number
  housingLoan: number
  housingRent: number
  elderlyCare: number
  seriousMedical: number
  otherDeduction: number
  remark?: string | null
}

export interface SalaryAccountMetricItem {
  label: string
  value: number
}

export interface SalaryAccountDetailItem {
  label: string
  description: string
  value: number
}

export interface SalaryAccountRecordItem {
  id: number
  monthKey: string
  monthLabel: string
  recordType: SalaryRecordType
  pillText: string
  amountLabel: string
  amountValue: number
  balanceLabel: string
  balanceValue: number
  note?: string | null
  editable: boolean
}

export interface SalaryAccountForecast {
  sourceYear: number
  forecastYear: number
  sourceAnnualGrossIncome: number
  predictedMonthlyBase: number
  predictedPersonal: number
  predictedCompany: number
  predictedInterest?: number | null
  interestAnnualRate?: number | null
  interestSettlementDate?: string | null
}

export interface SalaryAccountPage {
  accountType: SalaryAccountType
  title: string
  subtitle: string
  badgeText: string
  year: number
  currentBalance: number
  monthlyPersonal: number
  monthlyCompany: number
  yearlyIncrease: number
  metrics: SalaryAccountMetricItem[]
  details: SalaryAccountDetailItem[]
  records: SalaryAccountRecordItem[]
  forecast: SalaryAccountForecast
  updatedAt?: string | null
}

export interface SaveSalaryAccountBalanceParams {
  userId: number
  amount: number
  year?: number
  note?: string | null
}

export interface SaveSalaryAccountRecordParams {
  userId: number
  amount: number
  recordMonth: string
  recordType?: SalaryRecordType | 'adjustment'
  impactMode?: string | null
  note?: string | null
}

export interface SalaryTaxMetricItem {
  label: string
  value: number
}

export interface SalaryTaxDeductionItem {
  label: string
  monthlyValue: number
  annualValue: number
}

export interface SalaryTaxMonthItem {
  monthKey: string
  monthLabel: string
  grossIncome: number
  taxAmount: number
  takeHomeIncome: number
  statusText: string
}

export interface SalaryTaxPage {
  year: number
  paidMonths: number
  annualIncome: number
  annualTax: number
  currentMonthTax: number
  annualNetIncome: number
  monthlyAverageNetIncome: number
  specialDeductionTotal: number
  metrics: SalaryTaxMetricItem[]
  deductions: SalaryTaxDeductionItem[]
  monthItems: SalaryTaxMonthItem[]
}

export interface SalaryCashMetricItem {
  label: string
  value: number
}

export interface SalaryCashMonthItem {
  monthKey: string
  monthLabel: string
  grossIncome: number
  personalDeduction: number
  taxAmount: number
  cashIncome: number
  statusText: string
}

export interface SalaryCashPage {
  year: number
  paidMonths: number
  annualCashIncome: number
  currentMonthCashIncome: number
  annualGrossIncome: number
  annualPersonalDeduction: number
  annualTax: number
  metrics: SalaryCashMetricItem[]
  monthItems: SalaryCashMonthItem[]
}

export function getSalaryOverview(userId: number, month?: string) {
  return requestGet<SalaryOverview>(
    financeRequest,
    '/api/finance/salary/overview',
    {
      params: { userId, month },
    },
  )
}

export function getSalarySettings(userId: number, taxYear?: number) {
  return requestGet<SalarySettings>(
    financeRequest,
    '/api/finance/salary/settings',
    {
      params: { userId, taxYear },
    },
  )
}

export function saveSalarySettings(params: SaveSalarySettingsParams) {
  return requestPut<SalarySettings, SaveSalarySettingsParams>(
    financeRequest,
    '/api/finance/salary/settings',
    params,
  )
}

export function getSalaryMonthPage(userId: number, year?: number) {
  return requestGet<SalaryMonthPage>(
    financeRequest,
    '/api/finance/salary/records',
    {
      params: { userId, year },
    },
  )
}

export function createSalaryMonthRecord(params: SaveSalaryMonthRecordParams) {
  return requestPost<SalaryMonthPage, SaveSalaryMonthRecordParams>(
    financeRequest,
    '/api/finance/salary/records',
    params,
  )
}

export function updateSalaryMonthRecord(
  recordId: number,
  params: SaveSalaryMonthRecordParams,
) {
  return requestPut<SalaryMonthPage, SaveSalaryMonthRecordParams>(
    financeRequest,
    `/api/finance/salary/records/${recordId}`,
    params,
  )
}

export function deleteSalaryMonthRecord(recordId: number, userId: number) {
  return requestDelete<SalaryMonthPage>(
    financeRequest,
    `/api/finance/salary/records/${recordId}`,
    {
      params: { userId },
    },
  )
}

export function getSalaryAccountPage(
  userId: number,
  accountType: string,
  year?: number,
) {
  return requestGet<SalaryAccountPage>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}`,
    {
      params: { userId, year },
    },
  )
}

export function saveSalaryAccountBalance(
  accountType: string,
  params: SaveSalaryAccountBalanceParams,
) {
  return requestPut<SalaryAccountPage, SaveSalaryAccountBalanceParams>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}/balance`,
    params,
  )
}

export function createSalaryAccountRecord(
  accountType: string,
  params: SaveSalaryAccountRecordParams,
) {
  return requestPost<SalaryAccountPage, SaveSalaryAccountRecordParams>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}/records`,
    params,
  )
}

export function updateSalaryAccountRecord(
  accountType: string,
  recordId: number,
  params: SaveSalaryAccountRecordParams,
) {
  return requestPut<SalaryAccountPage, SaveSalaryAccountRecordParams>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}/records/${recordId}`,
    params,
  )
}

export function deleteSalaryAccountRecord(
  accountType: string,
  recordId: number,
  userId: number,
) {
  return requestDelete<SalaryAccountPage>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}/records/${recordId}`,
    { params: { userId } },
  )
}

export function getSalaryTaxPage(userId: number, year?: number) {
  return requestGet<SalaryTaxPage>(financeRequest, '/api/finance/salary/tax', {
    params: { userId, year },
  })
}

export function getSalaryCashPage(userId: number, year?: number) {
  return requestGet<SalaryCashPage>(
    financeRequest,
    '/api/finance/salary/cash',
    {
      params: { userId, year },
    },
  )
}
