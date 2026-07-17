import { financeRequest, requestDelete, requestGet, requestPost, requestPut } from '@/api/request'

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
  contactId?: number | null
  accountName?: string | null
  fundingAccountId?: number | null
  fundingAccountName?: string | null
  direction: DebtDirection
  recordType: DebtRecordType
  amount: number
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

export type GoldPriceRange = '1d' | '7d' | '30d' | '3m' | '1y' | '3y'

export interface GoldRealtimePrice {
  name: string
  unit: string
  price: number
  change: number
  changePercent: number
  updatedAt: string
  source: string
}

export interface GoldMarketQuote {
  name: string
  unit: string
  price: number
  change: number
  changePercent: number
  updatedAt: string
}

export interface GoldMarketStats {
  openPrice: number
  highPrice: number
  lowPrice: number
  buyPrice: number
  sellPrice: number
  unit: string
}

export interface JewelryGoldPrice {
  brandName: string
  price: number
  unit: string
  updatedAt: string
}

export interface GoldChartPoint {
  label: string
  price: number
}

export interface GoldPrice {
  spotGold: GoldMarketQuote
  londonGold: GoldMarketQuote
  stats: GoldMarketStats
  jewelryPrices: JewelryGoldPrice[]
  chartPoints: GoldChartPoint[]
  updatedAt: string
  source: string
}

export interface ExchangeRate {
  fromCurrency: string
  toCurrency: string
  rate: number
  updatedAt: string
  source: string
}

export type MarketNewsCategory = 'all' | 'focus' | 'china' | 'stock' | 'commodity' | 'fund' | 'macro'

export interface MarketNewsItem {
  code: string
  title: string
  summary: string
  url: string
  publishedAt: string | null
  commentCount: number
  shareCount: number
  relatedStockCount: number
  highlight: boolean
}

export interface MarketNews {
  categoryKey: MarketNewsCategory
  categoryLabel: string
  count: number
  updatedAt: string
  source: string
  items: MarketNewsItem[]
}

export type TransactionType = 'expense' | 'income'

export interface Transaction {
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
  userId: number
  type: TransactionType
  amount: number
  currencyCode?: string
  accountId: number
  categoryId: number
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

export type AssetTrendRange = '7d' | '30d' | 'ytd' | 'all'

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
  currentAssets: number
  changeAmount: number
}

export interface AssetAccountSnapshot {
  userId: number
  snapshotDate: string
  totalAssets: number
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
  remark?: string | null
}

export interface InvestmentPositionQuery {
  userId: number
  accountId?: number
  productType?: InvestmentProductType
  status?: string
}

export interface SaveInvestmentPositionParams {
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
  includeInNetWorth?: boolean
  status?: string
  remark?: string | null
}

export function getAccountTypes(params: AccountTypeQuery = {}) {
  return requestGet<AccountType[]>(financeRequest, '/api/finance/account-types', { params })
}

export function updateAccountTypeSortOrders(params: SaveAccountTypeSortOrdersParams) {
  return requestPut<void, SaveAccountTypeSortOrdersParams>(financeRequest, '/api/finance/account-types/actions/sort-orders', params)
}

export function getAccounts(params: AccountQuery = {}) {
  return requestGet<Account[]>(financeRequest, '/api/finance/accounts', { params })
}

export function getAccount(id: number) {
  return requestGet<Account>(financeRequest, `/api/finance/accounts/${id}`)
}

export function getFinanceOverview(userId: number) {
  return requestGet<FinanceOverviewSummary>(financeRequest, '/api/finance/accounts/overview', {
    params: { userId },
  })
}

export function createAccount(params: CreateAccountParams) {
  return requestPost<Account, CreateAccountParams>(financeRequest, '/api/finance/accounts', params)
}

export function updateAccount(id: number, params: CreateAccountParams) {
  return requestPut<Account, CreateAccountParams>(financeRequest, `/api/finance/accounts/${id}`, params)
}

export function updateAccountSortOrders(params: SaveAccountSortOrdersParams) {
  return requestPut<void, SaveAccountSortOrdersParams>(financeRequest, '/api/finance/accounts/sort-orders', params)
}

export function deleteAccount(id: number) {
  return requestDelete<void>(financeRequest, `/api/finance/accounts/${id}`)
}

export function getDebtAccountSummary(userId: number, accountId?: number) {
  return requestGet<DebtAccountSummary>(financeRequest, '/api/finance/debt-accounts/summary', {
    params: {
      userId,
      accountId,
    },
  })
}

export function getDebtRecords(params: DebtRecordQuery) {
  return requestGet<DebtRecord[]>(financeRequest, '/api/finance/debt-accounts/records', { params })
}

export function createDebtRecord(params: SaveDebtRecordParams) {
  return requestPost<DebtRecord, SaveDebtRecordParams>(financeRequest, '/api/finance/debt-accounts/records', params)
}

export function updateDebtRecord(id: number, params: SaveDebtRecordParams) {
  return requestPut<DebtRecord, SaveDebtRecordParams>(financeRequest, `/api/finance/debt-accounts/records/${id}`, params)
}

export function deleteDebtRecord(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/debt-accounts/records/${id}`, {
    params: { userId },
  })
}

export function getLiabilityAccountSummary(userId: number, accountId?: number) {
  return requestGet<LiabilityAccountSummary>(financeRequest, '/api/finance/liability-accounts/summary', {
    params: {
      userId,
      accountId,
    },
  })
}

export function getLiabilityRecords(params: LiabilityRecordQuery) {
  return requestGet<LiabilityRecord[]>(financeRequest, '/api/finance/liability-accounts/records', { params })
}

export function createLiabilityRecord(params: SaveLiabilityRecordParams) {
  return requestPost<LiabilityRecord, SaveLiabilityRecordParams>(
    financeRequest,
    '/api/finance/liability-accounts/records',
    params,
  )
}

export function updateLiabilityRecord(id: number, params: SaveLiabilityRecordParams) {
  return requestPut<LiabilityRecord, SaveLiabilityRecordParams>(
    financeRequest,
    `/api/finance/liability-accounts/records/${id}`,
    params,
  )
}

export function repayLiabilityRecord(id: number, params: RepayLiabilityRecordParams) {
  return requestPost<LiabilityRecord, RepayLiabilityRecordParams>(
    financeRequest,
    `/api/finance/liability-accounts/records/${id}/repay`,
    params,
  )
}

export function prepayLiabilityAccount(accountId: number, params: PrepayLiabilityAccountParams) {
  return requestPost<void, PrepayLiabilityAccountParams>(
    financeRequest,
    `/api/finance/liability-accounts/${accountId}/prepay`,
    params,
  )
}

export function deleteLiabilityRecord(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/liability-accounts/records/${id}`, {
    params: { userId },
  })
}

export function getHumanRelationAccountSummary(userId: number, accountId?: number) {
  return requestGet<HumanRelationAccountSummary>(financeRequest, '/api/finance/human-relation-accounts/summary', {
    params: {
      userId,
      accountId,
    },
  })
}

export function getHumanRelationRecords(params: HumanRelationRecordQuery) {
  return requestGet<HumanRelationRecord[]>(financeRequest, '/api/finance/human-relation-accounts/records', { params })
}

export function createHumanRelationRecord(params: SaveHumanRelationRecordParams) {
  return requestPost<HumanRelationRecord, SaveHumanRelationRecordParams>(
    financeRequest,
    '/api/finance/human-relation-accounts/records',
    params,
  )
}

export function updateHumanRelationRecord(id: number, params: SaveHumanRelationRecordParams) {
  return requestPut<HumanRelationRecord, SaveHumanRelationRecordParams>(
    financeRequest,
    `/api/finance/human-relation-accounts/records/${id}`,
    params,
  )
}

export function deleteHumanRelationRecord(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/human-relation-accounts/records/${id}`, {
    params: { userId },
  })
}

export function getCategories(params: CategoryQuery = {}) {
  return requestGet<Category[]>(financeRequest, '/api/finance/categories', { params })
}

export function createCategory(params: SaveCategoryParams) {
  return requestPost<Category, SaveCategoryParams>(financeRequest, '/api/finance/categories', params)
}

export function updateCategory(id: number, params: SaveCategoryParams) {
  return requestPut<Category, SaveCategoryParams>(financeRequest, `/api/finance/categories/${id}`, params)
}

export function deleteCategory(id: number) {
  return requestDelete<void>(financeRequest, `/api/finance/categories/${id}`)
}

export function getGoldPrices(
  range: GoldPriceRange = '1d',
  forceRefreshCurrent = false,
  includeChart = true,
) {
  return requestGet<GoldPrice>(financeRequest, '/api/finance/gold-prices', {
    params: { range, forceRefreshCurrent, includeChart },
  })
}

export function getRealtimeGoldPrice() {
  return requestGet<GoldRealtimePrice>(financeRequest, '/api/finance/gold-prices/realtime')
}

export function getExchangeRate(from: string, to: string) {
  return requestGet<ExchangeRate>(financeRequest, '/api/finance/exchange-rates', {
    params: { from, to },
  })
}

export function getMarketNews(params: { category?: MarketNewsCategory; limit?: number } = {}) {
  return requestGet<MarketNews>(financeRequest, '/api/finance/market-news', { params })
}

export function createTransaction(params: CreateTransactionParams) {
  return requestPost<Transaction, CreateTransactionParams>(financeRequest, '/api/finance/transactions', params)
}

export function updateTransaction(id: number, params: UpdateTransactionParams) {
  return requestPut<Transaction, UpdateTransactionParams>(financeRequest, `/api/finance/transactions/${id}`, params)
}

export function getTransactions(params: TransactionQuery = {}) {
  return requestGet<Transaction[]>(financeRequest, '/api/finance/transactions', { params })
}

export function getTransactionPage(params: TransactionPageQuery = {}) {
  return requestGet<TransactionPage>(financeRequest, '/api/finance/transactions/page', { params })
}

export function getTransactionAnalysis(params: TransactionAnalysisQuery) {
  return requestGet<TransactionAnalysis>(financeRequest, '/api/finance/transactions/analysis', { params })
}

export function getAccountTransactions(accountId: number, params: Omit<TransactionQuery, 'accountId'> = {}) {
  return requestGet<Transaction[]>(financeRequest, `/api/finance/transactions/accounts/${accountId}`, { params })
}

export function deleteTransaction(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/transactions/${id}`, {
    params: { userId },
  })
}

export function getMonthlyBudgets(params: MonthlyBudgetQuery) {
  return requestGet<MonthlyBudget[]>(financeRequest, '/api/finance/monthly-budgets', { params })
}

export function getCurrentMonthlyBudget(userId: number, budgetMonth: string) {
  return requestGet<MonthlyBudget | null>(financeRequest, '/api/finance/monthly-budgets/current', {
    params: { userId, budgetMonth },
  })
}

export function createMonthlyBudget(params: SaveMonthlyBudgetParams) {
  return requestPost<MonthlyBudget, SaveMonthlyBudgetParams>(financeRequest, '/api/finance/monthly-budgets', params)
}

export function updateMonthlyBudget(id: number, params: SaveMonthlyBudgetParams) {
  return requestPut<MonthlyBudget, SaveMonthlyBudgetParams>(financeRequest, `/api/finance/monthly-budgets/${id}`, params)
}

export function deleteMonthlyBudget(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/monthly-budgets/${id}`, {
    params: { userId },
  })
}

export function getRenewalSubscriptions(params: RenewalSubscriptionQuery) {
  return requestGet<RenewalSubscription[]>(financeRequest, '/api/finance/renewal-subscriptions', { params })
}

export function getRenewalSubscriptionSummary(userId: number) {
  return requestGet<RenewalSubscriptionSummary>(financeRequest, '/api/finance/renewal-subscriptions/summary', {
    params: { userId },
  })
}

export function createRenewalSubscription(params: SaveRenewalSubscriptionParams) {
  return requestPost<RenewalSubscription, SaveRenewalSubscriptionParams>(
    financeRequest,
    '/api/finance/renewal-subscriptions',
    params,
  )
}

export function updateRenewalSubscription(id: number, params: SaveRenewalSubscriptionParams) {
  return requestPut<RenewalSubscription, SaveRenewalSubscriptionParams>(
    financeRequest,
    `/api/finance/renewal-subscriptions/${id}`,
    params,
  )
}

export function pauseRenewalSubscription(id: number, userId: number) {
  return requestPost<RenewalSubscription, undefined>(
    financeRequest,
    `/api/finance/renewal-subscriptions/${id}/pause?userId=${userId}`,
    undefined,
  )
}

export function resumeRenewalSubscription(id: number, userId: number) {
  return requestPost<RenewalSubscription, undefined>(
    financeRequest,
    `/api/finance/renewal-subscriptions/${id}/resume?userId=${userId}`,
    undefined,
  )
}

export function deleteRenewalSubscription(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/renewal-subscriptions/${id}`, {
    params: { userId },
  })
}

export function getInvestmentProducts(params: { productType?: InvestmentProductType; keyword?: string } = {}) {
  return requestGet<InvestmentProduct[]>(financeRequest, '/api/finance/investments/products', { params })
}

export function createInvestmentProduct(params: SaveInvestmentProductParams) {
  return requestPost<InvestmentProduct, SaveInvestmentProductParams>(financeRequest, '/api/finance/investments/products', params)
}

export function getInvestmentSummary(params: { userId: number; accountId?: number }) {
  return requestGet<InvestmentSummary>(financeRequest, '/api/finance/investments/summary', { params })
}

export function getInvestmentTrend(params: { userId: number; accountId?: number; range?: InvestmentTrendRange }) {
  return requestGet<InvestmentTrend>(financeRequest, '/api/finance/investments/trend', { params })
}

export function getAssetTrend(params: { userId: number; accountId?: number; range?: AssetTrendRange }) {
  return requestGet<AssetTrend>(financeRequest, '/api/finance/accounts/trend', { params })
}

export function getLatestAssetAccountSnapshots(params: { userId: number }) {
  return requestGet<AssetAccountSnapshot>(financeRequest, '/api/finance/accounts/snapshots/latest', { params })
}

export function getFundProfitForecast(params: { userId: number; accountId?: number }) {
  return requestGet<FundProfitForecast>(financeRequest, '/api/finance/investments/fund-profit-forecast', { params })
}

export function getFundProfitPage(params: {
  userId: number
  accountId?: number
  view?: FundProfitView
  anchor?: string
  selected?: string
}) {
  return requestGet<FundProfitPage>(financeRequest, '/api/finance/investments/fund-profit', { params })
}

export function getInvestmentPositions(params: InvestmentPositionQuery) {
  return requestGet<InvestmentPosition[]>(financeRequest, '/api/finance/investments/positions', { params })
}

export function getInvestmentPosition(id: number) {
  return requestGet<InvestmentPosition>(financeRequest, `/api/finance/investments/positions/${id}`)
}

export function getInvestmentPositionDetail(id: number) {
  return requestGet<InvestmentAssetDetail>(financeRequest, `/api/finance/investments/positions/${id}/detail`)
}

export function runInvestmentQuoteSyncTask(params?: { userId?: number; accountId?: number }) {
  return requestPost<InvestmentQuoteSyncSummary, undefined>(
    financeRequest,
    '/api/finance/investments/tasks/quote-sync',
    undefined,
    { params },
  )
}

export function createInvestmentPosition(params: SaveInvestmentPositionParams) {
  return requestPost<InvestmentPosition, SaveInvestmentPositionParams>(financeRequest, '/api/finance/investments/positions', params)
}

export function updateInvestmentPosition(id: number, params: SaveInvestmentPositionParams) {
  return requestPut<InvestmentPosition, SaveInvestmentPositionParams>(financeRequest, `/api/finance/investments/positions/${id}`, params)
}

export function deleteInvestmentPosition(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/investments/positions/${id}`, {
    params: { userId },
  })
}

export function getInvestmentTransactions(params: { userId: number; accountId?: number; positionId?: number }) {
  return requestGet<InvestmentTransaction[]>(financeRequest, '/api/finance/investments/transactions', { params })
}

export function getInvestmentTransactionPage(params: { userId: number; accountId?: number; positionId?: number; page?: number; pageSize?: number }) {
  return requestGet<InvestmentTransactionPage>(financeRequest, '/api/finance/investments/transactions/page', { params })
}

export function createInvestmentTransaction(params: SaveInvestmentTransactionParams) {
  return requestPost<InvestmentTransaction, SaveInvestmentTransactionParams>(financeRequest, '/api/finance/investments/transactions', params)
}

export function getInvestmentAutoInvestPlans(params: { userId: number; accountId?: number; positionId?: number; status?: string }) {
  return requestGet<InvestmentAutoInvestPlan[]>(financeRequest, '/api/finance/investments/auto-invest-plans', { params })
}

export function createInvestmentAutoInvestPlan(params: SaveInvestmentAutoInvestPlanParams) {
  return requestPost<InvestmentAutoInvestPlan, SaveInvestmentAutoInvestPlanParams>(financeRequest, '/api/finance/investments/auto-invest-plans', params)
}

export function updateInvestmentAutoInvestPlan(id: number, params: SaveInvestmentAutoInvestPlanParams) {
  return requestPut<InvestmentAutoInvestPlan, SaveInvestmentAutoInvestPlanParams>(financeRequest, `/api/finance/investments/auto-invest-plans/${id}`, params)
}

export function deleteInvestmentAutoInvestPlan(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/investments/auto-invest-plans/${id}`, {
    params: { userId },
  })
}

export function getInvestmentDividendIncome(userId: number) {
  return requestGet<InvestmentDividendIncomePage>(financeRequest, '/api/finance/investments/dividend-income', {
    params: { userId },
  })
}

export function getInvestmentDividendForecast(params: InvestmentDividendForecastRequest) {
  return requestPost<InvestmentDividendForecast, InvestmentDividendForecastRequest>(
    financeRequest,
    '/api/finance/investments/dividend-forecast',
    params,
  )
}

export function getInvestmentFixedExpenses(userId: number) {
  return requestGet<InvestmentFixedExpense[]>(financeRequest, '/api/finance/investments/fixed-expenses', {
    params: { userId },
  })
}

export function createInvestmentFixedExpense(params: SaveInvestmentFixedExpenseParams) {
  return requestPost<InvestmentFixedExpense, SaveInvestmentFixedExpenseParams>(
    financeRequest,
    '/api/finance/investments/fixed-expenses',
    params,
  )
}

export function updateInvestmentFixedExpense(id: number, params: SaveInvestmentFixedExpenseParams) {
  return requestPut<InvestmentFixedExpense, SaveInvestmentFixedExpenseParams>(
    financeRequest,
    `/api/finance/investments/fixed-expenses/${id}`,
    params,
  )
}

export function deleteInvestmentFixedExpense(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/investments/fixed-expenses/${id}`, {
    params: { userId },
  })
}

export interface GoldAccountSummary {
  totalWeight: number
  averagePrice: number
  purchaseTotal: number
  estimatedValue: number
  estimatedProfit: number
  profitRate: number
  cumulativeProfit: number
}

export interface GoldAccountHolding {
  id: number
  accountId: number
  accountName?: string | null
  positionId: number
  productId: number
  productName?: string | null
  productSymbol?: string | null
  currentPrice: number
  purchaseAmount: number
  weight: number
  holdingProfit: number
  marketValue: number
  avgCostPrice: number
  createdAt: string
}

export interface GoldLiquidationRecord {
  id: number
  accountId: number
  accountName?: string | null
  positionId: number
  productId: number
  productName?: string | null
  productSymbol?: string | null
  tradeAt: string
  profit: number
  weight: number
  buyPrice: number
  sellPrice: number
  fee: number
}

export interface GoldLiquidation {
  cumulativeWeight: number
  cumulativeProfit: number
  records: GoldLiquidationRecord[]
}

export function getGoldAccountSummary(userId: number) {
  return requestGet<GoldAccountSummary>(financeRequest, '/api/finance/gold-accounts/summary', {
    params: { userId },
  })
}

export function getGoldAccountHoldings(userId: number) {
  return requestGet<GoldAccountHolding[]>(financeRequest, '/api/finance/gold-accounts/holdings', {
    params: { userId },
  })
}

export function getGoldLiquidations(userId: number) {
  return requestGet<GoldLiquidation>(financeRequest, '/api/finance/gold-accounts/liquidations', {
    params: { userId },
  })
}

export type SalaryAccountType = 'social_security' | 'housing_fund' | 'medical'
export type SalaryRecordType = 'initial' | 'auto' | 'manual'

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
  accountType: SalaryAccountType
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

export interface SalaryAccountPage {
  accountType: SalaryAccountType
  title: string
  subtitle: string
  badgeText: string
  year: number
  currentBalance: number
  initialBalance: number
  monthlyPersonal: number
  monthlyCompany: number
  yearlyIncrease: number
  metrics: SalaryAccountMetricItem[]
  details: SalaryAccountDetailItem[]
  records: SalaryAccountRecordItem[]
  updatedAt?: string | null
}

export interface SaveSalaryInitialBalanceParams {
  userId: number
  amount: number
  recordMonth: string
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

export function getSalaryOverview(userId: number, month?: string) {
  return requestGet<SalaryOverview>(financeRequest, '/api/finance/salary/overview', {
    params: { userId, month },
  })
}

export function getSalarySettings(userId: number, taxYear?: number) {
  return requestGet<SalarySettings>(financeRequest, '/api/finance/salary/settings', {
    params: { userId, taxYear },
  })
}

export function saveSalarySettings(params: SaveSalarySettingsParams) {
  return requestPut<SalarySettings, SaveSalarySettingsParams>(financeRequest, '/api/finance/salary/settings', params)
}

export function getSalaryMonthPage(userId: number, year?: number) {
  return requestGet<SalaryMonthPage>(financeRequest, '/api/finance/salary/records', {
    params: { userId, year },
  })
}

export function createSalaryMonthRecord(params: SaveSalaryMonthRecordParams) {
  return requestPost<SalaryMonthPage, SaveSalaryMonthRecordParams>(financeRequest, '/api/finance/salary/records', params)
}

export function updateSalaryMonthRecord(recordId: number, params: SaveSalaryMonthRecordParams) {
  return requestPut<SalaryMonthPage, SaveSalaryMonthRecordParams>(financeRequest, `/api/finance/salary/records/${recordId}`, params)
}

export function deleteSalaryMonthRecord(recordId: number, userId: number) {
  return requestDelete<SalaryMonthPage>(financeRequest, `/api/finance/salary/records/${recordId}`, {
    params: { userId },
  })
}

export function getSalaryAccountPage(userId: number, accountType: string, year?: number) {
  return requestGet<SalaryAccountPage>(financeRequest, `/api/finance/salary/accounts/${accountType}`, {
    params: { userId, year },
  })
}

export function saveSalaryInitialBalance(accountType: string, params: SaveSalaryInitialBalanceParams) {
  return requestPut<SalaryAccountPage, SaveSalaryInitialBalanceParams>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}/initial-balance`,
    params,
  )
}

export function createSalaryAccountRecord(accountType: string, params: SaveSalaryAccountRecordParams) {
  return requestPost<SalaryAccountPage, SaveSalaryAccountRecordParams>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}/records`,
    params,
  )
}

export function updateSalaryAccountRecord(accountType: string, recordId: number, params: SaveSalaryAccountRecordParams) {
  return requestPut<SalaryAccountPage, SaveSalaryAccountRecordParams>(
    financeRequest,
    `/api/finance/salary/accounts/${accountType}/records/${recordId}`,
    params,
  )
}

export function deleteSalaryAccountRecord(accountType: string, recordId: number, userId: number) {
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
