import { expect, test, type Page, type Route } from '@playwright/test'

const user = { id: 1, username: 'test', displayName: '测试用户' }
const detailPath = '/finance/accounts/investment/detail?positionId=1'
const envelope = (data: unknown) => ({ code: 0, message: '成功', data, timestamp: Date.now() })
const ok = (route: Route, data: unknown) => route.fulfill({ json: envelope(data) })

function detail(id = 1, chartType = 'line') {
  return {
    name: `测试资产${id}`, symbol: '', productType: 'fund', chartType, latestPrice: 1,
    position: {
      id, userId: 1, accountId: 3, productId: 1, productType: 'fund', productName: `测试资产${id}`,
      productSymbol: '', holdingQuantity: 100, availableQuantity: 100, frozenQuantity: 0,
      costAmount: 100, currentPrice: 1, marketValue: 100, cumulativeProfit: 0,
      holdingProfit: 0, currencyCode: 'CNY', subscriptionStatus: 'confirmed', dividendType: 'cash',
    },
    marketStats: [], holdingStats: [], dividendRecords: [], fundRedeemFeeOptions: [],
    chartPoints: [
      { label: '2026-11-04', value: 1, open: 1, close: 1.1, low: 0.9, high: 1.2, volume: 100 },
      { label: '2026-11-05', value: 1.1, open: 1.1, close: 1.2, low: 1, high: 1.3, volume: 200 },
    ],
  }
}

async function mockApi(page: Page, loggedIn = true) {
  await page.addInitScript(({ user, loggedIn }) => {
    if (loggedIn) localStorage.setItem('bookkeeping_current_user', JSON.stringify(user))
    // 不让空闲预取掩盖图表按需加载断言。
    Object.defineProperty(navigator, 'connection', { configurable: true, value: { saveData: true } })
  }, { user, loggedIn })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/me')) return ok(route, user)
    if (path.endsWith('/auth/family')) return ok(route, { hasFamily: false, memberCount: 1, members: [] })
    if (path.endsWith('/auth/login')) return ok(route, { tokenType: 'Bearer', expiresIn: 3600 })
    const position = path.match(/positions\/(\d+)\/detail$/)
    if (position) return ok(route, detail(Number(position[1])))
    if (path.endsWith('/accounts')) return ok(route, [{ id: 1, userId: 1, name: '现金', accountTypeCode: 'cash', currentBalance: 1000, currencyCode: 'CNY', status: 'active' }])
    if (path.endsWith('/transactions/page')) return ok(route, { items: [], total: 0, page: 1, pageSize: 10 })
    if (path.endsWith('/overview')) return ok(route, { totalAssets: 1000, totalLiabilities: 0, netAssets: 1000, monthlyIncome: 0, monthlyExpense: 0, accountGroups: [] })
    return ok(route, [])
  })
}

test('登录后保存用户会话，不向本地存储写入 token', async ({ page }) => {
  await mockApi(page, false)
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('test')
  await page.getByPlaceholder('请输入密码').fill('password')
  await page.getByRole('button', { name: '登录并继续' }).click()
  await expect(page).toHaveURL(/\/finance$/)
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('bookkeeping_current_user')!).id)).toBe(1)
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => /token/i.test(key)))).toEqual([])
})

test('详情先显示；费率失败可单独重试；503 保留登录', async ({ page }) => {
  await mockApi(page)
  let requests = 0
  let release!: () => void
  const pending = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/finance/investments/fund-redeem-fees**', async route => {
    if (++requests === 1) {
      await pending
      return route.fulfill({ status: 503, json: { message: '费率暂不可用' } })
    }
    return ok(route, [])
  })
  await page.goto(detailPath)
  await expect(page.getByRole('region', { name: '投资详情总览' }).getByText('测试资产1', { exact: true })).toBeVisible()
  release()
  const feeError = page.locator('p').filter({ hasText: '费率暂不可用' })
  await expect(feeError).toBeVisible()
  await expect(page.getByRole('button', { name: '加仓', exact: true })).toBeEnabled()
  await feeError.getByRole('button', { name: '重试' }).click()
  await expect(feeError).toHaveCount(0)
  expect(requests).toBe(2)
  expect(await page.evaluate(() => localStorage.getItem('bookkeeping_current_user'))).not.toBeNull()
  await expect(page).toHaveURL(/positionId=1/)
})

test('服务已受理但响应断开后，表单重试复用同一请求 ID', async ({ page }) => {
  await mockApi(page)
  const ids: string[] = []
  const committed = new Set<string>()
  await page.route('**/api/finance/investments/transactions', async route => {
    if (route.request().method() !== 'POST') return ok(route, [])
    const payload = route.request().postDataJSON()
    ids.push(payload.requestId)
    committed.add(payload.requestId)
    if (ids.length === 1) return route.abort('failed')
    return ok(route, { ...payload, id: 7, settlementStatus: 'pending', settlementExpectedConfirmDate: '2026-11-09' })
  })
  await page.goto(detailPath)
  await page.getByRole('button', { name: '加仓', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('请输入申购金额').fill('100')
  await dialog.getByRole('button', { name: '加仓', exact: true }).click()
  await expect(dialog.locator('.investment-detail-modal-error')).toBeVisible()
  await dialog.getByRole('button', { name: '加仓', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(ids).toHaveLength(2)
  expect(ids[0]).toMatch(/^[a-f0-9-]{36}$/)
  expect(ids[1]).toBe(ids[0])
  expect(committed.size).toBe(1)
})

test('切换持仓时丢弃旧响应，401 清理会话', async ({ page }) => {
  await mockApi(page)
  let release!: () => void
  const pending = new Promise<void>(resolve => { release = resolve })
  let started!: () => void
  const requested = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/finance/investments/positions/1/detail', async route => {
    started(); await pending
    await ok(route, detail(1)).catch(() => undefined)
  })
  await page.goto(detailPath)
  await requested
  await page.evaluate(() => { history.pushState({}, '', '/finance/accounts/investment/detail?positionId=2'); dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page.getByRole('region', { name: '投资详情总览' }).getByText('测试资产2', { exact: true })).toBeVisible()
  release()
  await expect(page.getByRole('region', { name: '投资详情总览' }).getByText('测试资产1', { exact: true })).toHaveCount(0)
  await page.route('**/api/finance/investments/positions/3/detail', route => route.fulfill({ status: 401, json: { message: '登录已过期' } }))
  await page.evaluate(() => { history.pushState({}, '', '/finance/accounts/investment/detail?positionId=3'); dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page).toHaveURL(/\/login\?redirect=/)
  expect(await page.evaluate(() => localStorage.getItem('bookkeeping_current_user'))).toBeNull()
})

test('普通走势不下载 K 线，股票走势图可独立渲染', async ({ page }) => {
  await mockApi(page)
  const chunks: string[] = []
  const errors: string[] = []
  page.on('request', request => { if (/echarts.*\.js/.test(request.url())) chunks.push(request.url()) })
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(detailPath)
  await expect(page.locator('.investment-detail-chart canvas').first()).toBeVisible()
  expect(chunks.some(url => url.includes('echarts-kline'))).toBe(false)
  await page.route('**/api/finance/investments/positions/2/detail', route => ok(route, { ...detail(2, 'candlestick'), productType: 'stock' }))
  await page.evaluate(() => { history.pushState({}, '', '/finance/accounts/investment/detail?positionId=2'); dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page.getByRole('heading', { name: '股票日K走势' })).toBeVisible()
  await expect(page.locator('.investment-detail-chart canvas').first()).toBeVisible()
  expect(chunks.some(url => url.includes('echarts-kline'))).toBe(true)
  expect(errors).toEqual([])
})
