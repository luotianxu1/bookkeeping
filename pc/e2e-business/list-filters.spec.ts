import { expect, test, type Page, type Route } from '@playwright/test'
import { setupDesktop } from './desktop-fixtures'

const ok = (route: Route, data: unknown) => route.fulfill({
  json: { code: 0, message: '成功', timestamp: Date.now(), data },
})

function selectControl(page: Page, label: string) {
  return page.locator('.el-select').filter({
    has: page.getByRole('combobox', { name: label, exact: true }),
  })
}
async function selectOption(page: Page, label: string, option: string) {
  await selectControl(page, label).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: option, exact: true }).click()
}
async function clearSelect(page: Page, label: string) {
  const control = selectControl(page, label)
  await control.hover()
  await control.locator('.el-select__clear').click()
}
async function queryLedger(page: Page, button = '查询') {
  const pending = page.waitForResponse((response) =>
    new URL(response.url()).pathname.endsWith('/api/finance/transactions/page'),
  )
  await page.getByRole('button', { name: button, exact: true }).click()
  const response = await pending
  expect(response.ok()).toBe(true)
  return new URL(response.url()).searchParams
}

test('ledger query and reset refresh unchanged filters and return to the first page', async ({ page }) => {
  await setupDesktop(page)
  await page.route('**/api/finance/transactions/page?**', (route) => {
    const current = Number(new URL(route.request().url()).searchParams.get('page'))
    const day = new Date().toLocaleDateString('sv-SE')
    return ok(route, {
      items: [{
        id: current, userId: 7, accountId: 10, categoryId: 2,
        title: `第${current}页记录`, type: 'expense', amount: 10,
        sourceType: 'transaction', currencyCode: 'CNY', status: 'normal',
        occurredAt: day + 'T09:00:00',
      }],
      page: current, pageSize: 50, total: 51, totalPages: 2,
      incomeTotal: 0, expenseTotal: 510, balanceTotal: -510,
    })
  })
  await page.goto('/finance/transactions')
  await expect(page.getByRole('row').filter({ hasText: '第1页记录' })).toBeVisible()
  expect((await queryLedger(page)).get('page')).toBe('1')
  for (const action of ['查询', '重置']) {
    await page.locator('.desktop-pagination .btn-next').click()
    await expect(page.getByRole('row').filter({ hasText: '第2页记录' })).toBeVisible()
    expect((await queryLedger(page, action)).get('page')).toBe('1')
    await expect(page.getByRole('row').filter({ hasText: '第1页记录' })).toBeVisible()
  }
  await page.getByLabel('最低金额', { exact: true }).fill('20')
  await page.getByLabel('最高金额', { exact: true }).fill('10')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('最低金额不能大于最高金额')).toBeVisible()
  await page.getByLabel('最低金额', { exact: true }).fill('')
  await page.getByLabel('最高金额', { exact: true }).fill('')
  const params = await queryLedger(page)
  expect(params.has('minAmount')).toBe(false)
  expect(params.has('maxAmount')).toBe(false)
  await expect(page.getByText('最低金额不能大于最高金额')).toHaveCount(0)
})

test('ledger clears category drafts when switching the family scope', async ({ page }) => {
  await setupDesktop(page)
  await page.goto('/finance/transactions')
  await expect(page.getByRole('row').filter({ hasText: '工作午餐' })).toBeVisible()
  await selectOption(page, '分类', '支出 / 餐饮 / 午餐')
  expect((await queryLedger(page)).get('categoryId')).toBe('2')
  await selectOption(page, '账本范围', '家庭成员')
  await expect(selectControl(page, '分类')).toContainText('全部分类')
  await expect(selectControl(page, '分类').getByRole('combobox')).toBeEnabled()
  const params = await queryLedger(page)
  expect(params.get('userIds')).toBe('8')
  expect(params.has('categoryId')).toBe(false)
})

for (const kind of ['dishes', 'ingredients'] as const) {
  test(`${kind} category filters clear and follow menu navigation`, async ({ page }) => {
    await setupDesktop(page)
    const groups = [1, 2].map((id) => ({ id, userId: 7, name: `分类${id}`, status: 'active' }))
    const items = groups.map((group) => ({
      id: group.id, userId: 7, categoryId: group.id, categoryName: group.name,
      name: `条目${group.id}`, ingredientPreview: [], cookMinutes: 10,
      tasteTags: [], highlightTags: [], subtitle: '', note: '', status: 'active',
    }))
    await page.route('**/api/tools/food/categories?**', (route) => ok(route, groups))
    await page.route(`**/api/tools/food/${kind}**`, (route) =>
      ok(route, route.request().method() === 'GET' ? items : { ...items[0], ...route.request().postDataJSON() }),
    )
    const label = kind === 'dishes' ? '菜品分类' : '食材分类'
    const entry = kind === 'dishes' ? '菜品管理' : '食材管理'
    await page.goto(`/food/${kind}?categoryId=1`)
    await expect(page.getByRole('row').filter({ hasText: '条目1' })).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: '条目2' })).toHaveCount(0)
    await page.getByRole('menuitem', { name: entry, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/food/${kind}$`))
    await expect(page.getByRole('row').filter({ hasText: '条目2' })).toBeVisible()
    await selectOption(page, label, '分类1')
    await expect(page.getByRole('row').filter({ hasText: '条目2' })).toHaveCount(0)
    await clearSelect(page, label)
    await expect(page.getByRole('row').filter({ hasText: '条目2' })).toBeVisible()
    if (kind === 'ingredients') {
      await page.getByRole('row').filter({ hasText: '条目1' }).getByRole('button', { name: '编辑' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '保存修改' }).click()
      await expect(page.getByRole('dialog')).not.toBeVisible()
      await expect(page.getByRole('row').filter({ hasText: '条目2' })).toBeVisible()
    }
    await page.reload()
    await expect(page.getByRole('row').filter({ hasText: '条目2' })).toBeVisible()
  })
}

test('fund search can repeat the same keyword and return to ranking after clearing', async ({ page }) => {
  await setupDesktop(page)
  await page.goto('/finance/global-market/funds')
  await expect(page.getByText('测试优选混合A', { exact: true })).toBeVisible()
  for (let attempt = 0; attempt < 2; attempt++) {
    await page.getByRole('searchbox', { name: '搜索基金' }).fill('测试')
    const pending = page.waitForResponse((response) => response.url().includes('/funds/search?'))
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    expect((await pending).ok()).toBe(true)
    await expect(page.getByRole('row').filter({ hasText: '测试优选混合A' })).toBeVisible()
  }
  await page.getByRole('searchbox', { name: '搜索基金' }).fill('')
  const ranking = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/api/market/funds'))
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  expect((await ranking).ok()).toBe(true)
  await expect(page).not.toHaveURL(/[?&]q=/)
  await expect(page.getByRole('row').filter({ hasText: '测试优选混合A' })).toBeVisible()
})

test('shared table searches recover after clearing across finance, tools and food', async ({ page }) => {
  await setupDesktop(page)
  for (const [path, text] of [
    ['/tools/contacts', '王琳'],
    ['/tools/todo-items', '整理报销单'],
    ['/tools/health/records', '晨起空腹'],
    ['/finance/accounts', '日常账户'],
    ['/finance/categories', '午餐'],
    ['/finance/salary/records', '绩效已包含'],
    ['/food/categories', '家常菜'],
    ['/food/dishes', '番茄炒蛋'],
  ]) {
    await page.goto(path!)
    if (path === '/tools/todo-items')
      await page.getByRole('radiogroup', { name: '待办筛选' }).getByText('全部', { exact: true }).click()
    const row = page.getByRole('row').filter({ hasText: text })
    await expect(row).toBeVisible()
    const input = page.locator('.desktop-data-toolbar .el-input').first()
    await input.getByRole('textbox').fill('没有此条目的筛选词')
    await expect(row).toHaveCount(0)
    await input.hover()
    await input.locator('.el-input__clear').click()
    await expect(row).toBeVisible()
  }
})

for (const path of ['records', 'cash', 'tax', 'accounts/social-security']) {
  test(`salary ${path} ignores an earlier year response`, async ({ page }) => {
    await setupDesktop(page)
    const year = new Date().getFullYear()
    let release!: () => void
    let received!: () => void
    const hold = new Promise<void>((resolve) => { release = resolve })
    const ready = new Promise<void>((resolve) => { received = resolve })
    await page.route(`**/api/finance/salary/${path}?**`, async (route) => {
      const selected = Number(new URL(route.request().url()).searchParams.get('year'))
      if (selected === year - 1) {
        received()
        await hold
      }
      const row = { id: 1, monthLabel: `${selected}年数据`, monthKey: `${selected}-01`, grossSalary: 100, amountValue: 100 }
      return ok(route, {
        year: selected, metrics: [], deductions: [], details: [],
        records: [row], monthItems: [row], currentBalance: 100,
        forecast: { forecastYear: selected + 1 },
      })
    })
    try {
      await page.goto(`/finance/salary/${path}`)
      await expect(page.getByRole('row').filter({ hasText: `${year}年数据` })).toBeVisible()
      await selectOption(page, '年份', `${year - 1} 年`)
      await ready
      await selectOption(page, '年份', `${year - 2} 年`)
      await expect(page.getByRole('row').filter({ hasText: `${year - 2}年数据` })).toBeVisible()
      const old = page.waitForResponse((response) => response.url().includes(`year=${year - 1}`))
      release()
      await (await old).finished()
      await page.evaluate(() => new Promise(requestAnimationFrame))
      await expect(page.getByRole('row').filter({ hasText: `${year - 2}年数据` })).toBeVisible()
      await expect(page.getByRole('row').filter({ hasText: `${year - 1}年数据` })).toHaveCount(0)
    } finally {
      release()
    }
  })
}
