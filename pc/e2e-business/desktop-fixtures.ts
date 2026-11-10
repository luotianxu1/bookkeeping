import type { Page } from '@playwright/test'
import { accounts, setup } from './fixtures'

export async function setupDesktop(page: Page) {
  const state = await setup(page)
  const day = new Date().toLocaleDateString('sv-SE')
  const month = day.slice(0, 7)
  const transactions = [
    {
      id: 1,
      title: '工作午餐',
      type: 'expense',
      amount: 32,
      categoryId: 2,
      categoryName: '午餐',
      transactionNo: 'TX-1',
    },
    {
      id: 2,
      title: '月度工资',
      type: 'income',
      amount: 18000,
      categoryId: 3,
      categoryName: '工资',
      transactionNo: 'TX-2',
    },
    {
      id: 3,
      title: '午餐退款',
      type: 'expense',
      amount: -12,
      categoryId: 2,
      categoryName: '午餐',
      transactionNo: 'TX-3',
      originalTransactionId: 1,
      recoveryKind: 'refund',
    },
    {
      id: 4,
      title: '定期本金',
      type: 'transfer',
      amount: 1000,
      transactionNo: 'TD-4',
    },
  ].map((item) => ({
    ...item,
    userId: 7,
    accountId: 10,
    accountName: '日常账户',
    sourceType: 'transaction',
    currencyCode: 'CNY',
    occurredAt: day + 'T09:00:00',
    createdAt: day,
    updatedAt: day,
    status: 'normal',
    remark: '桌面业务验证',
  }))
  const budget = {
    id: 1,
    userId: 7,
    budgetMonth: month + '-01',
    amount: 5000,
    usedAmount: 3200,
    remainingAmount: 1800,
    usagePercent: 64,
    currencyCode: 'CNY',
    status: 'active',
    createdAt: day,
    updatedAt: day,
  }
  const records = [
    {
      id: 1,
      monthKey: month,
      monthLabel: month + '月',
      grossSalary: 20000,
      note: '绩效已包含',
      editable: true,
    },
  ]
  await page.route('**/*-api/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      path = url.pathname.replace(/^\/[a-z]+-api/, '')
    const ok = (data: unknown) =>
      route.fulfill({
        json: { code: 0, message: '成功', data, timestamp: Date.now() },
      })
    if (req.method() !== 'GET') return route.fallback()
    if (path === '/api/finance/transactions') return ok(transactions)
    if (path === '/api/finance/transactions/page') {
      let rows = transactions
      const keyword = url.searchParams.get('keyword')
      if (keyword) rows = rows.filter((row) => row.title.includes(keyword))
      if (url.searchParams.get('categoryId'))
        rows = rows.filter(
          (row) =>
            String(row.categoryId) === url.searchParams.get('categoryId'),
        )
      return ok({
        items: rows,
        total: rows.length,
        page: 1,
        totalPages: 1,
        pageSize: 50,
        incomeTotal: 18000,
        expenseTotal: 20,
        balanceTotal: 17980,
      })
    }
    if (path === '/api/finance/accounts/10') return ok(accounts[0])
    if (path === '/api/finance/accounts/summary')
      return ok({
        userId: 7,
        totalAssets: 20000,
        totalLiabilities: 0,
        netAssets: 20000,
        accountCount: 1,
        accounts,
      })
    if (path === '/api/finance/monthly-budgets/current') return ok(budget)
    if (path === '/api/finance/monthly-budgets')
      return ok([budget, { ...budget, id: 2, budgetMonth: '2026-09-01' }])
    if (path === '/api/finance/category-budgets')
      return ok([
        {
          ...budget,
          categoryId: 2,
          categoryName: '午餐',
          amount: 1000,
          usedAmount: 820,
          remainingAmount: 180,
          usagePercent: 82,
        },
      ])
    if (path === '/api/finance/transactions/analysis')
      return ok({
        userId: 7,
        period: 'month',
        month,
        summary: {
          income: 18000,
          expense: 20,
          surplus: 17980,
          incomeCount: 1,
          expenseCount: 2,
          transactionCount: 3,
        },
        incomeBreakdown: [
          {
            categoryId: 3,
            categoryName: '工资',
            amount: 18000,
            percent: 100,
            transactionCount: 1,
          },
        ],
        expenseBreakdown: [
          {
            categoryId: 2,
            categoryName: '午餐',
            amount: 20,
            percent: 100,
            transactionCount: 2,
          },
        ],
        trendPoints: [
          { key: day, label: day, income: 18000, expense: 20, surplus: 17980 },
        ],
        periodSummaries: [
          {
            key: day,
            label: day,
            income: 18000,
            expense: 20,
            surplus: 17980,
            transactionCount: 3,
            transactions,
          },
        ],
      })
    if (path === '/api/finance/salary/records')
      return ok({
        year: 2026,
        defaultMonthlyGrossSalary: 20000,
        recordedMonths: 1,
        recordedGrossIncome: 20000,
        estimatedAnnualGrossIncome: 240000,
        metrics: [{ label: '已记录税前收入', value: 20000 }],
        records,
        updatedAt: day,
      })
    if (path === '/api/finance/salary/overview')
      return ok({
        monthKey: month,
        payDay: 10,
        paidMonths: 10,
        grossIncome: 20000,
        netIncome: 16800,
        totalDeduction: 3200,
        taxAmount: 800,
        annualIncome: 200000,
        netRate: 84,
        metrics: [{ label: '五险一金', value: 2400 }],
        details: [
          { label: '养老保险', value: 1600, detail: '个人缴纳 8%' },
          { label: '公积金', value: 800, detail: '个人缴纳 4%' },
        ],
        linkedAccounts: [
          {
            accountType: 'housing_fund',
            title: '公积金',
            currentBalance: 55000,
            monthlyDeposit: 1600,
            routePath: '/finance/salary/accounts/housing_fund',
          },
        ],
        taxSummary: {
          currentMonthTax: 800,
          annualTax: 8000,
          annualIncome: 200000,
          routePath: '/finance/salary/tax',
        },
      })
    if (path === '/api/auth/recovery-code')
      return ok({ configured: true, createdAt: day })
    if (path === '/api/auth/sessions')
      return ok([
        {
          id: 'session-1',
          deviceName: 'Chrome · macOS',
          ipAddress: '127.0.0.1',
          createdAt: day + 'T08:00:00',
          lastSeenAt: day + 'T10:00:00',
          expiresAt: day,
          current: true,
        },
        {
          id: 'session-2',
          deviceName: 'Safari · iPhone',
          ipAddress: '192.0.2.10',
          createdAt: day + 'T08:00:00',
          lastSeenAt: day + 'T09:00:00',
          expiresAt: day,
          current: false,
        },
      ])
    if (path === '/api/auth/family')
      return ok({
        hasFamily: true,
        inviteCode: 'FAMILY-TEST',
        memberCount: 2,
        members: [
          {
            userId: 7,
            displayName: '桌面测试',
            username: 'desktop-test',
            role: 'owner',
            currentUser: true,
            canUnbind: false,
          },
          {
            userId: 8,
            displayName: '家庭成员',
            username: 'family-member',
            role: 'member',
            currentUser: false,
            canUnbind: true,
          },
        ],
      })
    if (path === '/api/tools/food/dishes/1')
      return ok({
        id: 1,
        name: '番茄炒蛋',
        subtitle: '家常小炒',
        cookMinutes: 15,
        description: '酸甜可口',
        highlightTags: ['家常菜'],
        ingredients: [
          { ingredientName: '番茄', amount: '2 个' },
          { ingredientName: '鸡蛋', amount: '3 个' },
        ],
        steps: [
          { stepNo: 1, content: '切番茄并打散鸡蛋' },
          { stepNo: 2, content: '炒熟鸡蛋后加入番茄' },
        ],
      })
    if (path === '/api/market/index/spot')
      return ok({
        count: 1,
        data: [{ 代码: '000001', 名称: '上证指数', 最新价: 3200, 涨跌幅: 1.2 }],
      })
    if (path === '/api/market/activity')
      return ok({
        up: 2100,
        down: 1800,
        flat: 120,
        sample: 4020,
        dist: [
          { side: 'down', label: '5%', count: 400 },
          { side: 'down', label: '3%', count: 1400 },
          { side: 'flat', label: '0%', count: 120 },
          { side: 'up', label: '3%', count: 1500 },
          { side: 'up', label: '5%', count: 600 },
        ],
      })
    if (path === '/api/market/turnover')
      return ok({ today: 1200000000000, prev: 1100000000000, currency: 'CNY' })
    if (path === '/api/market/limits')
      return ok({
        limit_up: 1,
        limit_down: 1,
        up: [
          {
            code: '600001',
            name: '测试工业',
            price: 12.3,
            pct: 10,
            amount: 123000000,
          },
        ],
        down: [
          {
            code: '600002',
            name: '测试科技',
            price: 9.1,
            pct: -10,
            amount: 100000000,
          },
        ],
      })
    if (path === '/api/market/sector/spot')
      return ok({
        count: 1,
        data: [
          {
            代码: 'sector1',
            名称: '人工智能',
            涨跌幅: 2.1,
            总成交额: 300000000,
          },
        ],
      })
    if (path === '/api/market/stock/spot')
      return ok({
        count: 1,
        data: [
          {
            代码: '600001',
            名称: '测试工业',
            最新价: 12.3,
            涨跌幅: 10,
            成交量: 10000,
            成交额: 123000000,
          },
        ],
      })
    return route.fallback()
  })
  return state
}
