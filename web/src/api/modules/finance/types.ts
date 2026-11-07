// 财务域全部类型定义（由 finance.ts 拆出；函数实现在同目录各域文件，finance.ts 作统一门面 re-export）
export interface AccountType {
  id: number
  code: string
  name: string
  category: string
  balanceDirection: string
  includeInNetWorthDefault: boolean
  allowOverdraft: boolean
  system: boolean
  sortOrder: number
  status: string
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface AccountTypeQuery {
  category?: string
  status?: string
}

export interface AccountQuery {
  userId?: number
  accountTypeId?: number
  status?: string
}

export interface FinanceOverviewSummary {
  totalAssets: number | null
}

export interface FreeEstimate {
  userId: number
  asOfDate: string
  currentNetAssets: number
  historicalMonthlyIncome: number
  historicalMonthlyExpense: number
  monthlyExpense: number
  monthlyIncome: number
  monthlySurplus: number
  dataStartDate?: string | null
  dataEndDate?: string | null
  dataMonths: number
  incomeTransactionCount: number
  expenseTransactionCount: number
}

export interface DebtAccountSummary {
  netAmount: number
  payableTotal: number
  receivableTotal: number
  accountCount: number
  recordCount: number
}

export interface LiabilityAccountSummary {
  totalAmount: number
  accountCount: number
  recordCount: number
}

export type DebtDirection = 'payable' | 'receivable'
export type DebtRecordType = 'borrow' | 'repayment'
export type HumanRelationDirection = 'outgoing' | 'incoming'
export type LiabilityRepaymentStatus = 'pending' | 'paid'

export interface HumanRelationAccountSummary {
  netAmount: number
  outgoingTotal: number
  incomingTotal: number
  accountCount: number
  recordCount: number
}

export interface DebtRecord {
  id: number
  userId: number
  accountId: number
  parentRecordId?: number | null
  contactId?: number | null
  accountName?: string | null
  fundingAccountId?: number | null
  fundingAccountName?: string | null
  direction: DebtDirection
  recordType: DebtRecordType
  amount: number
  remainingAmount?: number | null
  currencyCode: string
  remark?: string | null
  occurredAt: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface LiabilityRecord {
  id: number
  userId: number
  accountId: number
  accountName?: string | null
  amount: number
  installmentTotalPeriods?: number | null
  installmentCurrentPeriod?: number | null
  repaymentStatus: LiabilityRepaymentStatus
  repaymentType?: 'monthly' | 'prepayment'
  paidAt?: string | null
  currencyCode: string
  remark?: string | null
  occurredAt: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface HumanRelationRecord {
  id: number
  userId: number
  accountId: number
  contactId?: number | null
  accountName?: string | null
  fundingAccountId?: number | null
  fundingAccountName?: string | null
  direction: HumanRelationDirection
  amount: number
  currencyCode: string
  remark?: string | null
  occurredAt: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface Account {
  id: number
  userId: number
  accountTypeId: number
  contactId?: number | null
  accountTypeCode?: string | null
  accountTypeName?: string | null
  name: string
  icon?: string | null
  color?: string | null
  currencyCode: string
  currentBalance: number
  loanTotalAmount?: number | null
  loanInterestRate?: number | null
  loanInterestAmount?: number | null
  loanTotalPeriods?: number | null
  loanRepaymentDay?: number | null
  loanStartDate?: string | null
  loanSettledAt?: string | null
  includeInNetWorth: boolean
  sortOrder: number
  status: string
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface Category {
  id: number
  userId?: number | null
  name: string
  type: 'expense' | 'income'
  icon: string
  color?: string | null
  parentId?: number | null
  level?: number | null
  system: boolean
  sortOrder: number
  status: string
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface CategoryQuery {
  userId?: number
  type?: 'expense' | 'income'
  status?: string
}

export interface ExchangeRate {
  fromCurrency: string
  toCurrency: string
  rate: number
  updatedAt: string
  source: string
}

export type StockScreenMarket = 'ALL' | 'SH' | 'SZ' | 'BJ'

export interface StockScreenRun {
  id: number
  tradeDate?: string | null
  triggerName: string
  status: 'running' | 'success' | 'failed' | 'canceled' | string
  totalStocks: number
  processedStocks: number
  matchedStocks: number
  failedStocks: number
  dataSource: string
  ruleVersion?: string | null
  resultMessage?: string | null
  errorMessage?: string | null
  startedAt: string
  finishedAt?: string | null
}

export type ScheduledTaskStatus = 'pending' | 'running' | 'success' | 'failed' | 'missed' | 'skipped'

export interface ScheduledTask {
  taskName: string
  taskLabel: string
  triggerName: string
  scheduleLabel: string
  status: ScheduledTaskStatus
  statusLabel: string
  executable: boolean
  taskDate: string
  startedAt?: string | null
  finishedAt?: string | null
  resultMessage?: string | null
  errorMessage?: string | null
}

export interface StockScreenItem {
  stockCode: string
  stockName: string
  market: Exclude<StockScreenMarket, 'ALL'>
  bearishCount6: number
  lastThreeBearish: boolean
  lastThreeVolumeUp: boolean
  threeDayDeclinePct: number
  lastDayDeclinePct: number
  bullishEngulfing: boolean
  noLowerShadow: boolean
  volumeShrinking: boolean
  volumeRatio: number
  lowerShadowPct: number
  signalScore: number
  bearishStartDate: string
  previousDate: string
  signalDate: string
  previousOpen: number
  previousClose: number
  signalOpen: number
  signalClose: number
  previousVolume: number
  signalVolume: number
  yinYangDoubleBearMatched: boolean
  yinYangPenetrationPct: number
  yinYangType?: string | null
  yinYangScore: number
  firstBoardHighBearMatched: boolean
  firstBoardBuyPoint?: string | null
  firstBoardScore: number
  firstBoardDate?: string | null
  firstBoardLow?: number | null
  highBearDate?: string | null
  highBearHigh?: number | null
  highBearVolumeRatio: number
}

export interface StockScreenPage {
  run?: StockScreenRun | null
  total: number
  page: number
  pageSize: number
  items: StockScreenItem[]
}

export interface StockScreenQuery {
  market?: StockScreenMarket
  keyword?: string
  ruleKey?: string
  minBearishCount?: number
  minThreeDayDecline?: number
  minLastDayDecline?: number
  requireVolumeUp?: boolean
  requireNoLowerShadow?: boolean
  includeChiNext?: boolean
  includeStar?: boolean
  page?: number
  pageSize?: number
}

export interface StockScreenRunSubmission {
  status: 'accepted' | 'running' | 'reused' | string
  message: string
  runId?: number | null
  tradeDate?: string | null
  submittedAt: string
}

export interface StockScreenStopSubmission {
  status: 'accepted' | 'idle' | string
  message: string
  runId?: number | null
  submittedAt: string
}

export type TransactionType = 'expense' | 'income' | 'transfer'

export interface Transaction {
  originalTransactionId?: number | null
  recoveryKind?: 'refund' | 'reimbursement' | null
  reimbursementAmount?: number
  id: number
  sourceId?: number | null
  sourceType?: 'transaction' | 'debt_record' | 'human_relation_record' | null
  transactionNo: string
  userId: number
  type: TransactionType
  amount: number
  currencyCode: string
  accountId: number
  accountName?: string | null
  categoryId?: number | null
  categoryName?: string | null
  categoryIcon?: string | null
  categoryColor?: string | null
  fromAccountId?: number | null
  fromAccountName?: string | null
  toAccountId?: number | null
  toAccountName?: string | null
  title: string
  remark?: string | null
  occurredAt: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface TransactionQuery {
  userId?: number
  type?: TransactionType
  accountId?: number
}

export interface TransactionPageQuery extends TransactionQuery {
  userIds?: string
  cashOnly?: boolean
  startDate?: string
  endDate?: string
  sortOrder?: 'asc' | 'desc'
  page?: number
  pageSize?: number
}

export interface TransactionPage {
  items: Transaction[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  incomeTotal?: number
  expenseTotal?: number
  balanceTotal?: number
}

export type TransactionAnalysisPeriod = 'month' | 'year'

export interface TransactionAnalysisSummary {
  income: number
  expense: number
  surplus: number
  incomeCount: number
  expenseCount: number
  transactionCount: number
}

export interface TransactionAnalysisCategoryBreakdownItem {
  categoryId?: number | null
  categoryName: string
  categoryIcon?: string | null
  categoryColor?: string | null
  amount: number
  percent: number
  transactionCount: number
}

export interface TransactionAnalysisTrendPoint {
  key: string
  label: string
  income: number
  expense: number
  surplus: number
}

export interface TransactionAnalysisPeriodSummary {
  key: string
  label: string
  income: number
  expense: number
  surplus: number
  transactionCount: number
  transactions: Transaction[]
}

export interface TransactionAnalysis {
  userId: number
  period: TransactionAnalysisPeriod
  month?: string | null
  year?: number | null
  summary: TransactionAnalysisSummary
  incomeBreakdown: TransactionAnalysisCategoryBreakdownItem[]
  expenseBreakdown: TransactionAnalysisCategoryBreakdownItem[]
  trendPoints: TransactionAnalysisTrendPoint[]
  periodSummaries: TransactionAnalysisPeriodSummary[]
}

export interface TransactionAnalysisQuery {
  userId: number
  period?: TransactionAnalysisPeriod
  month?: string
  year?: number
}

export interface MonthlyBudget {
  id: number
  userId: number
  budgetMonth: string
  amount: number
  currencyCode: string
  usedAmount: number
  remainingAmount: number
  usagePercent: number
  status: string
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface MonthlyBudgetQuery {
  userId: number
  limit?: number
}

export type RenewalSubscriptionStatus = 'active' | 'paused' | 'cancelled'
export type RenewalChargeStatus = 'idle' | 'success' | 'failed'
export type RenewalBillingCycle = 'monthly' | 'quarterly' | 'yearly'

export interface RenewalSubscription {
  id: number
  userId: number
  name: string
  providerName?: string | null
  amount: number
  currencyCode: string
  fundingAccountId: number
  fundingAccountName?: string | null
  categoryId?: number | null
  categoryName?: string | null
  categoryIcon?: string | null
  categoryColor?: string | null
  billingDay: number
  billingCycle: RenewalBillingCycle
  nextBillingDate: string
  lastChargedAt?: string | null
  lastTransactionId?: number | null
  lastChargeStatus: RenewalChargeStatus
  lastChargeMessage?: string | null
  status: RenewalSubscriptionStatus
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface RenewalSubscriptionQuery {
  userId: number
  status?: RenewalSubscriptionStatus
}

export interface RenewalSubscriptionSummary {
  activeCount: number
  pausedCount: number
  dueThisMonthCount: number
  monthlyAmount: number
  dueThisMonthAmount: number
}

export interface SaveRenewalSubscriptionParams {
  userId: number
  name: string
  providerName?: string | null
  amount: number
  currencyCode?: string
  fundingAccountId: number
  categoryId: number
  billingDay: number
  billingCycle: RenewalBillingCycle
  nextBillingDate?: string
  status?: Extract<RenewalSubscriptionStatus, 'active' | 'paused'>
  remark?: string | null
}

export interface CreateAccountParams {
  userId: number
  accountTypeId: number
  contactId?: number | null
  name: string
  icon?: string | null
  color?: string | null
  currencyCode?: string
  currentBalance?: number
  loanTotalAmount?: number | null
  loanInterestRate?: number | null
  loanInterestAmount?: number | null
  loanTotalPeriods?: number | null
  loanRepaymentDay?: number | null
  loanStartDate?: string | null
  includeInNetWorth: boolean
  sortOrder?: number
  status?: string
  remark?: string | null
}

export interface DebtRecordQuery {
  userId: number
  accountId?: number
}

export interface SaveDebtRecordParams {
  userId: number
  accountId: number
  parentRecordId?: number | null
  fundingAccountId?: number | null
  direction: DebtDirection
  recordType?: DebtRecordType
  amount: number
  currencyCode?: string
  remark?: string | null
  occurredAt?: string
}

export interface LiabilityRecordQuery {
  userId: number
  accountId?: number
}

export interface SaveLiabilityRecordParams {
  userId: number
  accountId: number
  amount?: number
  installmentTotalPeriods?: number | null
  installmentCurrentPeriod?: number | null
  currencyCode?: string
  remark?: string | null
  occurredAt?: string
}

export interface RepayLiabilityRecordParams {
  userId: number
  paidAt?: string
}

export interface PrepayLiabilityAccountParams {
  userId: number
  paidAt?: string
  remark?: string | null
}

export interface HumanRelationRecordQuery {
  userId: number
  accountId?: number
}

export interface SaveHumanRelationRecordParams {
  userId: number
  accountId: number
  fundingAccountId?: number | null
  direction: HumanRelationDirection
  amount: number
  currencyCode?: string
  remark?: string | null
  occurredAt?: string
}

export interface SaveAccountSortOrdersParams {
  userId: number
  items: {
    id: number
    sortOrder: number
  }[]
}

export interface SaveAccountTypeSortOrdersParams {
  items: {
    id: number
    sortOrder: number
  }[]
}

export interface SaveCategoryParams {
  userId?: number | null
  name: string
  type: 'expense' | 'income'
  icon: string
  color?: string | null
  parentId?: number | null
  system?: boolean
  sortOrder?: number
  status?: string
  remark?: string | null
}

export interface CreateTransactionParams {
  requestId?: string
  userId: number
  type: TransactionType
  amount: number
  currencyCode?: string
  // 收支流水必填；转账流水不传
  accountId?: number
  // 收支流水必填；转账流水不传
  categoryId?: number
  // 转账流水必填
  fromAccountId?: number
  // 转账流水必填
  toAccountId?: number
  title?: string
  remark?: string | null
  occurredAt: string
}

export type UpdateTransactionParams = CreateTransactionParams

export interface SaveMonthlyBudgetParams {
  userId: number
  budgetMonth: string
  amount: number
  currencyCode?: string
  remark?: string | null
}

export type InvestmentProductType = 'stock' | 'fund' | 'bond' | 'gold' | 'other'

export interface InvestmentProduct {
  id: number
  productType: InvestmentProductType
  market?: string | null
  exchangeCode?: string | null
  symbol: string
  name: string
  shortName?: string | null
  currencyCode: string
  unitName: string
  pricePrecision: number
  latestPrice?: number | null
  stableDividend?: boolean | null
  predictedAnnualDividendPerUnit?: number | null
  dividendStableYears?: number | null
  dividendLastPaidDate?: string | null
  dividendDataSource?: string | null
  dividendEvaluatedAt?: string | null
  status: string
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface SaveInvestmentProductParams {
  productType: InvestmentProductType
  market?: string | null
  exchangeCode?: string | null
  symbol: string
  name: string
  shortName?: string | null
  currencyCode?: string
  unitName?: string
  pricePrecision?: number
  status?: string
  remark?: string | null
}

export interface InvestmentSummary {
  userId: number
  totalMarketValue: number
  dayProfit?: number | null
  dayProfitRate?: number | null
  holdingProfit: number
  holdingProfitRate: number
  cumulativeProfit: number
  cumulativeProfitRate: number
  lastSyncedAt?: string | null
}

export type InvestmentTrendRange = '7d' | '30d' | 'ytd' | 'all'

export type AssetTrendRange = '7d' | '30d' | '60d' | 'ytd' | 'all'

export interface InvestmentTrendPoint {
  key: string
  label: string
  value: number
}

export interface InvestmentTrendAllocation {
  productType?: InvestmentProductType | string | null
  label: string
  marketValue: number
  percent: number
}

export interface InvestmentTrendContributor {
  positionId: number
  productId: number
  productType?: InvestmentProductType | string | null
  productName: string
  productSymbol?: string | null
  contributionAmount: number
  contributionRate: number
}

export interface InvestmentTrend {
  userId: number
  accountId?: number | null
  range: InvestmentTrendRange | string
  rangeLabel: string
  startDate: string
  endDate: string
  totalMarketValue: number
  cumulativeProfit: number
  cumulativeProfitRate: number
  periodChangeAmount: number
  periodChangeRate: number
  lastSyncedAt?: string | null
  trendPoints: InvestmentTrendPoint[]
  allocations: InvestmentTrendAllocation[]
  contributors: InvestmentTrendContributor[]
}

export interface AssetTrendPoint {
  key: string
  label: string
  value: number
}

export interface AssetTrendAllocation {
  accountTypeCode?: string | null
  label: string
  balance: number
  percent: number
}

export interface AssetTrendContributor {
  accountId: number
  accountName: string
  accountTypeCode?: string | null
  accountTypeLabel: string
  contributionAmount: number
  contributionRate: number
}

export interface AssetAccountSnapshotItem {
  userId: number
  accountId: number
  accountName: string
  accountTypeCode?: string | null
  accountTypeLabel: string
  totalAssets: number
  rawAssets?: number | null
  currentAssets: number
  changeAmount: number
}

export interface AssetAccountSnapshot {
  userId: number
  snapshotDate: string
  totalAssets: number
  rawTotalAssets?: number | null
  currentTotalAssets: number
  changeAmount: number
  accounts: AssetAccountSnapshotItem[]
}

export interface AssetTrend {
  userId: number
  accountId?: number | null
  range: AssetTrendRange | string
  rangeLabel: string
  startDate: string
  endDate: string
  totalAssets: number
  cumulativeProfit: number
  cumulativeProfitRate?: number | null
  periodChangeAmount: number
  periodChangeRate: number
  previousPeriodValue?: number | null
  lastSyncedAt?: string | null
  trendPoints: AssetTrendPoint[]
  allocations: AssetTrendAllocation[]
  contributors: AssetTrendContributor[]
}

export interface FundProfitForecastAccount {
  accountId: number
  accountName: string
  holdingAmount: number
  estimateProfit: number
  estimateProfitRate: number
  totalProfit: number
  totalProfitRate: number
  fundCount: number
  estimatedAt?: string | null
}

export interface FundProfitForecastHolding {
  accountId: number
  accountName?: string | null
  positionId: number
  productId: number
  productName: string
  productSymbol: string
  unitName?: string | null
  holdingQuantity: number
  costAmount: number
  holdingAmount: number
  estimateProfit: number
  estimateProfitRate: number
  totalProfit: number
  totalProfitRate: number
  estimatedNetValue: number
  officialNetValue: number
  estimatedAt?: string | null
}

export interface FundProfitForecast {
  userId: number
  holdingAmount: number
  estimateProfit: number
  estimateProfitRate: number
  totalProfit: number
  totalProfitRate: number
  fundCount: number
  estimatedAt?: string | null
  accounts: FundProfitForecastAccount[]
  holdings: FundProfitForecastHolding[]
}

export type FundProfitView = 'day' | 'month' | 'year'

export interface FundProfitPageAccount {
  accountId: number
  accountName: string
  holdingAmount: number
  totalProfit: number
  totalProfitRate: number
  fundCount: number
}

export interface FundProfitPageSummaryMetric {
  key: 'today' | '7d' | 'month' | string
  label: string
  profit?: number | null
  profitRate?: number | null
}

export interface FundProfitPageSummary {
  holdingAmount: number
  investedAmount: number
  totalProfit: number
  totalProfitRate: number
  fundCount: number
  lastSyncedAt?: string | null
  activeShortcut: string
  shortcuts: FundProfitPageSummaryMetric[]
}

export interface FundProfitTrendPoint {
  key: string
  label: string
  date: string
  profit?: number | null
}

export interface FundProfitCalendarCell {
  key: string
  label: string
  secondaryLabel?: string | null
  startDate: string
  endDate: string
  profit?: number | null
  profitRate?: number | null
  selected: boolean
  current: boolean
}

export interface FundProfitSelection {
  key: string
  label: string
  title: string
  startDate: string
  endDate: string
  comparisonDate: string
  profit?: number | null
  profitRate?: number | null
  positiveFundCount: number
  negativeFundCount: number
}

export interface FundProfitContribution {
  positionId: number
  productId: number
  productName: string
  productSymbol?: string | null
  accountName?: string | null
  contributionAmount: number
  contributionRate: number
  holdingAmount: number
  holdingQuantity: number
}

export interface FundProfitDetail {
  positionId: number
  productId: number
  productName: string
  productSymbol?: string | null
  accountName?: string | null
  holdingQuantity: number
  netValue: number
  holdingAmount: number
  costAmount: number
  periodProfit: number
  periodProfitRate: number
}

export interface FundProfitPage {
  userId: number
  accountId?: number | null
  view: FundProfitView | string
  anchor: string
  selectedKey: string
  lastSyncedAt?: string | null
  accounts: FundProfitPageAccount[]
  summary: FundProfitPageSummary
  insight: string
  trendPoints: FundProfitTrendPoint[]
  calendarItems: FundProfitCalendarCell[]
  selection: FundProfitSelection
  contributors: FundProfitContribution[]
  details: FundProfitDetail[]
}

export interface InvestmentPosition {
  id: number
  userId: number
  accountId: number
  accountName?: string | null
  productId: number
  productType?: InvestmentProductType | null
  productName?: string | null
  productSymbol?: string | null
  market?: string | null
  unitName?: string | null
  currencyCode?: string | null
  holdingQuantity: number
  availableQuantity: number
  frozenQuantity: number
  costAmount: number
  avgCostPrice: number
  currentPrice: number
  marketValue: number
  dayProfit?: number | null
  dayProfitRate?: number | null
  holdingProfit: number
  holdingProfitRate: number
  cumulativeProfit: number
  cumulativeProfitRate: number
  includeInNetWorth: boolean
  status: string
  lastSyncedAt?: string | null
  hasRecentDividendPlan?: boolean | null
  recentDividendStatus?: string | null
  recentDividendDate?: string | null
  recentDividendPerUnit?: number | null
  subscriptionStatus?: 'confirmed' | 'pending' | string | null
  subscriptionAppliedDate?: string | null
  subscriptionExpectedConfirmDate?: string | null
  subscriptionConfirmedAt?: string | null
  dividendType?: 'cash' | 'reinvest' | string | null
  dividendFundingAccountId?: number | null
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface InvestmentDetailStat {
  label: string
  value: string
  tone?: 'positive' | 'negative' | 'neutral' | string | null
}

export interface InvestmentChartPoint {
  label: string
  value?: number | null
  open?: number | null
  close?: number | null
  high?: number | null
  low?: number | null
  volume?: number | null
}

export interface InvestmentAssetDetail {
  position: InvestmentPosition
  productType?: InvestmentProductType | string | null
  name?: string | null
  symbol?: string | null
  market?: string | null
  unitName?: string | null
  latestPrice?: number | null
  change?: number | null
  changePercent?: number | null
  updatedAt?: string | null
  marketStats: InvestmentDetailStat[]
  holdingStats: InvestmentDetailStat[]
  fundRedeemFeeOptions?: InvestmentFundRedeemFeeOption[] | null
  dividendRecords?: InvestmentDividendRecord[] | null
  chartPoints: InvestmentChartPoint[]
  chartType?: 'line' | 'candlestick' | string | null
  source?: string | null
  description?: string | null
}

export interface InvestmentQuoteSyncSummary {
  trigger: string
  userId?: number | null
  accountId?: number | null
  startedAt: string
  finishedAt: string
  durationMs: number
  syncedPositions: number
  syncedFundPositions?: number
  syncedStockPositions?: number
  syncedDividendPlans: number
  settledCount: number
}

export interface InvestmentFundRedeemFeeOption {
  label: string
  feeRate: number
}

export interface InvestmentDividendRecord {
  id: number
  productId: number
  productName?: string | null
  productSymbol?: string | null
  dividendYear?: number | null
  payDate?: string | null
  dividendPerUnit?: number | null
  expectedAmount?: number | null
  actualAmount?: number | null
  status?: string | null
  paidAt?: string | null
}

export interface InvestmentDividendIncomeItem {
  positionId?: number | null
  productId: number
  productName: string
  productSymbol?: string | null
  productType?: InvestmentProductType | string | null
  unitName?: string | null
  holdingQuantity: number
  marketValue: number
  costAmount: number
  estimatedDividendAmount: number
  estimatedDividendRate: number
  actualDividendAmount: number
  actualDividendRate: number
}

export interface InvestmentDividendIncomeSummary {
  estimatedDividendAmount: number
  estimatedDividendRate: number
  actualDividendAmount: number
  actualDividendRate: number
  holdingCount: number
}

export interface InvestmentFixedExpense {
  id: number
  userId: number
  name: string
  amount: number
  currencyCode?: string | null
  sortOrder?: number | null
  status?: string | null
  remark?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface SaveInvestmentFixedExpenseParams {
  userId: number
  name: string
  amount: number
  currencyCode?: string
  sortOrder?: number
  remark?: string | null
}

export interface InvestmentDividendIncomePage {
  userId: number
  summary: InvestmentDividendIncomeSummary
  items: InvestmentDividendIncomeItem[]
  fixedExpenses?: InvestmentFixedExpense[]
  updatedAt?: string | null
}

export interface InvestmentDividendForecastRequest {
  productType: InvestmentProductType
  symbol: string
  name?: string
  market?: string | null
  exchangeCode?: string | null
  currencyCode?: string
  unitName?: string
  latestPrice?: number | null
  quoteDate?: string | null
  holdingQuantity?: number
  holdingAmount?: number
}

export interface InvestmentDividendForecast {
  productType: InvestmentProductType | string
  productTypeLabel: string
  symbol: string
  name: string
  market?: string | null
  unitName?: string | null
  currentPrice: number
  basisYear: number
  lastYearDividendCount: number
  lastYearDividendPerUnit: number
  estimatedHoldingQuantity: number
  estimatedHoldingAmount: number
  estimatedDividendAmount: number
  estimatedDividendRate: number
  calculationNote: string
  source?: string | null
}

export interface InvestmentProductQuote {
  productType: InvestmentProductType | string
  productTypeLabel: string
  symbol: string
  name: string
  market?: string | null
  unitName?: string | null
  latestPrice?: number | null
  change?: number | null
  changePercent?: number | null
  quoteDate?: string | null
  source?: string | null
}

export interface InvestmentTransaction {
  id: number
  transactionNo: string
  userId: number
  accountId: number
  accountName?: string | null
  positionId?: number | null
  productId: number
  productName?: string | null
  productSymbol?: string | null
  tradeType: string
  quantity: number
  price?: number | null
  amount: number
  feeAmount: number
  taxAmount: number
  currencyCode: string
  fundingAccountId?: number | null
  tradeAt: string
  status: string
  settlementStatus?: 'confirmed' | 'pending' | string | null
  settlementAppliedDate?: string | null
  settlementExpectedDate?: string | null
  settlementConfirmedAt?: string | null
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface InvestmentTransactionPage {
  items: InvestmentTransaction[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export type InvestmentAutoInvestFrequency = 'daily' | 'weekly' | 'monthly'

export interface InvestmentAutoInvestPlan {
  id: number
  userId: number
  accountId: number
  accountName?: string | null
  positionId: number
  productId: number
  productName?: string | null
  productSymbol?: string | null
  fundingAccountId: number
  fundingAccountName?: string | null
  frequency: InvestmentAutoInvestFrequency | string
  amount: number
  currencyCode: string
  nextExecuteDate: string
  lastExecutedAt?: string | null
  status: 'active' | 'paused' | 'cancelled' | string
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface SaveInvestmentAutoInvestPlanParams {
  userId: number
  accountId: number
  positionId: number
  fundingAccountId: number
  frequency: InvestmentAutoInvestFrequency
  amount: number
  currencyCode?: string
  nextExecuteDate: string
  status?: 'active' | 'paused' | 'cancelled'
  remark?: string | null
}

export interface SaveInvestmentTransactionParams {
  requestId?: string
  userId: number
  accountId: number
  positionId: number
  productId: number
  tradeType: 'buy' | 'sell'
  quantity?: number
  price?: number | null
  amount: number
  feeAmount?: number
  taxAmount?: number
  currencyCode?: string
  tradeAt: string
  fundingAccountId?: number
  subscriptionTimeSlot?: 'before_1500' | 'after_1500'
  dividendType?: 'cash' | 'reinvest'
  remark?: string | null
}

export interface InvestmentPositionQuery {
  userId: number
  accountId?: number
  productType?: InvestmentProductType
  status?: string
}

export interface SaveInvestmentPositionParams {
  requestId?: string
  userId: number
  accountId: number
  fundingAccountId?: number
  productId?: number
  product?: SaveInvestmentProductParams
  holdingQuantity?: number
  availableQuantity?: number
  frozenQuantity?: number
  costAmount: number
  currentPrice?: number
  tradeAt?: string
  subscriptionTimeSlot?: 'before_1500' | 'after_1500'
  dividendType?: 'cash' | 'reinvest'
  includeInNetWorth?: boolean
  status?: string
  remark?: string | null
}

export interface CategoryBudget {
  id: number
  budgetMonth: string
  categoryId: number
  categoryName: string
  amount: number
  usedAmount: number
  remainingAmount: number
  usagePercent: number
  currencyCode: string
}
export interface ExpenseReceipt {
  id: number
  kind: 'refund' | 'reimbursement'
  amount: number
  accountId: number
  accountName: string
  occurredAt: string
  remark?: string | null
}
export interface ExpenseRecovery {
  id: number
  title: string
  categoryName: string
  accountName: string
  currencyCode: string
  occurredAt: string
  amount: number
  reimbursementAmount: number
  refundedAmount: number
  reimbursedAmount: number
  recoverableAmount: number
  pendingAmount: number
  reimbursementStatus: 'none' | 'pending' | 'partial' | 'received'
  receipts?: ExpenseReceipt[]
}
export interface ExpenseRecoveryPage {
  items: ExpenseRecovery[]
  total: number
  page: number
  totalPages: number
}
