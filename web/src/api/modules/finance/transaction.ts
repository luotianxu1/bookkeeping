// 收支交易/分析/预算接口（由 finance.ts 拆出；类型定义见 ./types）
import { financeRequest, requestDelete, requestGet, requestPost, requestPut } from '@/api/request'
import type {
  FreeEstimate,
  Transaction,
  TransactionQuery,
  TransactionPageQuery,
  TransactionPage,
  TransactionAnalysis,
  TransactionAnalysisQuery,
  MonthlyBudget,
  MonthlyBudgetQuery,
  CreateTransactionParams,
  UpdateTransactionParams,
  SaveMonthlyBudgetParams,
} from './types'

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

export function getFreeEstimate(params: { userId: number }) {
  return requestGet<FreeEstimate>(financeRequest, '/api/finance/accounts/free-estimate', { params })
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

export function getCategoryBudgets(month: string) {
  return requestGet<import('./types').CategoryBudget[]>(financeRequest, '/api/finance/category-budgets', { params: { month } })
}
export function saveCategoryBudget(params: { budgetMonth: string; categoryId: number; amount: number }) {
  return requestPut<void>(financeRequest, '/api/finance/category-budgets', params)
}
export function deleteCategoryBudget(id: number) {
  return requestDelete<void>(financeRequest, `/api/finance/category-budgets/${id}`)
}
export function getExpenseRecoveries(params: { state: string; page: number; transactionId?: number }) {
  return requestGet<import('./types').ExpenseRecoveryPage>(financeRequest, '/api/finance/expense-recoveries', { params })
}
export function getExpenseRecovery(id: number) {
  return requestGet<import('./types').ExpenseRecovery>(financeRequest, `/api/finance/expense-recoveries/${id}`)
}
export function saveReimbursementPlan(id: number, amount: number) {
  return requestPut<import('./types').ExpenseRecovery>(financeRequest, `/api/finance/expense-recoveries/${id}/plan`, { amount })
}
export function receiveExpenseRecovery(params: {
  transactionId: number; accountId: number; amount: number; kind: 'refund' | 'reimbursement'; occurredAt: string; remark?: string; requestId: string
}) {
  return requestPost<import('./types').ExpenseReceipt>(financeRequest, '/api/finance/expense-recoveries', params)
}
