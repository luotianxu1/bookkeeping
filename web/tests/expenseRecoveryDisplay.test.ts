import { describe, it, expect } from 'vitest'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import { detailAmountText, buildMetricAmount } from '@/pages/finance/AnalysisPage/analysisFormat'
import type { Transaction, TransactionAnalysisPeriodSummary } from '@/api/modules/finance'

describe('关联到账显示', () => {
  it('到账为正现金流、负支出，保留关联 ID 且不计收入', () => {
    const receipt = { id: 2, sourceType: 'transaction', originalTransactionId: 1, recoveryKind: 'refund', type: 'expense',
      amount: -100, title: '退款', occurredAt: '2026-02-01T12:00:00' } as Transaction
    const day = buildTransactionDayGroups([receipt])[0]!
    expect(day.expense).toBe('¥-100.00')
    expect(day.income).toBe('¥0.00')
    expect(day.surplus).toBe('¥100.00')
    expect(day.transactions[0]?.amount).toBe('+ ¥100.00')
    expect(day.transactions[0]?.originalTransactionId).toBe(1)
    expect(detailAmountText(receipt)).toMatch(/^\+/)
    expect(buildMetricAmount({ expense: -100 } as TransactionAnalysisPeriodSummary, 'month', '支出')).toBe('+100')
  })
})
