import type { Page } from '@playwright/test'
export const user = {
  id: 7,
  username: 'desktop-test',
  displayName: '桌面测试',
  roleCode: 'super_admin',
  roleName: '超级管理员',
  status: 'active',
  phone: '13800138000',
  email: 'test@example.test',
  createdAt: '2026-11-01T10:00:00',
  updatedAt: '2026-11-01T10:00:00',
}
export const accountTypes = [
  { id: 1, code: 'cash', name: '现金', status: 'active' },
  { id: 2, code: 'term_deposit', name: '定期存款', status: 'active' },
  { id: 3, code: 'investment', name: '投资', status: 'active' },
]
export const accounts = [
  {
    id: 10,
    userId: 7,
    accountTypeId: 1,
    accountTypeCode: 'cash',
    name: '日常账户',
    currencyCode: 'CNY',
    currentBalance: 20000,
    includeInNetWorth: true,
    status: 'active',
    createdAt: '2026-11-01T10:00:00',
    updatedAt: '2026-11-01T10:00:00',
  },
]
export const categories = [
  {
    id: 1,
    userId: null,
    type: 'expense',
    name: '餐饮',
    parentId: null,
    icon: 'food',
    color: '#6155d9',
    system: true,
    status: 'active',
  },
  {
    id: 2,
    userId: 7,
    type: 'expense',
    name: '午餐',
    parentId: 1,
    icon: 'food',
    color: '#6155d9',
    system: false,
    status: 'active',
  },
  {
    id: 3,
    userId: null,
    type: 'income',
    name: '工资',
    parentId: null,
    system: true,
    status: 'active',
  },
]
export async function setup(page: Page, onlyErrors = false) {
  const calls: { method: string; path: string; data: any }[] = []
  const weights = [
    {
      id: 1,
      userId: 7,
      recordDate: '2026-11-09',
      weight: 68.5,
      bodyFatRate: 20.1,
      remark: '晨起空腹',
    },
  ]
  const contacts: any[] = [
    {
      id: 1,
      userId: 7,
      name: '王琳',
      phone: '13911112222',
      remark: '实际备注',
      sortOrder: 0,
      status: 'active',
    },
  ]
  const todos: any[] = [
    {
      id: 1,
      userId: 7,
      title: '整理报销单',
      dueAt: '2026-11-15T10:00:00',
      remark: '出差',
      status: 'pending',
      sortOrder: 0,
    },
  ]
  const foodCategories = [
    {
      id: 1,
      userId: 7,
      name: '家常菜',
      categoryType: 'dish',
      iconText: 'food',
      iconTone: 'blue',
      itemCount: 1,
      description: '日常',
      status: 'active',
    },
  ]
  const dishes = [
    {
      id: 1,
      userId: 7,
      categoryId: 1,
      categoryName: '家常菜',
      name: '番茄炒蛋',
      subtitle: '家常小炒',
      tasteTags: ['清淡'],
      highlightTags: [],
      cookMinutes: 15,
      ingredientPreview: ['番茄', '鸡蛋'],
      ingredients: [],
      steps: [],
      status: 'published',
    },
  ]
  await page.route('**/*-api/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.replace(/^\/[a-z]+-api/, '')
    const data = request.postDataJSON()
    calls.push({ method: request.method(), path, data })
    const ok = (data: unknown) =>
      route.fulfill({
        json: { code: 0, message: '成功', data, timestamp: Date.now() },
      })
    if (path === '/api/admin/session')
      return ok({
        user,
        permissions: [
          'pc.access',
          'user.read',
          'user.create',
          'user.update',
          'user.status',
          'user.role',
          'role.read',
          'role.create',
          'role.update',
          'role.delete',
          'permission.read',
          'permission.assign',
        ],
      })
    if (onlyErrors)
      return route.fulfill({
        status: 503,
        json: { code: 503, message: '测试服务暂不可用', data: null },
      })
    if (path === '/api/auth/me') return ok(user)
    if (path === '/api/auth/family')
      return ok({ hasFamily: false, members: [], memberCount: 0 })
    if (path === '/api/tools/weight-records') {
      if (request.method() === 'POST') {
        const saved = { ...data, id: 2, bodyFatRate: 20 }
        const index = weights.findIndex((x) => x.recordDate === data.recordDate)
        if (index < 0) weights.unshift(saved)
        else weights[index] = saved
        return ok(saved)
      }
      return ok(weights)
    }
    if (path === '/api/tools/contacts') {
      if (request.method() === 'POST') {
        contacts.push({ ...data, id: contacts.length + 1 })
        return ok(contacts.at(-1))
      }
      return ok(contacts)
    }
    if (/^\/api\/tools\/contacts\/\d+$/.test(path)) {
      const id = Number(path.split('/').at(-1))
      if (request.method() === 'PUT')
        Object.assign(
          contacts.find((x) => x.id === id),
          data,
        )
      else
        contacts.splice(
          contacts.findIndex((x) => x.id === id),
          1,
        )
      return ok(null)
    }
    if (path === '/api/tools/todo-items') {
      if (request.method() === 'POST') {
        todos.push({ ...data, id: todos.length + 1 })
        return ok(todos.at(-1))
      }
      return ok(todos)
    }
    if (path.startsWith('/api/tools/todo-items/')) {
      const id = Number(path.split('/')[4])
      Object.assign(
        todos.find((x) => x.id === id),
        data,
      )
      return ok(todos.find((x) => x.id === id))
    }
    if (path === '/api/tools/calendar') {
      const anchor = url.searchParams.get('anchor') || '2026-11'
      const year = Number(anchor.slice(0, 4)),
        month = Number(anchor.slice(5, 7) || 11)
      const makeDays = (m: number) =>
        Array.from({ length: 35 }, (_, i) => {
          const d = new Date(Date.UTC(year, m - 1, i))
          return {
            date: d.toISOString().slice(0, 10),
            day: d.getUTCDate(),
            currentMonth: d.getUTCMonth() === m - 1,
            weekend: i % 7 > 4,
            today: false,
            selected: i === 10,
            anniversaryCount: i === 10 ? 1 : 0,
          }
        })
      return ok({
        view: url.searchParams.get('view') || 'month',
        anchor,
        selectedDate: `${year}-11-10`,
        title: `${year}年${month}月`,
        subtitle: '',
        days: makeDays(month),
        months: Array.from({ length: 12 }, (_, i) => ({
          key: `${year}-${String(i + 1).padStart(2, '0')}`,
          label: `${i + 1}月`,
          daysInMonth: 30,
          current: false,
          selected: i === 10,
          days: makeDays(i + 1),
        })),
        anniversaries: [
          {
            id: 1,
            title: '结婚纪念日',
            occurrenceDate: `${year}-11-10`,
            remark: '',
            statusLabel: '今天',
            daysOffset: 0,
          },
        ],
      })
    }
    if (path.endsWith('/accounts/free-estimate'))
      return ok({
        userId: 7,
        asOfDate: '2026-11-10',
        currentNetAssets: 120000,
        historicalMonthlyIncome: 20000,
        historicalMonthlyExpense: 5000,
        monthlyIncome: 20000,
        monthlyExpense: 5000,
        monthlySurplus: 15000,
        dataStartDate: '2025-12-01',
        dataEndDate: '2026-11-10',
        dataMonths: 12,
        incomeTransactionCount: 12,
        expenseTransactionCount: 120,
      })
    if (path === '/api/finance/account-types') return ok(accountTypes)
    if (path === '/api/finance/accounts') return ok(accounts)
    if (path === '/api/finance/accounts/summary')
      return ok({ accounts, totalAssets: 20000 })
    if (path === '/api/finance/categories')
      return ok(
        categories.filter(
          (x) =>
            !url.searchParams.get('type') ||
            x.type === url.searchParams.get('type'),
        ),
      )
    if (path === '/api/finance/savings-goals') return ok([])
    if (path === '/api/finance/term-deposits') return ok([])
    if (path === '/api/finance/transactions' && request.method() === 'POST')
      return ok({ ...data, id: 99 })
    if (path === '/api/tools/food/categories') return ok(foodCategories)
    if (path === '/api/tools/food/dishes') return ok(dishes)
    if (path === '/api/tools/food/orders' && request.method() === 'POST')
      return ok({ ...data, id: 1 })
    if (path === '/api/tools/food/orders') return ok([])
    if (path === '/api/tools/food/orders/1')
      return ok({
        id: 1,
        userId: 7,
        title: '新菜单',
        plannedFor: '2026-11-10',
        totalCookMinutes: 15,
        dishCount: 1,
        dishNames: ['番茄炒蛋'],
        dishes: [{ dishId: 1, dishName: '番茄炒蛋', categoryName: '家常菜' }],
      })
    if (path === '/api/tools/food/ingredients') return ok([])
    if (path.endsWith('/global/modules'))
      return ok({
        count: 1,
        modules: [
          {
            key: 'cn_index',
            title: 'A股指数',
            rows: [
              {
                代码: '000001',
                名称: '上证指数',
                最新价: 3200,
                涨跌幅: 1.2,
                涨跌额: 32,
                linkable: false,
              },
            ],
          },
        ],
      })
    if (path.endsWith('/funds'))
      return ok({
        page: 1,
        pageSize: 20,
        total: 1,
        fetchedAt: '2026-11-10T13:00:00+08:00',
        items: [
          {
            code: '001234',
            name: '测试优选混合A',
            navDate: '2026-11-09',
            nav: 1.2345,
            accumulatedNav: 2.34,
            day: 0.2,
            week: 1,
            month: 3,
            quarter: 5,
            halfYear: 8,
            year: 12.3,
            ytd: 10,
          },
        ],
      })
    if (path.endsWith('/fund/base'))
      return ok({
        matched: true,
        Datas: {
          SHORTNAME: '测试优选混合A',
          FCODE: '001234',
          DWJZ: '1.2345',
          LJJZ: '2.3456',
          FSRQ: '2026-11-09',
          FTYPE: '混合型',
          JJGS: '测试基金公司',
          SGZT: '开放申购',
          SHZT: '开放赎回',
        },
      })
    if (path.endsWith('/fund/trend'))
      return ok({
        matched: true,
        items: [
          { date: '2026-11-09', nav: '1.2345', accNav: '2.34' },
          { date: '2026-11-06', nav: '1.23', accNav: '2.33' },
          { date: '2026-10-10', nav: '1.20', accNav: '2.31' },
        ],
      })
    if (path.endsWith('/fund/period-returns'))
      return ok({ matched: true, rows: [{ title: '1N', syl: '12.30' }] })
    if (path.endsWith('/funds/search'))
      return ok({
        total: 1,
        items: [{ code: '001234', name: '测试优选混合A', type: '混合型' }],
      })
    return route.fulfill({
      status: 503,
      json: { code: 503, message: '测试服务暂不可用', data: null },
    })
  })
  return { calls, contacts, todos }
}
