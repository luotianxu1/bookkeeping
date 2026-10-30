/** 行情中心路由助手：各标的类型 → 详情页路径（/finance/global-market/detail/:kind/:code）。 */

import type { DetailKind } from '@/api/modules/market'

/** 详情页路由：A股个股/指数详情接口只认 6 位代码；其余类型 code 为市场简称或完整新浪代码 */
export function detailRoute(kind: DetailKind, code: string): string {
  return `/finance/global-market/detail/${kind}/${encodeURIComponent(code)}`
}

/** 板块列表页路由 */
export function sectorsRoute(): string {
  return '/finance/global-market/sectors'
}
