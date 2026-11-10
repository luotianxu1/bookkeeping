// 账户/账户类型/负债/人情/分类/汇率接口（由 finance.ts 拆出；类型定义见 ./types）
import type { AxiosRequestConfig } from 'axios'
import {
  financeRequest,
  requestDelete,
  requestGet,
  requestPost,
  requestPut,
} from '@/business/api/request'
import type {
  AccountType,
  AccountTypeQuery,
  AccountQuery,
  FinanceOverviewSummary,
  DebtAccountSummary,
  LiabilityAccountSummary,
  HumanRelationAccountSummary,
  DebtRecord,
  LiabilityRecord,
  HumanRelationRecord,
  Account,
  Category,
  CategoryQuery,
  ExchangeRate,
  CreateAccountParams,
  DebtRecordQuery,
  SaveDebtRecordParams,
  LiabilityRecordQuery,
  SaveLiabilityRecordParams,
  RepayLiabilityRecordParams,
  PrepayLiabilityAccountParams,
  HumanRelationRecordQuery,
  SaveHumanRelationRecordParams,
  SaveAccountSortOrdersParams,
  SaveAccountTypeSortOrdersParams,
  SaveCategoryParams,
} from './types'

export function getAccountTypes(
  params: AccountTypeQuery = {},
  config?: AxiosRequestConfig,
) {
  return requestGet<AccountType[]>(
    financeRequest,
    '/api/finance/account-types',
    { params, ...config },
  )
}

export function updateAccountTypeSortOrders(
  params: SaveAccountTypeSortOrdersParams,
) {
  return requestPut<void, SaveAccountTypeSortOrdersParams>(
    financeRequest,
    '/api/finance/account-types/actions/sort-orders',
    params,
  )
}

export function getAccounts(
  params: AccountQuery = {},
  config?: AxiosRequestConfig,
) {
  return requestGet<Account[]>(financeRequest, '/api/finance/accounts', {
    params,
    ...config,
  })
}

export interface AccountSummary {
  accounts: Account[]
  totalAssets: number
}

export function getAccountSummary(userId: number, config?: AxiosRequestConfig) {
  return requestGet<AccountSummary>(
    financeRequest,
    '/api/finance/accounts/summary',
    { params: { userId }, ...config },
  )
}

export function getAccount(id: number) {
  return requestGet<Account>(financeRequest, `/api/finance/accounts/${id}`)
}

export function getFinanceOverview(userId: number) {
  return requestGet<FinanceOverviewSummary>(
    financeRequest,
    '/api/finance/accounts/overview',
    {
      params: { userId },
    },
  )
}

export function createAccount(params: CreateAccountParams) {
  return requestPost<Account, CreateAccountParams>(
    financeRequest,
    '/api/finance/accounts',
    params,
  )
}

export function updateAccount(id: number, params: CreateAccountParams) {
  return requestPut<Account, CreateAccountParams>(
    financeRequest,
    `/api/finance/accounts/${id}`,
    params,
  )
}

export function updateAccountSortOrders(params: SaveAccountSortOrdersParams) {
  return requestPut<void, SaveAccountSortOrdersParams>(
    financeRequest,
    '/api/finance/accounts/sort-orders',
    params,
  )
}

export function deleteAccount(id: number) {
  return requestDelete<void>(financeRequest, `/api/finance/accounts/${id}`)
}

export function getDebtAccountSummary(userId: number, accountId?: number) {
  return requestGet<DebtAccountSummary>(
    financeRequest,
    '/api/finance/debt-accounts/summary',
    {
      params: {
        userId,
        accountId,
      },
    },
  )
}

export function getDebtRecords(params: DebtRecordQuery) {
  return requestGet<DebtRecord[]>(
    financeRequest,
    '/api/finance/debt-accounts/records',
    { params },
  )
}

export function createDebtRecord(params: SaveDebtRecordParams) {
  return requestPost<DebtRecord, SaveDebtRecordParams>(
    financeRequest,
    '/api/finance/debt-accounts/records',
    params,
  )
}

export function updateDebtRecord(id: number, params: SaveDebtRecordParams) {
  return requestPut<DebtRecord, SaveDebtRecordParams>(
    financeRequest,
    `/api/finance/debt-accounts/records/${id}`,
    params,
  )
}

export function deleteDebtRecord(id: number, userId: number) {
  return requestDelete<void>(
    financeRequest,
    `/api/finance/debt-accounts/records/${id}`,
    {
      params: { userId },
    },
  )
}

export function getLiabilityAccountSummary(userId: number, accountId?: number) {
  return requestGet<LiabilityAccountSummary>(
    financeRequest,
    '/api/finance/liability-accounts/summary',
    {
      params: {
        userId,
        accountId,
      },
    },
  )
}

export function getLiabilityRecords(params: LiabilityRecordQuery) {
  return requestGet<LiabilityRecord[]>(
    financeRequest,
    '/api/finance/liability-accounts/records',
    { params },
  )
}

export function createLiabilityRecord(params: SaveLiabilityRecordParams) {
  return requestPost<LiabilityRecord, SaveLiabilityRecordParams>(
    financeRequest,
    '/api/finance/liability-accounts/records',
    params,
  )
}

export function updateLiabilityRecord(
  id: number,
  params: SaveLiabilityRecordParams,
) {
  return requestPut<LiabilityRecord, SaveLiabilityRecordParams>(
    financeRequest,
    `/api/finance/liability-accounts/records/${id}`,
    params,
  )
}

export function repayLiabilityRecord(
  id: number,
  params: RepayLiabilityRecordParams,
) {
  return requestPost<LiabilityRecord, RepayLiabilityRecordParams>(
    financeRequest,
    `/api/finance/liability-accounts/records/${id}/repay`,
    params,
  )
}

export function prepayLiabilityAccount(
  accountId: number,
  params: PrepayLiabilityAccountParams,
) {
  return requestPost<void, PrepayLiabilityAccountParams>(
    financeRequest,
    `/api/finance/liability-accounts/${accountId}/prepay`,
    params,
  )
}

export function deleteLiabilityRecord(id: number, userId: number) {
  return requestDelete<void>(
    financeRequest,
    `/api/finance/liability-accounts/records/${id}`,
    {
      params: { userId },
    },
  )
}

export function getHumanRelationAccountSummary(
  userId: number,
  accountId?: number,
) {
  return requestGet<HumanRelationAccountSummary>(
    financeRequest,
    '/api/finance/human-relation-accounts/summary',
    {
      params: {
        userId,
        accountId,
      },
    },
  )
}

export function getHumanRelationRecords(params: HumanRelationRecordQuery) {
  return requestGet<HumanRelationRecord[]>(
    financeRequest,
    '/api/finance/human-relation-accounts/records',
    { params },
  )
}

export function createHumanRelationRecord(
  params: SaveHumanRelationRecordParams,
) {
  return requestPost<HumanRelationRecord, SaveHumanRelationRecordParams>(
    financeRequest,
    '/api/finance/human-relation-accounts/records',
    params,
  )
}

export function updateHumanRelationRecord(
  id: number,
  params: SaveHumanRelationRecordParams,
) {
  return requestPut<HumanRelationRecord, SaveHumanRelationRecordParams>(
    financeRequest,
    `/api/finance/human-relation-accounts/records/${id}`,
    params,
  )
}

export function deleteHumanRelationRecord(id: number, userId: number) {
  return requestDelete<void>(
    financeRequest,
    `/api/finance/human-relation-accounts/records/${id}`,
    {
      params: { userId },
    },
  )
}

export function getCategories(params: CategoryQuery = {}) {
  return requestGet<Category[]>(financeRequest, '/api/finance/categories', {
    params,
  })
}

export function createCategory(params: SaveCategoryParams) {
  return requestPost<Category, SaveCategoryParams>(
    financeRequest,
    '/api/finance/categories',
    params,
  )
}

export function updateCategory(id: number, params: SaveCategoryParams) {
  return requestPut<Category, SaveCategoryParams>(
    financeRequest,
    `/api/finance/categories/${id}`,
    params,
  )
}

export function deleteCategory(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/categories/${id}`, {
    params: { userId },
  })
}

export function getExchangeRate(from: string, to: string) {
  return requestGet<ExchangeRate>(
    financeRequest,
    '/api/finance/exchange-rates',
    {
      params: { from, to },
    },
  )
}
