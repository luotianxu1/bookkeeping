import { toolRequest, requestGet, requestPost, requestPut } from '@/api/request'

export type ReminderType = 'todo' | 'anniversary' | 'renewal' | 'liability' | 'investment' | 'budget' | 'reimbursement' | 'term_deposit'
export interface Reminder {
  key: string
  type: ReminderType
  title: string
  description: string
  dueAt: string
  allDay: boolean
  urgency: 'failed' | 'overdue' | 'today' | 'upcoming' | 'warning' | 'exceeded'
  targetPath: string
  read: boolean
  snoozedUntil: string | null
}
export interface ReminderOverview {
  items: Reminder[]
  unreadCount: number
  snoozedCount: number
}
export interface ReminderSettings {
  advanceDays: number
  todoEnabled: boolean
  anniversaryEnabled: boolean
  renewalEnabled: boolean
  liabilityEnabled: boolean
  investmentEnabled: boolean
  budgetEnabled: boolean
  reimbursementEnabled: boolean
  termDepositEnabled: boolean
}
export type ReminderAction = 'read' | 'unread' | 'snooze' | 'resume'
const path = '/api/tools/reminders'

export const getReminders = () => requestGet<ReminderOverview>(toolRequest, path)
export const getReminderSettings = () => requestGet<ReminderSettings>(toolRequest, `${path}/settings`)
export const saveReminderSettings = (settings: ReminderSettings) => requestPut<void>(toolRequest, `${path}/settings`, settings)
export const actOnReminders = (keys: string[], action: ReminderAction, snoozeMinutes?: number) =>
  requestPost<void>(toolRequest, `${path}/actions`, { keys, action, snoozeMinutes })
