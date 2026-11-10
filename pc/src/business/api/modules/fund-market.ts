import { marketRequest, requestGet } from '@/business/api/request'

export type FundType = 'all' | 'gp' | 'hh' | 'zq' | 'zs' | 'qdii' | 'fof'
export type FundSort =
  | 'day'
  | 'week'
  | 'month'
  | 'quarter'
  | 'halfYear'
  | 'year'
  | 'ytd'
export type FundOrder = 'desc' | 'asc'
export interface FundRankingRow {
  code: string
  name: string
  navDate: string
  nav: number | null
  accumulatedNav: number | null
  day: number | null
  week: number | null
  month: number | null
  quarter: number | null
  halfYear: number | null
  year: number | null
  ytd: number | null
}
export interface FundRanking {
  items: FundRankingRow[]
  total: number
  page: number
  pageSize: number
  fetchedAt: string
  source: string
}
export interface FundSearchItem {
  code: string
  name: string
  type: string
}
export interface FundSearchResult {
  items: FundSearchItem[]
  total: number
}
export const getFundRanking = (
  params: {
    type: FundType
    sort: FundSort
    order: FundOrder
    page: number
    pageSize: number
  },
  signal?: AbortSignal,
) =>
  requestGet<FundRanking>(marketRequest, '/api/market/funds', {
    params,
    signal,
    timeout: 20_000,
  })
export const searchMarketFunds = (keyword: string, signal?: AbortSignal) =>
  requestGet<FundSearchResult>(marketRequest, '/api/market/funds/search', {
    params: { keyword },
    signal,
    timeout: 20_000,
  })
