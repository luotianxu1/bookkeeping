import { financeRequest, requestGet } from '@/api/request'

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

export function getAccountTypes(params: AccountTypeQuery = {}) {
  return requestGet<AccountType[]>(financeRequest, '/api/finance/account-types', { params })
}
