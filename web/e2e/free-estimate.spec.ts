import { expect, test } from '@playwright/test'

test('财务自由测算展示紧凑参数、方案对比及可切换年限的趋势图', async ({ page }, testInfo) => {
  const user = { id: 1, username: 'estimate-test', displayName: '测算测试' }
  const estimate = { userId: 1, asOfDate: '2026-11-07', currentNetAssets: 120000,
    historicalMonthlyIncome: 20000, historicalMonthlyExpense: 5000, monthlyIncome: 20000, monthlyExpense: 5000,
    monthlySurplus: 15000, dataStartDate: '2025-12-01', dataEndDate: '2026-11-07', dataMonths: 12,
    incomeTransactionCount: 12, expenseTransactionCount: 120 }
  await page.addInitScript(user => {
    localStorage.setItem('bookkeeping_current_user', JSON.stringify(user))
    Object.defineProperty(navigator, 'connection', { configurable: true, value: { saveData: true } })
  }, user)
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data, message: '成功', timestamp: Date.now() } })
    if (path.endsWith('/auth/me')) return ok(user)
    if (path.endsWith('/auth/family')) return ok({ hasFamily: false, members: [] })
    if (path.endsWith('/reminders')) return ok({ items: [], unreadCount: 0, snoozedCount: 0 })
    if (path.endsWith('/accounts/free-estimate')) return ok(estimate)
    return ok([])
  })
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/finance/more-features')
  await page.getByRole('link', { name: '财务自由测算', exact: true }).click()
  await expect(page.getByRole('heading', { name: '财务自由测算', exact: true })).toBeVisible()
  const result = page.getByRole('region', { name: '方案结果', exact: true })
  await expect(result).toContainText('1,942,890.00')
  await expect(page.getByRole('button', { name: '基准方案', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('switch').click()
  await expect(result).toContainText('1,500,000.00')
  await page.getByLabel('每月生活费', { exact: true }).fill('4000')
  await expect(result).toContainText('1,200,000.00')
  await expect(result).toContainText('16,000.00')
  await page.getByLabel('基准年化回报', { exact: true }).fill('6')
  await expect(page.getByRole('button', { name: '保守方案', exact: true })).toContainText('4.0%')
  await expect(page.getByRole('button', { name: '乐观方案', exact: true })).toContainText('8.0%')
  await page.getByRole('button', { name: '乐观方案', exact: true }).click()
  await expect(result).toContainText('比基准方案提前')
  await expect(result).toContainText('年化 8.0%')
  const chart = page.getByRole('img', { name: /未来30年资产趋势/ })
  await expect(chart).toHaveAttribute('aria-busy', 'false')
  await expect(chart.locator('canvas')).toHaveCount(1)
  await expect(chart).toHaveAttribute('aria-label', /突出乐观方案/)
  await page.getByRole('button', { name: '10年', exact: true }).click()
  await expect(page.getByRole('img', { name: /未来10年资产趋势/ })).toBeVisible()
  await page.getByRole('button', { name: '恢复默认', exact: true }).click()
  await expect(page.getByLabel('每月生活费', { exact: true })).toHaveValue('5000.00')
  await expect(page.getByRole('switch')).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('button', { name: '基准方案', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(result).toContainText('1,942,890.00')
  await page.getByText('历史收支参考', { exact: true }).click()
  await expect(page.getByText('历史月均收入', { exact: true })).toBeVisible()
  for (const width of [375, 320, 1280]) {
    await page.setViewportSize({ width, height: 900 })
    await page.locator('.page-content').evaluate(element => { element.scrollTop = 0 })
    await page.getByRole('region', { name: '方案测算', exact: true }).screenshot({ path: testInfo.outputPath(`plan-${width}.png`), animations: 'disabled' })
    await page.getByRole('region', { name: '方案与趋势对比', exact: true }).screenshot({ path: testInfo.outputPath(`chart-${width}.png`), animations: 'disabled' })
    expect(await page.evaluate(() => {
      const page = document.querySelector('.free-estimate-page')!
      return page.scrollWidth <= page.clientWidth && document.documentElement.scrollWidth <= innerWidth
    })).toBe(true)
  }
  await page.evaluate(() => document.documentElement.dataset.theme = 'dark')
  await page.getByRole('button', { name: '20年', exact: true }).click()
  await page.getByRole('region', { name: '方案与趋势对比', exact: true }).screenshot({ path: testInfo.outputPath('chart-dark.png'), animations: 'disabled' })
  await page.getByRole('button', { name: '返回更多功能', exact: true }).click()
  await expect(page).toHaveURL(/\/finance\/more-features$/)
  expect(errors).toEqual([])
})
