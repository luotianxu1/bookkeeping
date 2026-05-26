import { requestDelete, requestGet, requestPost, requestPut, toolRequest } from '@/api/request'

export type ContactStatus = 'active' | 'archived'

export interface Contact {
  id: number
  userId: number
  name: string
  phone?: string | null
  remark?: string | null
  sortOrder: number
  status: ContactStatus | string
  createdAt: string
  updatedAt: string
}

export interface ContactQuery {
  userId?: number
  status?: ContactStatus | 'all'
  keyword?: string
}

export interface SaveContactParams {
  userId: number
  name: string
  phone?: string | null
  remark?: string | null
  sortOrder?: number
  status?: ContactStatus | string
}

export type PhotographyOrderStatus = 'pending' | 'shot'
export type PhotographyOrderType =
  | 'first_birthday'
  | 'hundred_days'
  | 'engagement'
  | 'thanks_banquet'
  | 'wedding'
  | 'graduation'

export interface PhotographyOrder {
  id: number
  orderNo: string
  userId: number
  customerName: string
  contactInfo?: string | null
  orderType: PhotographyOrderType | string
  status: PhotographyOrderStatus | string
  shootAt: string
  totalAmount: number
  depositAmount: number
  finalAmount: number
  depositAccountId?: number | null
  depositAccountName?: string | null
  depositTransactionId?: number | null
  depositReceivedAt?: string | null
  finalAccountId?: number | null
  finalAccountName?: string | null
  finalTransactionId?: number | null
  finalReceivedAt?: string | null
  address?: string | null
  remark?: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface PhotographyOrderQuery {
  userId?: number
  status?: PhotographyOrderStatus | 'all'
  keyword?: string
}

export interface SavePhotographyOrderParams {
  userId: number
  customerName: string
  contactInfo?: string | null
  orderType: PhotographyOrderType
  shootAt: string
  totalAmount: number
  depositAmount: number
  finalAmount: number
  depositAccountId?: number | null
  address?: string | null
  remark?: string | null
  sortOrder?: number
}

export interface CollectPhotographyOrderFinalParams {
  userId: number
  finalAccountId?: number | null
  occurredAt?: string | null
}

export type PhotographyOrderOverviewView = 'calendar' | 'month' | 'year'

export interface PhotographyOrderOverviewSummary {
  totalOrders: number
  shotOrders: number
  pendingOrders: number
  totalContractAmount: number
  totalReceivedAmount: number
  totalDepositAmount: number
  totalFinalAmount: number
  depositIncome: number
  finalIncome: number
  pendingFinalAmount: number
  averageContractAmount: number
}

export interface PhotographyOrderOverviewTrendPoint {
  key: string
  label: string
  orderCount: number
  shotCount: number
  pendingCount: number
  totalIncome: number
  contractAmount: number
}

export interface PhotographyOrderOverviewTypeStat {
  type: PhotographyOrderType | string
  label: string
  orderCount: number
  totalIncome: number
  contractAmount: number
}

export interface PhotographyOrderOverviewBucket {
  key: string
  label: string
  subLabel?: string | null
  orderCount: number
  shotCount: number
  pendingCount: number
  totalIncome: number
  contractAmount: number
  selected: boolean
  currentScope: boolean
}

export interface PhotographyOrderOverview {
  view: PhotographyOrderOverviewView | string
  anchor: string
  selectedValue?: string | null
  title: string
  subtitle: string
  summary: PhotographyOrderOverviewSummary
  trendPoints: PhotographyOrderOverviewTrendPoint[]
  typeStats: PhotographyOrderOverviewTypeStat[]
  buckets: PhotographyOrderOverviewBucket[]
  orders: PhotographyOrder[]
}

export interface PhotographyOrderOverviewQuery {
  userId: number
  view: PhotographyOrderOverviewView
  anchor?: string
  selectedDate?: string | null
}

export function getContacts(params: ContactQuery) {
  return requestGet<Contact[]>(toolRequest, '/api/tools/contacts', { params })
}

export function getContact(id: number) {
  return requestGet<Contact>(toolRequest, `/api/tools/contacts/${id}`)
}

export function createContact(params: SaveContactParams) {
  return requestPost<Contact, SaveContactParams>(toolRequest, '/api/tools/contacts', params)
}

export function updateContact(id: number, params: SaveContactParams) {
  return requestPut<Contact, SaveContactParams>(toolRequest, `/api/tools/contacts/${id}`, params)
}

export function deleteContact(id: number) {
  return requestDelete<void>(toolRequest, `/api/tools/contacts/${id}`)
}

export function getPhotographyOrders(params: PhotographyOrderQuery) {
  return requestGet<PhotographyOrder[]>(toolRequest, '/api/tools/photography-orders', { params })
}

export function getPhotographyOrderOverview(params: PhotographyOrderOverviewQuery) {
  return requestGet<PhotographyOrderOverview>(toolRequest, '/api/tools/photography-orders/overview', { params })
}

export function getPhotographyOrder(id: number) {
  return requestGet<PhotographyOrder>(toolRequest, `/api/tools/photography-orders/${id}`)
}

export function createPhotographyOrder(params: SavePhotographyOrderParams) {
  return requestPost<PhotographyOrder, SavePhotographyOrderParams>(toolRequest, '/api/tools/photography-orders', params)
}

export function collectPhotographyOrderFinal(id: number, params: CollectPhotographyOrderFinalParams) {
  return requestPost<PhotographyOrder, CollectPhotographyOrderFinalParams>(
    toolRequest,
    `/api/tools/photography-orders/${id}/collect-final`,
    params,
  )
}

export function deletePhotographyOrder(id: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/photography-orders/${id}`, {
    params: { userId },
  })
}
