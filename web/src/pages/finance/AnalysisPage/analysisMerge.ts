import type {
  Transaction,
  TransactionAnalysis,
  TransactionAnalysisCategoryBreakdownItem,
  TransactionAnalysisPeriod,
  TransactionAnalysisPeriodSummary,
  TransactionAnalysisTrendPoint,
} from '@/api/modules/finance'

export type AnalysisMergeContext = {
  period: TransactionAnalysisPeriod
  activeMonth: string
  activeYear: number
}

export type AnalysisViewContext = {
  isReadOnlyFamilyView: boolean
  selectedFamilyViewKind: string
  viewerNameByUserId: Map<number, string>
}

export function normalizeAnalysisForView(
  source: TransactionAnalysis,
  userId: number,
  ctx: AnalysisViewContext,
) {
  if (!ctx.isReadOnlyFamilyView || ctx.selectedFamilyViewKind !== 'total') {
    return source
  }

  const viewerName = ctx.viewerNameByUserId.get(userId)
  if (!viewerName) {
    return source
  }

  return {
    ...source,
    periodSummaries: source.periodSummaries.map((item) => ({
      ...item,
      transactions: item.transactions.map((transaction) => ({
        ...transaction,
        accountName: `${viewerName} · ${transaction.accountName ?? '现金账户'}`,
      })),
    })),
  }
}

export function mergeTransactionAnalysisResults(results: TransactionAnalysis[], ctx: AnalysisMergeContext) {
  const summary = {
    income: 0,
    expense: 0,
    surplus: 0,
    incomeCount: 0,
    expenseCount: 0,
    transactionCount: 0,
  }

  const incomeBreakdownMap = new Map<string, TransactionAnalysisCategoryBreakdownItem>()
  const expenseBreakdownMap = new Map<string, TransactionAnalysisCategoryBreakdownItem>()
  const trendMap = new Map<string, TransactionAnalysisTrendPoint>()
  const periodSummaryMap = new Map<string, TransactionAnalysisPeriodSummary>()

  results.forEach((result) => {
    summary.income += Number(result.summary.income ?? 0)
    summary.expense += Number(result.summary.expense ?? 0)
    summary.surplus += Number(result.summary.surplus ?? 0)
    summary.incomeCount += Number(result.summary.incomeCount ?? 0)
    summary.expenseCount += Number(result.summary.expenseCount ?? 0)
    summary.transactionCount += Number(result.summary.transactionCount ?? 0)

    mergeBreakdownItems(incomeBreakdownMap, result.incomeBreakdown)
    mergeBreakdownItems(expenseBreakdownMap, result.expenseBreakdown)

    result.trendPoints.forEach((item) => {
      const current = trendMap.get(item.key) ?? {
        key: item.key,
        label: item.label,
        income: 0,
        expense: 0,
        surplus: 0,
      }
      current.income += Number(item.income ?? 0)
      current.expense += Number(item.expense ?? 0)
      current.surplus += Number(item.surplus ?? 0)
      trendMap.set(item.key, current)
    })

    result.periodSummaries.forEach((item) => {
      const current = periodSummaryMap.get(item.key) ?? {
        key: item.key,
        label: item.label,
        income: 0,
        expense: 0,
        surplus: 0,
        transactionCount: 0,
        transactions: [],
      }
      current.income += Number(item.income ?? 0)
      current.expense += Number(item.expense ?? 0)
      current.surplus += Number(item.surplus ?? 0)
      current.transactionCount += Number(item.transactionCount ?? 0)
      current.transactions = [...current.transactions, ...item.transactions]
      periodSummaryMap.set(item.key, current)
    })
  })

  const incomeBreakdown = finalizeBreakdownItems(incomeBreakdownMap, summary.income)
  const expenseBreakdown = finalizeBreakdownItems(expenseBreakdownMap, summary.expense)
  const trendPoints = Array.from(trendMap.values()).sort((left, right) => left.key.localeCompare(right.key))
  const periodSummaries = Array.from(periodSummaryMap.values())
    .sort((left, right) => left.key.localeCompare(right.key))
    .map((item) => ({
      ...item,
      transactions: [...item.transactions].sort(compareTransactionsDesc),
    }))

  const first = results[0]
  return {
    userId: first?.userId ?? 0,
    period: ctx.period,
    month: ctx.period === 'month' ? (first?.month ?? ctx.activeMonth) : null,
    year: ctx.period === 'year' ? (first?.year ?? ctx.activeYear) : null,
    summary,
    incomeBreakdown,
    expenseBreakdown,
    trendPoints,
    periodSummaries,
  } satisfies TransactionAnalysis
}

export function mergeBreakdownItems(
  target: Map<string, TransactionAnalysisCategoryBreakdownItem>,
  source: TransactionAnalysisCategoryBreakdownItem[],
) {
  source.forEach((item) => {
    const key = `${item.categoryId ?? item.categoryName}`
    const current = target.get(key) ?? {
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      categoryIcon: item.categoryIcon,
      categoryColor: item.categoryColor,
      amount: 0,
      percent: 0,
      transactionCount: 0,
    }
    current.amount += Number(item.amount ?? 0)
    current.transactionCount += Number(item.transactionCount ?? 0)
    target.set(key, current)
  })
}

export function finalizeBreakdownItems(
  source: Map<string, TransactionAnalysisCategoryBreakdownItem>,
  totalAmount: number,
) {
  return Array.from(source.values())
    .map((item) => ({
      ...item,
      percent: totalAmount > 0 ? (Number(item.amount ?? 0) / totalAmount) * 100 : 0,
    }))
    .sort((left, right) => Number(right.amount ?? 0) - Number(left.amount ?? 0))
}

export function compareTransactionsDesc(left: Transaction, right: Transaction) {
  const leftTime = parseTime(left.occurredAt)
  const rightTime = parseTime(right.occurredAt)
  if (leftTime !== rightTime) {
    return rightTime - leftTime
  }
  return Number(right.id ?? 0) - Number(left.id ?? 0)
}

export function parseTime(value?: string | null) {
  if (!value) {
    return 0
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}
