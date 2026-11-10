// 投资与黄金账户接口（由 finance.ts 拆出；类型定义见 ./types）
import type { AxiosRequestConfig } from 'axios'
import {
  financeRequest,
  requestDelete,
  requestGet,
  requestPost,
  requestPut,
} from '@/business/api/request'
import type {
  InvestmentProductType,
  InvestmentProduct,
  SaveInvestmentProductParams,
  InvestmentSummary,
  InvestmentTrendRange,
  AssetTrendRange,
  InvestmentTrend,
  AssetAccountSnapshot,
  AssetTrend,
  FundProfitForecast,
  FundProfitView,
  FundProfitPage,
  InvestmentPosition,
  InvestmentAssetDetail,
  InvestmentFundRedeemFeeOption,
  InvestmentQuoteSyncSummary,
  InvestmentFixedExpense,
  SaveInvestmentFixedExpenseParams,
  InvestmentDividendIncomePage,
  InvestmentDividendForecastRequest,
  InvestmentDividendForecast,
  InvestmentProductQuote,
  InvestmentTransaction,
  InvestmentTransactionPage,
  InvestmentAutoInvestPlan,
  SaveInvestmentAutoInvestPlanParams,
  SaveInvestmentTransactionParams,
  InvestmentPositionQuery,
  SaveInvestmentPositionParams,
} from './types'

export function getInvestmentProducts(
  params: { productType?: InvestmentProductType; keyword?: string } = {},
) {
  return requestGet<InvestmentProduct[]>(
    financeRequest,
    '/api/finance/investments/products',
    { params },
  )
}

export function createInvestmentProduct(params: SaveInvestmentProductParams) {
  return requestPost<InvestmentProduct, SaveInvestmentProductParams>(
    financeRequest,
    '/api/finance/investments/products',
    params,
  )
}

export function getInvestmentSummary(
  params: { userId: number; accountId?: number },
  config?: AxiosRequestConfig,
) {
  return requestGet<InvestmentSummary>(
    financeRequest,
    '/api/finance/investments/summary',
    { params, ...config },
  )
}

export function getInvestmentTrend(params: {
  userId: number
  accountId?: number
  range?: InvestmentTrendRange
}) {
  return requestGet<InvestmentTrend>(
    financeRequest,
    '/api/finance/investments/trend',
    { params },
  )
}

export function getAssetTrend(params: {
  userId: number
  accountId?: number
  range?: AssetTrendRange
}) {
  return requestGet<AssetTrend>(financeRequest, '/api/finance/accounts/trend', {
    params,
  })
}

export function getLatestAssetAccountSnapshots(params: { userId: number }) {
  return requestGet<AssetAccountSnapshot>(
    financeRequest,
    '/api/finance/accounts/snapshots/latest',
    { params },
  )
}

export function getFundProfitForecast(params: {
  userId: number
  accountId?: number
}) {
  return requestGet<FundProfitForecast>(
    financeRequest,
    '/api/finance/investments/fund-profit-forecast',
    { params },
  )
}

export function getFundProfitPage(
  params: {
    userId: number
    accountId?: number
    view?: FundProfitView
    anchor?: string
    selected?: string
  },
  config?: AxiosRequestConfig,
) {
  return requestGet<FundProfitPage>(
    financeRequest,
    '/api/finance/investments/fund-profit',
    { params, ...config },
  )
}

export function getInvestmentPositions(
  params: InvestmentPositionQuery,
  config?: AxiosRequestConfig,
) {
  return requestGet<InvestmentPosition[]>(
    financeRequest,
    '/api/finance/investments/positions',
    { params, ...config },
  )
}

export function getInvestmentPosition(id: number) {
  return requestGet<InvestmentPosition>(
    financeRequest,
    `/api/finance/investments/positions/${id}`,
  )
}

export function getInvestmentPositionDetail(
  id: number,
  config?: AxiosRequestConfig,
) {
  return requestGet<InvestmentAssetDetail>(
    financeRequest,
    `/api/finance/investments/positions/${id}/detail`,
    config,
  )
}

export function runInvestmentQuoteSyncTask(params?: {
  userId?: number
  accountId?: number
}) {
  return requestPost<InvestmentQuoteSyncSummary, undefined>(
    financeRequest,
    '/api/finance/investments/tasks/quote-sync',
    undefined,
    { params },
  )
}

export function createInvestmentPosition(params: SaveInvestmentPositionParams) {
  return requestPost<InvestmentPosition, SaveInvestmentPositionParams>(
    financeRequest,
    '/api/finance/investments/positions',
    params,
  )
}

export function updateInvestmentPosition(
  id: number,
  params: SaveInvestmentPositionParams,
) {
  return requestPut<InvestmentPosition, SaveInvestmentPositionParams>(
    financeRequest,
    `/api/finance/investments/positions/${id}`,
    params,
  )
}

export function deleteInvestmentPosition(id: number, userId: number) {
  return requestDelete<void>(
    financeRequest,
    `/api/finance/investments/positions/${id}`,
    {
      params: { userId },
    },
  )
}

export function getInvestmentTransactions(
  params: {
    userId: number
    accountId?: number
    positionId?: number
    settlementStatus?: string
  },
  config?: AxiosRequestConfig,
) {
  return requestGet<InvestmentTransaction[]>(
    financeRequest,
    '/api/finance/investments/transactions',
    { params, ...config },
  )
}

export function getInvestmentTransactionPage(
  params: {
    userId: number
    accountId?: number
    positionId?: number
    page?: number
    pageSize?: number
  },
  config?: AxiosRequestConfig,
) {
  return requestGet<InvestmentTransactionPage>(
    financeRequest,
    '/api/finance/investments/transactions/page',
    { params, ...config },
  )
}

export function createInvestmentTransaction(
  params: SaveInvestmentTransactionParams,
) {
  return requestPost<InvestmentTransaction, SaveInvestmentTransactionParams>(
    financeRequest,
    '/api/finance/investments/transactions',
    params,
  )
}

export function updateInvestmentTransaction(
  id: number,
  params: SaveInvestmentTransactionParams,
) {
  return requestPut<InvestmentTransaction, SaveInvestmentTransactionParams>(
    financeRequest,
    `/api/finance/investments/transactions/${id}`,
    params,
  )
}

export function deleteInvestmentTransaction(id: number, userId: number) {
  return requestDelete<void>(
    financeRequest,
    `/api/finance/investments/transactions/${id}`,
    { params: { userId } },
  )
}

export function getInvestmentAutoInvestPlans(
  params: {
    userId: number
    accountId?: number
    positionId?: number
    status?: string
  },
  config?: AxiosRequestConfig,
) {
  return requestGet<InvestmentAutoInvestPlan[]>(
    financeRequest,
    '/api/finance/investments/auto-invest-plans',
    { params, ...config },
  )
}

export function createInvestmentAutoInvestPlan(
  params: SaveInvestmentAutoInvestPlanParams,
) {
  return requestPost<
    InvestmentAutoInvestPlan,
    SaveInvestmentAutoInvestPlanParams
  >(financeRequest, '/api/finance/investments/auto-invest-plans', params)
}

export function updateInvestmentAutoInvestPlan(
  id: number,
  params: SaveInvestmentAutoInvestPlanParams,
) {
  return requestPut<
    InvestmentAutoInvestPlan,
    SaveInvestmentAutoInvestPlanParams
  >(financeRequest, `/api/finance/investments/auto-invest-plans/${id}`, params)
}

export function deleteInvestmentAutoInvestPlan(id: number, userId: number) {
  return requestDelete<void>(
    financeRequest,
    `/api/finance/investments/auto-invest-plans/${id}`,
    {
      params: { userId },
    },
  )
}

export function getInvestmentDividendIncome(userId: number) {
  return requestGet<InvestmentDividendIncomePage>(
    financeRequest,
    '/api/finance/investments/dividend-income',
    {
      params: { userId },
    },
  )
}

export function refreshInvestmentDividendIncome(userId: number) {
  return requestPost<InvestmentDividendIncomePage, undefined>(
    financeRequest,
    '/api/finance/investments/dividend-income/refresh',
    undefined,
    { params: { userId }, timeout: 120000 },
  )
}

export function getInvestmentDividendForecast(
  params: InvestmentDividendForecastRequest,
) {
  return requestPost<
    InvestmentDividendForecast,
    InvestmentDividendForecastRequest
  >(financeRequest, '/api/finance/investments/dividend-forecast', params)
}

export function getInvestmentProductQuote(params: {
  productType: InvestmentProductType
  symbol: string
  exchangeCode?: string | null
  name?: string | null
}) {
  return requestGet<InvestmentProductQuote>(
    financeRequest,
    '/api/finance/investments/products/quote',
    { params },
  )
}

export function getInvestmentFixedExpenses(userId: number) {
  return requestGet<InvestmentFixedExpense[]>(
    financeRequest,
    '/api/finance/investments/fixed-expenses',
    {
      params: { userId },
    },
  )
}

export function createInvestmentFixedExpense(
  params: SaveInvestmentFixedExpenseParams,
) {
  return requestPost<InvestmentFixedExpense, SaveInvestmentFixedExpenseParams>(
    financeRequest,
    '/api/finance/investments/fixed-expenses',
    params,
  )
}

export function updateInvestmentFixedExpense(
  id: number,
  params: SaveInvestmentFixedExpenseParams,
) {
  return requestPut<InvestmentFixedExpense, SaveInvestmentFixedExpenseParams>(
    financeRequest,
    `/api/finance/investments/fixed-expenses/${id}`,
    params,
  )
}

export function deleteInvestmentFixedExpense(id: number, userId: number) {
  return requestDelete<void>(
    financeRequest,
    `/api/finance/investments/fixed-expenses/${id}`,
    {
      params: { userId },
    },
  )
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
  return requestGet<GoldAccountSummary>(
    financeRequest,
    '/api/finance/gold-accounts/summary',
    {
      params: { userId },
    },
  )
}

export function getGoldAccountHoldings(userId: number) {
  return requestGet<GoldAccountHolding[]>(
    financeRequest,
    '/api/finance/gold-accounts/holdings',
    {
      params: { userId },
    },
  )
}

export function getGoldLiquidations(userId: number) {
  return requestGet<GoldLiquidation>(
    financeRequest,
    '/api/finance/gold-accounts/liquidations',
    {
      params: { userId },
    },
  )
}

export function getInvestmentFundRedeemFees(
  code: string,
  config?: AxiosRequestConfig,
) {
  return requestGet<InvestmentFundRedeemFeeOption[]>(
    financeRequest,
    '/api/finance/investments/fund-redeem-fees',
    {
      params: { code },
      timeout: 25000,
      ...config,
    },
  )
}
