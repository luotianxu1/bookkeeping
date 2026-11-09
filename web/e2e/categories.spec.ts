import { expect, test } from '@playwright/test'

test('分类管理展示系统固定支出及其二级分类，系统只读、自建分类可管理', async ({ page }, testInfo) => {
  const user = { id: 1, username: 'categories-test', displayName: '分类测试' }
  await page.addInitScript(user => {
    localStorage.setItem('bookkeeping_current_user', JSON.stringify(user))
    Object.defineProperty(navigator, 'connection', { configurable: true, value: { saveData: true } })
  }, user)
  const categories = [
    { id: 1, userId: null, name: '固定支出', type: 'expense', icon: 'daily', system: true, parentId: null, level: 1 },
    { id: 2, userId: 1, name: '房租', type: 'expense', icon: 'other', system: false, parentId: 1, level: 2 },
    { id: 3, userId: null, name: '餐饮', type: 'expense', icon: 'food', system: true, parentId: null, level: 1 },
    { id: 4, userId: null, name: '工资', type: 'income', icon: 'salary', system: true, parentId: null, level: 1 },
    { id: 5, userId: 1, name: '兼职', type: 'income', icon: 'other', system: false, parentId: null, level: 1 },
  ].map((category, index) => ({ ...category, status: 'active', sortOrder: index * 10, color: '#334155' }))
  await page.route('**/api/**', route => {
    const url = new URL(route.request().url())
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data, message: '成功', timestamp: Date.now() } })
    if (url.pathname.endsWith('/finance/categories')) {
      expect(url.searchParams.get('userId')).toBe('1')
      expect(url.searchParams.get('status')).toBe('active')
      return ok(categories)
    }
    return ok([])
  })
  await page.goto('/finance/categories')
  await expect(page.getByRole('button', { name: /固定支出 系统$/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '房租分类图标 房租', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /工资 系统$/ })).toBeVisible()
  await page.getByRole('button', { name: '管理分类', exact: true }).click()
  await expect(page.getByRole('button', { name: /固定支出 系统$/ })).toBeDisabled()
  await expect(page.getByRole('button', { name: '删除固定支出', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '删除房租', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '房租分类图标 房租', exact: true }).click()
  await expect(page.getByRole('heading', { name: '修改分类', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.screenshot({ path: testInfo.outputPath('system-categories.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
