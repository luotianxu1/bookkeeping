import { expect, test } from '@playwright/test'

test('账户列表和总览共用请求，切换家庭成员取消旧请求并保持最新视角', async ({ page }) => {
  const user = { id: 1, username: 'self', displayName: '本人' }
  const requests: string[] = []
  let release!: () => void
  const delayed = new Promise<void>(resolve => { release = resolve })
  let oldMemberStarted!: () => void
  const started = new Promise<void>(resolve => { oldMemberStarted = resolve })
  await page.addInitScript(user => {
    localStorage.setItem('bookkeeping_current_user', JSON.stringify(user))
    Object.defineProperty(navigator, 'connection', { configurable: true, value: { saveData: true } })
  }, user)
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, message: '成功', data, timestamp: Date.now() } })
    requests.push(url.pathname)
    if (url.pathname.endsWith('/auth/family')) return ok({ hasFamily: true, memberCount: 3, members: [
      { userId: 1, displayName: '本人' }, { userId: 2, displayName: '成员甲' }, { userId: 3, displayName: '成员乙' },
    ] })
    if (url.pathname.endsWith('/account-types')) return ok([{ id: 1, name: '现金', code: 'cash', balanceDirection: 'debit', sortOrder: 0, status: 'active' }])
    if (url.pathname.endsWith('/accounts/summary')) {
      const id = Number(url.searchParams.get('userId'))
      if (id === 2) { oldMemberStarted(); await delayed }
      return ok({ accounts: [{ id, userId: id, name: `账户${id}`, accountTypeId: 1, accountTypeCode: 'cash', currentBalance: id * 100,
        includeInNetWorth: true, currencyCode: 'CNY', status: 'active' }], totalAssets: id * 100 })
    }
    return ok([])
  })
  try {
    await page.goto('/finance/accounts')
    await expect(page.getByText('账户1', { exact: true })).toBeVisible()
    const selector = page.getByRole('button', { name: /切换家庭成员账户视角/ })
    await selector.click()
    await page.getByRole('option', { name: '成员甲', exact: true }).click()
    await started
    await selector.click()
    await page.getByRole('option', { name: '成员乙', exact: true }).click()
    await expect(page.getByText('账户3 · 成员乙', { exact: true })).toBeVisible()
    release()
    await expect(page.getByText('账户2 · 成员甲', { exact: true })).toHaveCount(0)
    await expect(page.getByText('成员乙资产', { exact: true })).toBeVisible()
    expect(requests.filter(path => path.endsWith('/accounts/summary'))).toHaveLength(3)
    expect(requests.some(path => path.endsWith('/accounts') || path.endsWith('/accounts/overview'))).toBe(false)
  } finally { release() }
})
