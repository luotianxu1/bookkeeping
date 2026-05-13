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
