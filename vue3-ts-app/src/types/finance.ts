// 财务页面类型：描述资产概览、日期分组和交易流水的数据结构。
export type TransactionType = 'income' | 'expense'

export type Transaction = {
  /** 流水 ID。 */
  id?: number
  /** 交易名称。 */
  name: string
  /** 交易发生时间。 */
  time: string
  /** 交易分类文案。 */
  category: string
  /** 交易方向，用于决定金额颜色和符号含义。 */
  type: TransactionType
  /** 已格式化的展示金额。 */
  amount: string
}

export type DayGroup = {
  /** 日期分组标题。 */
  date: string
  /** 当日收入汇总。 */
  income: string
  /** 当日支出汇总。 */
  expense: string
  /** 当日盈余汇总。 */
  surplus: string
  /** 当日交易列表。 */
  transactions: Transaction[]
}

export type FinanceOverview = {
  /** 资产总数。 */
  totalAssets: string
  /** 当月结余。 */
  monthlyBalance: string
  /** 当月收入汇总。 */
  monthlyIncome: string
  /** 当月支出汇总。 */
  monthlyExpense: string
  /** 月预算文案。 */
  budget: string
  /** 预算使用率展示文案。 */
  budgetUsageLabel: string
  /** 预算使用率数值，用于进度条宽度。 */
  budgetUsagePercent: number
}
