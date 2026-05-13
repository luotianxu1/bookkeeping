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

export function getAccountTypes(params: AccountTypeQuery = {}) {
  return requestGet<AccountType[]>(financeRequest, '/api/finance/account-types', { params })
}

export function getAccounts(params: AccountQuery = {}) {
  return requestGet<Account[]>(financeRequest, '/api/finance/accounts', { params })
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
