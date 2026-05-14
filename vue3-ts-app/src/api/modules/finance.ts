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

export interface Account {
  id: number
  userId: number
  accountTypeId: number
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

export interface CreateAccountParams {
  userId: number
  accountTypeId: number
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

export function getAccountTypes(params: AccountTypeQuery = {}) {
  return requestGet<AccountType[]>(financeRequest, '/api/finance/account-types', { params })
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

export function deleteAccount(id: number) {
  return requestDelete<void>(financeRequest, `/api/finance/accounts/${id}`)
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
