// 财务首页静态演示数据：来自当前 Pencil「财务」页面设计稿。
import type { DayGroup, FinanceOverview } from '@/types/finance'

export const financeOverview: FinanceOverview = {
  totalAssets: '168,420.00',
  monthlyBalance: '当月结余 8,420',
  monthlyIncome: '收入 13,200',
  monthlyExpense: '支出 4,780',
  budget: '月预算 15,000',
  budgetUsageLabel: '已用 31.9%',
  budgetUsagePercent: 31.9,
}

export const financeDayGroups: DayGroup[] = [
  {
    date: '今天',
    income: '3,200.00',
    expense: '48.00',
    surplus: '3,152.00',
    transactions: [
      {
        name: '午餐',
        time: '12:20',
        category: '支出',
        type: 'expense',
        amount: '- 48.00',
      },
      {
        name: '工资入账',
        time: '09:00',
        category: '收入',
        type: 'income',
        amount: '+ 3,200.00',
      },
    ],
  },
  {
    date: '昨天',
    income: '86.00',
    expense: '126.50',
    surplus: '-40.50',
    transactions: [
      {
        name: '超市购物',
        time: '18:42',
        category: '支出',
        type: 'expense',
        amount: '- 126.50',
      },
      {
        name: '退款到账',
        time: '16:12',
        category: '收入',
        type: 'income',
        amount: '+ 86.00',
      },
    ],
  },
  {
    date: '03-21',
    income: '1,500.00',
    expense: '22.00',
    surplus: '1,478.00',
    transactions: [
      {
        name: '咖啡',
        time: '08:35',
        category: '支出',
        type: 'expense',
        amount: '- 22.00',
      },
      {
        name: '项目奖金',
        time: '20:10',
        category: '收入',
        type: 'income',
        amount: '+ 1,500.00',
      },
    ],
  },
]
