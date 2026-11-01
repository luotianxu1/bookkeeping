// 负债账户贷款计划的纯计算函数：利息、利率展示、下期账期与月供拆分。
import type { Account, LiabilityRecord } from '@/api/modules/finance'
import { roundCurrency } from './liabilityFormat'

export function resolveInterestAmount(currentAccount: Account | null) {
  if (!currentAccount) {
    return 0
  }
  const principal = Number(currentAccount.loanTotalAmount ?? 0)
  const rate = currentAccount.loanInterestRate
  if (rate != null && Number.isFinite(Number(rate))) {
    return roundCurrency((principal * Number(rate)) / 100)
  }
  return Number(currentAccount.loanInterestAmount ?? 0)
}

export function formatRate(currentAccount: Account | null) {
  if (!currentAccount) {
    return '--'
  }
  if (currentAccount.loanInterestRate != null && Number.isFinite(Number(currentAccount.loanInterestRate))) {
    return `${Number(currentAccount.loanInterestRate).toFixed(2)}%`
  }
  const principal = Number(currentAccount.loanTotalAmount ?? 0)
  const interestAmount = Number(currentAccount.loanInterestAmount ?? 0)
  if (principal > 0 && interestAmount > 0) {
    return `${((interestAmount / principal) * 100).toFixed(2)}%`
  }
  return '未设置'
}

export function resolveNextInstallmentPeriod(recordList: LiabilityRecord[], totalPeriods: number) {
  const maxPeriod = recordList.reduce((max, record) => Math.max(max, Number(record.installmentCurrentPeriod ?? 0)), 0)
  return Math.min(maxPeriod + 1, totalPeriods + 1)
}

export function buildScheduledDate(currentAccount: Account, period: number) {
  if (!currentAccount.loanStartDate) {
    return null
  }
  const baseDate = new Date(`${currentAccount.loanStartDate}T09:00:00`)
  if (Number.isNaN(baseDate.getTime())) {
    return null
  }
  const scheduleMonth = new Date(baseDate)
  scheduleMonth.setMonth(scheduleMonth.getMonth() + Math.max(period - 1, 0))
  const repaymentDay = Number(currentAccount.loanRepaymentDay ?? baseDate.getDate())
  const monthEnd = new Date(scheduleMonth.getFullYear(), scheduleMonth.getMonth() + 1, 0).getDate()
  scheduleMonth.setDate(Math.min(repaymentDay, monthEnd))
  return scheduleMonth
}

export function calculateInstallmentAmount(totalAmount: number, totalPeriods: number, currentPeriod: number) {
  const averageAmount = roundCurrency(totalAmount / totalPeriods)
  if (currentPeriod >= totalPeriods) {
    return roundCurrency(totalAmount - averageAmount * (totalPeriods - 1))
  }
  return averageAmount
}
