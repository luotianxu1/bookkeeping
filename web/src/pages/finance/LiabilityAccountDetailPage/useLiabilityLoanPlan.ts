// 负债账户详情页的贷款计划派生指标：注入 account/records/summary 三个响应式源，返回各 computed。
import { computed, type Ref } from 'vue'
import type { Account, LiabilityAccountSummary, LiabilityRecord } from '@/api/modules/finance'
import { roundCurrency } from './liabilityFormat'
import { resolveInterestAmount } from './liabilityLoanPlan'

export function useLiabilityLoanPlan(options: {
  account: Ref<Account | null>
  records: Ref<LiabilityRecord[]>
  summary: Ref<LiabilityAccountSummary>
}) {
  const { account, records, summary } = options

  const hasLoanPlan = computed(() =>
    account.value?.loanTotalAmount != null
    && account.value?.loanTotalPeriods != null
    && account.value?.loanRepaymentDay != null
    && Boolean(account.value?.loanStartDate),
  )
  const isSettled = computed(() => Boolean(account.value?.loanSettledAt) || Number(summary.value.totalAmount ?? 0) <= 0)
  const paidRecords = computed(() => records.value.filter((record) => record.repaymentStatus === 'paid'))
  const interestAmount = computed(() => resolveInterestAmount(account.value))
  const totalRepaymentAmount = computed(() =>
    Number(account.value?.loanTotalAmount ?? 0) + interestAmount.value,
  )
  const paidAmount = computed(() => Math.max(totalRepaymentAmount.value - Number(summary.value.totalAmount ?? 0), 0))
  const monthlyPaymentAmount = computed(() => {
    const periods = Number(account.value?.loanTotalPeriods ?? 0)
    if (periods <= 0) {
      return 0
    }
    return roundCurrency(totalRepaymentAmount.value / periods)
  })
  const monthlyPaidPeriods = computed(() => {
    if (!hasLoanPlan.value) {
      return paidRecords.value.length
    }
    if (isSettled.value) {
      return Number(account.value?.loanTotalPeriods ?? 0)
    }
    const periods = new Set(
      paidRecords.value
        .filter((record) => record.repaymentType !== 'prepayment')
        .map((record) => Number(record.installmentCurrentPeriod ?? 0))
        .filter((period) => period > 0),
    )
    return periods.size
  })
  const remainingInstallmentPeriods = computed(() => {
    if (!hasLoanPlan.value) {
      return records.value.reduce((total, record) => {
        if (record.repaymentStatus === 'paid') {
          return total
        }
        if (!record.installmentTotalPeriods || !record.installmentCurrentPeriod) {
          return total
        }
        return total + Math.max(record.installmentTotalPeriods - record.installmentCurrentPeriod, 0)
      }, 0)
    }
    return Math.max(Number(account.value?.loanTotalPeriods ?? 0) - monthlyPaidPeriods.value, 0)
  })

  return {
    hasLoanPlan,
    isSettled,
    paidRecords,
    interestAmount,
    totalRepaymentAmount,
    paidAmount,
    monthlyPaymentAmount,
    monthlyPaidPeriods,
    remainingInstallmentPeriods,
  }
}
