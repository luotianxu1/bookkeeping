import {
  financeRequest,
  requestGet,
  requestPost,
  requestPut,
} from '@/business/api/request'
export type TermAction = 'withdraw' | 'renew_principal' | 'renew_all'
export interface TermDeposit {
  id: number
  accountId: number
  accountName: string
  fundingAccountId: number | null
  payoutAccountId: number
  interestCategoryId: number | null
  interestCategoryName: string | null
  parentDepositId: number | null
  name: string
  currencyCode: string
  principal: number
  annualRate: number
  startDate: string
  maturityDate: string
  termMonths: number | null
  maturityAction: TermAction
  openingMode: 'new' | 'existing' | 'renewal'
  status: 'active' | 'due' | 'closed' | 'voided'
  remark: string | null
  expectedInterest: number
  expectedTotal: number
  daysRemaining: number
  asOfDate: string
  canCancel: boolean
}
export interface TermOperation {
  id: number
  operation: string
  cashAccountId: number | null
  cashAccountName: string | null
  childDepositId: number | null
  interestCategoryId: number | null
  interestCategoryName: string | null
  principal: number
  interest: number
  cashDelta: number
  occurredOn: string
  detail: string | null
  reversedAt: string | null
  createdAt: string
}
export interface TermDetail {
  deposit: TermDeposit
  operations: TermOperation[]
}
export interface TermTerms {
  name: string
  payoutAccountId: number
  annualRate: number
  startDate: string
  maturityDate: string
  interestCategoryId: number | null
  termMonths: number | null
  maturityAction: TermAction
  remark: string | null
  requestId?: string
}
export interface CreateTermDeposit extends TermTerms {
  accountId: number
  fundingAccountId: number | null
  principal: number
  openingMode: 'new' | 'existing'
  savingsAllocations: { goalId: number; amount: number }[]
}
export interface SettleTermDeposit {
  action: TermAction
  payoutAccountId: number
  interestCategoryId: number | null
  actualInterest: number
  occurredOn: string
  renewalAnnualRate: number | null
  renewalMaturityDate: string | null
  renewalTermMonths: number | null
  remark: string | null
  requestId?: string
}
const path = '/api/finance/term-deposits'
export const getTermDeposits = (signal?: AbortSignal) =>
  requestGet<TermDeposit[]>(financeRequest, path, { signal })
export const getTermDeposit = (id: number, signal?: AbortSignal) =>
  requestGet<TermDetail>(financeRequest, `${path}/${id}`, { signal })
export const createTermDeposit = (data: CreateTermDeposit) =>
  requestPost<TermDeposit>(financeRequest, path, data)
export const updateTermDeposit = (id: number, data: TermTerms) =>
  requestPut<TermDeposit>(financeRequest, `${path}/${id}`, data)
export const settleTermDeposit = (id: number, data: SettleTermDeposit) =>
  requestPost<TermDeposit>(financeRequest, `${path}/${id}/settle`, data)
export const reverseTermDeposit = (
  id: number,
  data: { operationId: number; requestId?: string },
) => requestPost<TermDeposit>(financeRequest, `${path}/${id}/reverse`, data)
export const cancelTermDeposit = (id: number, data: { requestId?: string }) =>
  requestPost<TermDeposit>(financeRequest, `${path}/${id}/cancel`, data)
