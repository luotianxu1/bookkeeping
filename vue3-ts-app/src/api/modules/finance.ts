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

export interface DebtAccountSummary {
  netAmount: number
  payableTotal: number
  receivableTotal: number
  accountCount: number
  recordCount: number
}

export type DebtDirection = 'payable' | 'receivable'
export type HumanRelationDirection = 'outgoing' | 'incoming'

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
  amount: number
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

export type GoldPriceRange = '1d' | '7d' | '30d' | '1y'

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
  categoryId: number
  categoryName?: string | null
  categoryIcon?: string | null
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

export interface CreateAccountParams {
  userId: number
  accountTypeId: number
  contactId?: number | null
  name: string
  icon?: string | null
  color?: string | null
  currencyCode?: string
  currentBalance?: number
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
  amount: number
  currencyCode?: string
  remark?: string | null
  occurredAt?: string
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
  dayProfit: number
  dayProfitRate: number
  holdingProfit: number
  holdingProfitRate: number
  cumulativeProfit: number
  cumulativeProfitRate: number
  lastSyncedAt?: string | null
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
  dayProfit: number
  dayProfitRate: number
  holdingProfit: number
  holdingProfitRate: number
  cumulativeProfit: number
  cumulativeProfitRate: number
  includeInNetWorth: boolean
  status: string
  lastSyncedAt?: string | null
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
  chartPoints: InvestmentChartPoint[]
  chartType?: 'line' | 'candlestick' | string | null
  source?: string | null
  description?: string | null
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
  tradeAt: string
  status: string
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface SaveInvestmentTransactionParams {
  userId: number
  accountId: number
  positionId: number
  productId: number
  tradeType: 'buy' | 'sell'
  quantity: number
  price?: number | null
  amount: number
  feeAmount?: number
  taxAmount?: number
  currencyCode?: string
  tradeAt: string
  fundingAccountId?: number
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
  holdingQuantity: number
  availableQuantity?: number
  frozenQuantity?: number
  costAmount: number
  currentPrice?: number
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

export function getGoldPrices(range: GoldPriceRange = '1d') {
  return requestGet<GoldPrice>(financeRequest, '/api/finance/gold-prices', {
    params: { range },
  })
}

export function getExchangeRate(from: string, to: string) {
  return requestGet<ExchangeRate>(financeRequest, '/api/finance/exchange-rates', {
    params: { from, to },
  })
}

export function createTransaction(params: CreateTransactionParams) {
  return requestPost<Transaction, CreateTransactionParams>(financeRequest, '/api/finance/transactions', params)
}

export function getTransactions(params: TransactionQuery = {}) {
  return requestGet<Transaction[]>(financeRequest, '/api/finance/transactions', { params })
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

export function getInvestmentProducts(params: { productType?: InvestmentProductType; keyword?: string } = {}) {
  return requestGet<InvestmentProduct[]>(financeRequest, '/api/finance/investments/products', { params })
}

export function createInvestmentProduct(params: SaveInvestmentProductParams) {
  return requestPost<InvestmentProduct, SaveInvestmentProductParams>(financeRequest, '/api/finance/investments/products', params)
}

export function getInvestmentSummary(params: { userId: number; accountId?: number }) {
  return requestGet<InvestmentSummary>(financeRequest, '/api/finance/investments/summary', { params })
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

export function createInvestmentTransaction(params: SaveInvestmentTransactionParams) {
  return requestPost<InvestmentTransaction, SaveInvestmentTransactionParams>(financeRequest, '/api/finance/investments/transactions', params)
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
