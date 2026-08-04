import { requestDelete, requestGet, requestPost, requestPut, toolRequest } from '@/api/request'

export type TodoStatus = 'pending' | 'completed'
export type TodoDueScope = 'all' | 'today'

export interface TodoItem {
  id: number
  userId: number
  title: string
  dueAt: string
  remark?: string | null
  sortOrder: number
  status: TodoStatus | string
  completedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface TodoItemQuery {
  userId?: number
  status?: TodoStatus | 'all'
  dueScope?: TodoDueScope
  keyword?: string
}

export interface SaveTodoItemParams {
  userId: number
  title: string
  dueAt: string
  remark?: string | null
  sortOrder?: number
  status?: TodoStatus | string
}

export interface UpdateTodoItemStatusParams {
  userId: number
  status: TodoStatus
}

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

export type AnniversaryScope = 'all' | 'month' | 'expired'

export interface Anniversary {
  id: number
  userId: number
  title: string
  anniversaryDate: string
  remark?: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface AnniversaryQuery {
  userId?: number
  scope?: AnniversaryScope
  keyword?: string
}

export interface SaveAnniversaryParams {
  userId: number
  title: string
  anniversaryDate: string
  remark?: string | null
  sortOrder?: number
}

export type CalendarOverviewView = 'month' | 'year'

export interface CalendarDay {
  date: string
  day: number
  currentMonth: boolean
  weekend: boolean
  today: boolean
  selected: boolean
  anniversaryCount: number
  holidayLabel?: string | null
  workdayLabel?: string | null
}

export interface CalendarMonth {
  key: string
  label: string
  daysInMonth: number
  current: boolean
  selected: boolean
  days: CalendarDay[]
}

export interface CalendarAnniversaryNote {
  id: number
  title: string
  occurrenceDate: string
  remark?: string | null
  statusLabel: string
  daysOffset: number
}

export interface CalendarOverview {
  view: CalendarOverviewView | string
  anchor: string
  selectedDate: string
  title: string
  subtitle: string
  days: CalendarDay[]
  months: CalendarMonth[]
  anniversaries: CalendarAnniversaryNote[]
}

export interface CalendarOverviewQuery {
  userId: number
  view?: CalendarOverviewView
  anchor?: string
  selectedDate?: string | null
}

export type PhotographyOrderStatus = 'pending' | 'shot' | 'cancelled'
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

export type TravelPlanStatus = 'active' | 'completed' | 'cancelled'
export type TravelItineraryType = 'transport' | 'scenic' | 'dining' | 'accommodation'
export type TravelExpenseType = TravelItineraryType | 'other'

export interface TravelPlan {
  id: number
  userId: number
  name: string
  destination?: string | null
  startDate?: string | null
  endDate?: string | null
  remark?: string | null
  status: TravelPlanStatus | string
  sortOrder: number
  companionCount: number
  travelerCount: number
  dayCount: number
  expenseCount: number
  totalExpenseAmount: number
  perPersonExpenseAmount: number
  createdAt: string
  updatedAt: string
}

export interface TravelPlanCompanion {
  id: number
  travelPlanId: number
  contactId: number
  contactName?: string | null
  contactPhone?: string | null
  contactRemark?: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface TravelPlanItinerary {
  id: number
  travelPlanDayId: number
  type: TravelItineraryType | string
  title: string
  poiName?: string | null
  poiId?: string | null
  address?: string | null
  longitude?: number | null
  latitude?: number | null
  startTime?: string | null
  transportMode?: 'driving' | 'walking' | 'riding' | null
  distanceMeters?: number | null
  durationSeconds?: number | null
  remark?: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface TravelPlanExpense {
  id: number
  travelPlanId: number
  travelPlanDayId: number
  type: TravelExpenseType | string
  title: string
  amount: number
  payerContactId?: number | null
  payerContactName?: string | null
  remark?: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface TravelPlanDay {
  id: number
  travelPlanId: number
  dayIndex: number
  title?: string | null
  travelDate?: string | null
  sortOrder: number
  itineraries: TravelPlanItinerary[]
  expenses: TravelPlanExpense[]
  createdAt: string
  updatedAt: string
}

export interface TravelPlanOverview {
  companionCount: number
  travelerCount: number
  dayCount: number
  itineraryCount: number
  expenseCount: number
  totalExpenseAmount: number
  perPersonExpenseAmount: number
}

export interface TravelPlanDetail {
  id: number
  userId: number
  name: string
  destination?: string | null
  startDate?: string | null
  endDate?: string | null
  remark?: string | null
  status: TravelPlanStatus | string
  sortOrder: number
  overview: TravelPlanOverview
  companions: TravelPlanCompanion[]
  days: TravelPlanDay[]
  expenses: TravelPlanExpense[]
  createdAt: string
  updatedAt: string
}

export interface TravelPlanQuery {
  userId?: number
  status?: TravelPlanStatus | 'all'
  keyword?: string
}

export interface SaveTravelPlanParams {
  userId: number
  name: string
  destination?: string | null
  startDate?: string | null
  endDate?: string | null
  remark?: string | null
  status?: TravelPlanStatus | string
  sortOrder?: number
}

export interface SaveTravelPlanCompanionParams {
  userId: number
  contactId: number
  sortOrder?: number
}

export interface SaveTravelPlanDayParams {
  userId: number
  dayIndex: number
  title?: string | null
  travelDate?: string | null
  sortOrder?: number
}

export interface SaveTravelPlanItineraryParams {
  userId: number
  type: TravelItineraryType
  title: string
  poiName?: string | null
  poiId?: string | null
  address?: string | null
  longitude?: number | null
  latitude?: number | null
  startTime?: string | null
  transportMode?: 'driving' | 'walking' | 'riding' | null
  distanceMeters?: number | null
  durationSeconds?: number | null
  remark?: string | null
  sortOrder?: number
}

export interface SaveTravelPlanExpenseParams {
  userId: number
  type: TravelExpenseType
  title: string
  amount: number
  payerContactId?: number | null
  remark?: string | null
  sortOrder?: number
}

export function getTodoItems(params: TodoItemQuery) {
  return requestGet<TodoItem[]>(toolRequest, '/api/tools/todo-items', { params })
}

export function getTodoItem(id: number) {
  return requestGet<TodoItem>(toolRequest, `/api/tools/todo-items/${id}`)
}

export function createTodoItem(params: SaveTodoItemParams) {
  return requestPost<TodoItem, SaveTodoItemParams>(toolRequest, '/api/tools/todo-items', params)
}

export function updateTodoItem(id: number, params: SaveTodoItemParams) {
  return requestPut<TodoItem, SaveTodoItemParams>(toolRequest, `/api/tools/todo-items/${id}`, params)
}

export function updateTodoItemStatus(id: number, params: UpdateTodoItemStatusParams) {
  return requestPut<TodoItem, UpdateTodoItemStatusParams>(toolRequest, `/api/tools/todo-items/${id}/status`, params)
}

export function deleteTodoItem(id: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/todo-items/${id}`, {
    params: { userId },
  })
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

export function getAnniversaries(params: AnniversaryQuery) {
  return requestGet<Anniversary[]>(toolRequest, '/api/tools/anniversaries', { params })
}

export function getAnniversary(id: number) {
  return requestGet<Anniversary>(toolRequest, `/api/tools/anniversaries/${id}`)
}

export function createAnniversary(params: SaveAnniversaryParams) {
  return requestPost<Anniversary, SaveAnniversaryParams>(toolRequest, '/api/tools/anniversaries', params)
}

export function updateAnniversary(id: number, params: SaveAnniversaryParams) {
  return requestPut<Anniversary, SaveAnniversaryParams>(toolRequest, `/api/tools/anniversaries/${id}`, params)
}

export function deleteAnniversary(id: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/anniversaries/${id}`, {
    params: { userId },
  })
}

export function getCalendarOverview(params: CalendarOverviewQuery) {
  return requestGet<CalendarOverview>(toolRequest, '/api/tools/calendar', { params })
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

export function updatePhotographyOrder(id: number, params: SavePhotographyOrderParams) {
  return requestPut<PhotographyOrder, SavePhotographyOrderParams>(toolRequest, `/api/tools/photography-orders/${id}`, params)
}

export function collectPhotographyOrderFinal(id: number, params: CollectPhotographyOrderFinalParams) {
  return requestPost<PhotographyOrder, CollectPhotographyOrderFinalParams>(
    toolRequest,
    `/api/tools/photography-orders/${id}/collect-final`,
    params,
  )
}

export function cancelPhotographyOrder(id: number, userId: number) {
  return requestPost<PhotographyOrder>(toolRequest, `/api/tools/photography-orders/${id}/cancel`, undefined, {
    params: { userId },
  })
}

export function deletePhotographyOrder(id: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/photography-orders/${id}`, {
    params: { userId },
  })
}

export function getTravelPlans(params: TravelPlanQuery) {
  return requestGet<TravelPlan[]>(toolRequest, '/api/tools/travel-plans', { params })
}

export function getTravelPlan(id: number) {
  return requestGet<TravelPlanDetail>(toolRequest, `/api/tools/travel-plans/${id}`)
}

export function getTravelPlanOverview(id: number) {
  return requestGet<TravelPlanOverview>(toolRequest, `/api/tools/travel-plans/${id}/overview`)
}

export function createTravelPlan(params: SaveTravelPlanParams) {
  return requestPost<TravelPlan, SaveTravelPlanParams>(toolRequest, '/api/tools/travel-plans', params)
}

export function updateTravelPlan(id: number, params: SaveTravelPlanParams) {
  return requestPut<TravelPlan, SaveTravelPlanParams>(toolRequest, `/api/tools/travel-plans/${id}`, params)
}

export function deleteTravelPlan(id: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/travel-plans/${id}`, {
    params: { userId },
  })
}

export function createTravelPlanCompanion(id: number, params: SaveTravelPlanCompanionParams) {
  return requestPost<TravelPlanCompanion, SaveTravelPlanCompanionParams>(
    toolRequest,
    `/api/tools/travel-plans/${id}/companions`,
    params,
  )
}

export function updateTravelPlanCompanion(companionId: number, params: SaveTravelPlanCompanionParams) {
  return requestPut<TravelPlanCompanion, SaveTravelPlanCompanionParams>(
    toolRequest,
    `/api/tools/travel-plans/companions/${companionId}`,
    params,
  )
}

export function deleteTravelPlanCompanion(companionId: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/travel-plans/companions/${companionId}`, {
    params: { userId },
  })
}

export function createTravelPlanDay(id: number, params: SaveTravelPlanDayParams) {
  return requestPost<TravelPlanDay, SaveTravelPlanDayParams>(toolRequest, `/api/tools/travel-plans/${id}/days`, params)
}

export function updateTravelPlanDay(dayId: number, params: SaveTravelPlanDayParams) {
  return requestPut<TravelPlanDay, SaveTravelPlanDayParams>(toolRequest, `/api/tools/travel-plans/days/${dayId}`, params)
}

export function deleteTravelPlanDay(dayId: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/travel-plans/days/${dayId}`, {
    params: { userId },
  })
}

export function createTravelPlanItinerary(dayId: number, params: SaveTravelPlanItineraryParams) {
  return requestPost<TravelPlanItinerary, SaveTravelPlanItineraryParams>(
    toolRequest,
    `/api/tools/travel-plans/days/${dayId}/itineraries`,
    params,
  )
}

export function updateTravelPlanItinerary(itineraryId: number, params: SaveTravelPlanItineraryParams) {
  return requestPut<TravelPlanItinerary, SaveTravelPlanItineraryParams>(
    toolRequest,
    `/api/tools/travel-plans/itineraries/${itineraryId}`,
    params,
  )
}

export function deleteTravelPlanItinerary(itineraryId: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/travel-plans/itineraries/${itineraryId}`, {
    params: { userId },
  })
}

export function createTravelPlanExpense(dayId: number, params: SaveTravelPlanExpenseParams) {
  return requestPost<TravelPlanExpense, SaveTravelPlanExpenseParams>(
    toolRequest,
    `/api/tools/travel-plans/days/${dayId}/expenses`,
    params,
  )
}

export function updateTravelPlanExpense(expenseId: number, params: SaveTravelPlanExpenseParams) {
  return requestPut<TravelPlanExpense, SaveTravelPlanExpenseParams>(
    toolRequest,
    `/api/tools/travel-plans/expenses/${expenseId}`,
    params,
  )
}

export function deleteTravelPlanExpense(expenseId: number, userId: number) {
  return requestDelete<void>(toolRequest, `/api/tools/travel-plans/expenses/${expenseId}`, {
    params: { userId },
  })
}
