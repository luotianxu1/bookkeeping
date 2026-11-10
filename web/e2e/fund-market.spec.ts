import { expect, test, type Page } from '@playwright/test'

async function setup(page: Page) {
  const user = { id: 1, username: 'fund-test', displayName: '基金测试' }
  await page.addInitScript(user => {
    localStorage.setItem('bookkeeping_current_user', JSON.stringify(user))
    Object.defineProperty(navigator, 'connection', { configurable: true, value: { saveData: true } })
  }, user)
  let failure = false
  const requests: URL[] = []
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data, message: '成功', timestamp: Date.now() } })
    if (path.endsWith('/auth/me')) return ok(user)
    if (path.endsWith('/auth/family')) return ok({ hasFamily: false, members: [] })
    if (path.endsWith('/reminders')) return ok({ items: [], unreadCount: 0, snoozedCount: 0 })
    if (path.endsWith('/global/modules')) return ok({ count: 1, modules: [{ key: 'cn_index', title: 'A股指数', rows: [{ 代码: '000001', 名称: '上证指数', 最新价: 3200, 涨跌幅: 1.2, 涨跌额: 32, linkable: false }] }] })
    if (path.endsWith('/quotes')) return ok({ data: [] })
    if (path.endsWith('/funds/search')) {
      requests.push(url)
      const empty = url.searchParams.get('keyword') === '不存在'
      return ok({ total: empty ? 0 : 1, items: empty ? [] : [{ code: '001234', name: '测试优选混合A', type: '混合型-偏股' }] })
    }
    if (path.endsWith('/funds')) {
      requests.push(url)
      if (failure) return route.fulfill({ status: 503, json: { code: 503, data: null, message: '基金数据源暂不可用', timestamp: Date.now() } })
      const pageNo = Number(url.searchParams.get('page') ?? 1)
      return ok({ page: pageNo, pageSize: 20, total: 21, fetchedAt: '2026-11-09T13:00:00+08:00', source: '天天基金', items: [
        { code: pageNo === 1 ? '001234' : '001235', name: pageNo === 1 ? '测试优选混合A' : '第二页基金', navDate: '2026-11-06', nav: 1.2345, accumulatedNav: 2.3456, day: -0.12, week: 0, month: null, quarter: 2.5, halfYear: 5, year: 12.3, ytd: 8.5 },
      ] })
    }
    if (path.endsWith('/fund/base')) return ok({ matched: true, Datas: { SHORTNAME: '测试优选混合A', FCODE: '001234', DWJZ: '1.2345', LJJZ: '2.3456', FSRQ: '2026-11-06', FTYPE: '混合型', JJGS: '测试基金管理有限公司', SGZT: '开放申购', SHZT: '开放赎回' } })
    if (path.endsWith('/fund/trend')) return ok({ matched: true, items: [
      { date: '2026-11-06', nav: '1.2345', accNav: '2.3456' }, { date: '2026-11-05', nav: '1.23', accNav: '2.34' }, { date: '2026-10-10', nav: '1.20', accNav: '2.31' }, { date: '2026-08-01', nav: '1.12', accNav: '2.23' },
    ] })
    if (path.endsWith('/fund/period-returns')) return ok({ matched: true, rows: [{ title: 'Y', syl: '1.23' }, { title: '1N', syl: '12.30' }, { title: '5N', syl: null }] })
    return ok([])
  })
  return { requests, fail: (value: boolean) => { failure = value } }
}

test('股票基金切换、类型周期排序分页、搜索和基金详情完整可用', async ({ page }, testInfo) => {
  const { requests } = await setup(page)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/finance/global-market/global')
  const switcher = page.getByRole('group', { name: '行情类型' })
  await expect(switcher.getByRole('button', { name: '股票', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByText('上证指数', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'ETF', exact: true }).click()
  await switcher.getByRole('button', { name: '基金', exact: true }).click()
  await expect(page).toHaveURL(/\/funds$/)
  await expect(page.getByRole('navigation', { name: '市场切换' })).toHaveCount(0)
  await expect(page.locator('.fund-row')).toContainText('12.30%')
  await page.getByRole('button', { name: '债券型', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.searchParams.get('type')).toBe('zq')
  await page.getByRole('button', { name: /^涨幅周期 / }).click()
  await page.getByRole('option', { name: '近一月', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.searchParams.get('sort')).toBe('month')
  await expect(page.locator('.fund-row > strong')).toHaveText('—')
  await page.getByRole('button', { name: '当前降序，切换排序' }).click()
  await expect.poll(() => requests.at(-1)?.searchParams.get('order')).toBe('asc')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.fund-row')).toContainText('第二页基金')
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '上一页', exact: true }).click()
  await expect(page.locator('.fund-row')).toContainText('测试优选混合A')
  for (const width of [375, 320, 1280]) {
    await page.setViewportSize({ width, height: 812 })
    await page.screenshot({ path: testInfo.outputPath(`fund-list-${width}.png`), animations: 'disabled' })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth && document.querySelector('.market-page')!.scrollWidth <= document.querySelector('.market-page')!.clientWidth)).toBe(true)
  }
  await switcher.getByRole('button', { name: '股票', exact: true }).click()
  await expect(page).toHaveURL(/\/etf$/)
  await expect(page.getByRole('searchbox', { name: '搜索股票' })).toBeVisible()
  await switcher.getByRole('button', { name: '基金', exact: true }).click()
  await expect(page.getByRole('button', { name: '债券型', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('searchbox', { name: '搜索基金', exact: true }).fill('001234')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.locator('.fund-result')).toHaveCount(1)
  await expect(page.locator('.fund-note').first()).toContainText('共 1 只')
  await page.locator('.fund-result').click()
  await expect(page).toHaveURL(/\/funds\/001234$/)
  await expect(page.getByRole('region', { name: '基金概况' })).toContainText('1.2345')
  const chart = page.getByRole('img', { name: '单位净值走势图' })
  await expect(chart).toHaveAttribute('aria-busy', 'false')
  await expect(chart.locator('canvas')).toHaveCount(1)
  await page.getByRole('button', { name: '累计', exact: true }).click()
  await expect(page.getByRole('img', { name: '累计净值走势图' })).toBeVisible()
  await page.getByRole('button', { name: '近一月', exact: true }).click()
  await expect(page.getByRole('region', { name: '基金阶段收益' })).toContainText('+12.30%')
  await page.setViewportSize({ width: 375, height: 812 })
  await page.locator('.page-content').evaluate(el => { el.scrollTop = 0 })
  await page.screenshot({ path: testInfo.outputPath('fund-detail.png'), animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '‹ 返回基金列表', exact: true }).click()
  await expect(page.locator('.fund-result')).toHaveCount(1)
  await expect(page.getByRole('searchbox', { name: '搜索基金' })).toHaveValue('001234')
  await page.getByRole('searchbox', { name: '搜索基金' }).fill('不存在')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByText('未找到匹配的净值型基金，试试基金代码或其他关键词。')).toBeVisible()
  await page.getByRole('button', { name: '返回基金排行' }).click()
  await expect(page.locator('.fund-row')).toHaveCount(1)
  expect(errors).toEqual([])
})

test('基金数据失败可重试，快速切换筛选不会混入旧列表，详情直达有效', async ({ page }) => {
  const state = await setup(page)
  state.fail(true)
  await page.goto('/finance/global-market/funds')
  await expect(page.getByRole('alert')).toContainText('基金数据源暂不可用')
  await expect(page.getByText('当前条件下暂无基金')).toHaveCount(0)
  state.fail(false)
  await page.getByRole('button', { name: '重试行情', exact: true }).click()
  await expect(page.locator('.fund-row')).toHaveCount(1)
  await page.route('**/api/market/funds?**', async route => {
    if (new URL(route.request().url()).searchParams.get('type') !== 'gp') return route.fallback()
    await new Promise(resolve => setTimeout(resolve, 400))
    await route.fulfill({ json: { code: 0, message: '成功', timestamp: Date.now(), data: { total: 0, page: 1, pageSize: 20, items: [], source: '天天基金', fetchedAt: '2026-11-09T13:00:00+08:00' } } })
  })
  await page.getByRole('button', { name: '股票型', exact: true }).click()
  await page.getByRole('button', { name: '混合型', exact: true }).click()
  await expect(page.locator('.fund-row')).toHaveCount(1)
  await page.waitForTimeout(500)
  await expect(page.locator('.fund-row')).toHaveCount(1)
  await page.goto('/finance/global-market/funds/001234')
  await expect(page.getByRole('region', { name: '基金概况' })).toBeVisible()
  await page.getByRole('button', { name: '‹ 返回基金列表', exact: true }).click()
  await expect(page).toHaveURL(/\/funds(?:\?|$)/)
  await page.goto('/finance/global-market/funds/invalid')
  await expect(page.getByRole('alert')).toContainText('基金代码无效')
})
