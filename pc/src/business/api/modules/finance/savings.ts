import {
  financeRequest,
  requestDelete,
  requestGet,
  requestPost,
  requestPut,
} from '@/business/api/request'

export interface SavingsGoal {
  id: number
  name: string
  accountId: number
  accountName: string | null
  currencyCode: string
  targetAmount: number
  targetDate: string | null
  remark: string | null
  accountAvailable: boolean
  currentAmount: number
  remainingAmount: number
  progressPercent: number
  status: 'saving' | 'completed' | 'overdue'
  monthsRemaining: number | null
  monthlyNeeded: number | null
  asOfDate: string
  accountBalance: number | null
  accountReservedAmount: number
  accountAvailableAmount: number | null
}

export interface SaveSavingsGoal {
  name: string
  accountId: number
  targetAmount: number
  targetDate: string | null
  remark: string | null
  requestId?: string
}

const path = '/api/finance/savings-goals'
export const getSavingsGoals = (signal?: AbortSignal) =>
  requestGet<SavingsGoal[]>(financeRequest, path, { signal })
export const createSavingsGoal = (data: SaveSavingsGoal) =>
  requestPost<SavingsGoal>(financeRequest, path, data)
export const updateSavingsGoal = (id: number, data: SaveSavingsGoal) =>
  requestPut<SavingsGoal>(financeRequest, `${path}/${id}`, data)
export const deleteSavingsGoal = (id: number) =>
  requestDelete<void>(financeRequest, `${path}/${id}`)

export interface SavingsGoalEntry {
  id: number
  goalId: number
  type: 'deposit' | 'withdrawal'
  amount: number
  occurredOn: string
  remark: string | null
  createdAt: string
  transactionId: number | null
}
export interface SaveSavingsGoalEntry {
  type: SavingsGoalEntry['type']
  amount: number
  occurredOn: string
  remark: string | null
  requestId?: string
}
export interface SavingsGoalEntryPage {
  entries: SavingsGoalEntry[]
  nextBeforeId: number | null
}
export const getSavingsGoalEntries = (
  id: number,
  beforeId: number | null,
  signal?: AbortSignal,
) =>
  requestGet<SavingsGoalEntryPage>(financeRequest, `${path}/${id}/entries`, {
    params: { beforeId },
    signal,
  })
export const addSavingsGoalEntry = (id: number, data: SaveSavingsGoalEntry) =>
  requestPost<SavingsGoalEntry>(financeRequest, `${path}/${id}/entries`, data)
