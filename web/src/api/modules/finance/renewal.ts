// 订阅续费接口（由 finance.ts 拆出；类型定义见 ./types）
import { financeRequest, requestDelete, requestGet, requestPost, requestPut } from '@/api/request'
import type {
  RenewalSubscription,
  RenewalSubscriptionQuery,
  RenewalSubscriptionSummary,
  SaveRenewalSubscriptionParams,
} from './types'

export function getRenewalSubscriptions(params: RenewalSubscriptionQuery) {
  return requestGet<RenewalSubscription[]>(financeRequest, '/api/finance/renewal-subscriptions', { params })
}

export function getRenewalSubscriptionSummary(userId: number) {
  return requestGet<RenewalSubscriptionSummary>(financeRequest, '/api/finance/renewal-subscriptions/summary', {
    params: { userId },
  })
}

export function createRenewalSubscription(params: SaveRenewalSubscriptionParams) {
  return requestPost<RenewalSubscription, SaveRenewalSubscriptionParams>(
    financeRequest,
    '/api/finance/renewal-subscriptions',
    params,
  )
}

export function updateRenewalSubscription(id: number, params: SaveRenewalSubscriptionParams) {
  return requestPut<RenewalSubscription, SaveRenewalSubscriptionParams>(
    financeRequest,
    `/api/finance/renewal-subscriptions/${id}`,
    params,
  )
}

export function pauseRenewalSubscription(id: number, userId: number) {
  return requestPost<RenewalSubscription, undefined>(
    financeRequest,
    `/api/finance/renewal-subscriptions/${id}/pause?userId=${userId}`,
    undefined,
  )
}

export function resumeRenewalSubscription(id: number, userId: number) {
  return requestPost<RenewalSubscription, undefined>(
    financeRequest,
    `/api/finance/renewal-subscriptions/${id}/resume?userId=${userId}`,
    undefined,
  )
}

export function deleteRenewalSubscription(id: number, userId: number) {
  return requestDelete<void>(financeRequest, `/api/finance/renewal-subscriptions/${id}`, {
    params: { userId },
  })
}
