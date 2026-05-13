import { financeRequest, requestGet, requestPost } from '@/api/request'

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

export function createAccount(params: CreateAccountParams) {
  return requestPost<Account, CreateAccountParams>(financeRequest, '/api/finance/accounts', params)
}
