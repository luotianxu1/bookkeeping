// A股选股与定时任务接口（由 finance.ts 拆出；类型定义见 ./types）
import { financeRequest, requestGet, requestPost } from '@/business/api/request'
import type {
  StockScreenRun,
  ScheduledTask,
  StockScreenPage,
  StockScreenQuery,
  StockScreenRunSubmission,
  StockScreenStopSubmission,
} from './types'

export function getStockScreenStatus() {
  return requestGet<StockScreenRun | null>(
    financeRequest,
    '/api/finance/stock-screener/status',
  )
}

export function getTodayScheduledTasks() {
  return requestGet<ScheduledTask[]>(
    financeRequest,
    '/api/finance/scheduled-tasks/today',
  )
}

export function executeTodayScheduledTask(
  taskName: string,
  triggerName: string,
) {
  return requestPost<ScheduledTask>(
    financeRequest,
    `/api/finance/scheduled-tasks/today/${encodeURIComponent(taskName)}/${encodeURIComponent(triggerName)}/execute`,
  )
}

export function getStockScreenResults(params: StockScreenQuery = {}) {
  return requestGet<StockScreenPage>(
    financeRequest,
    '/api/finance/stock-screener/results',
    { params },
  )
}

export function triggerStockScreenRun(force = false) {
  return requestPost<StockScreenRunSubmission, Record<string, never>>(
    financeRequest,
    '/api/finance/stock-screener/runs',
    {},
    { params: { force } },
  )
}

export function stopStockScreenRun() {
  return requestPost<StockScreenStopSubmission, Record<string, never>>(
    financeRequest,
    '/api/finance/stock-screener/runs/stop',
    {},
  )
}
