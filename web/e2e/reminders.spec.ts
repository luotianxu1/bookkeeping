import { expect, test, type Page } from '@playwright/test'
import type { Reminder, ReminderSettings } from '../src/api/modules/reminder'

const user = { id: 1, username: 'reminder-test', displayName: '提醒测试' }
const initialSettings: ReminderSettings = { advanceDays: 3, todoEnabled: true, anniversaryEnabled: true, renewalEnabled: true, liabilityEnabled: true, investmentEnabled: true }
const initialItems: Reminder[] = [
  { key: 'todo:1:2026-11-07T18:00', type: 'todo', title: '带上相机去取景', description: '待办截止', dueAt: '2026-11-07T18:00:00+08:00', allDay: false, urgency: 'today', targetPath: '/tools/todo-items?reminderId=1', read: false, snoozedUntil: null },
  { key: 'renewal:2:2026-11-08T00:00', type: 'renewal', title: '视频会员续费', description: '本期固定支出 · CNY 25.00', dueAt: '2026-11-08T00:00:00+08:00', allDay: true, urgency: 'upcoming', targetPath: '/finance/renewals?reminderId=2', read: false, snoozedUntil: null },
  { key: 'anniversary:3:2026-11-09T00:00', type: 'anniversary', title: '我们的纪念日', description: '一年一度的重要日子', dueAt: '2026-11-09T00:00:00+08:00', allDay: true, urgency: 'upcoming', targetPath: '/tools/anniversaries?reminderId=3', read: false, snoozedUntil: null },
]

async function mockApi(page: Page) {
  let items = structuredClone(initialItems)
  let settings = { ...initialSettings }
  await page.addInitScript(user => {
    localStorage.setItem('bookkeeping_current_user', JSON.stringify(user))
    Object.defineProperty(navigator, 'connection', { configurable: true, value: { saveData: true } })
  }, user)
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data, message: '成功', timestamp: Date.now() } })
    if (path.endsWith('/auth/me')) return ok(user)
    if (path.endsWith('/auth/family')) return ok({ hasFamily: false, memberCount: 0, members: [] })
    if (path.endsWith('/reminders/settings')) {
      if (route.request().method() === 'PUT') settings = route.request().postDataJSON()
      return ok(settings)
    }
    if (path.endsWith('/reminders/actions')) {
      const body = route.request().postDataJSON()
      items = items.map(item => {
        if (!body.keys.includes(item.key)) return item
        if (body.action === 'read') return { ...item, read: true }
        if (body.action === 'unread') return { ...item, read: false }
        if (body.action === 'snooze') return { ...item, read: false, snoozedUntil: '2026-11-08T10:00:00+08:00' }
        return { ...item, read: false, snoozedUntil: null }
      })
      return ok(null)
    }
    if (path.endsWith('/reminders')) return ok({ items, unreadCount: items.filter(item => !item.read && !item.snoozedUntil).length, snoozedCount: items.filter(item => item.snoozedUntil).length })
    if (path.endsWith('/todo-items')) return ok([{ id: 1, title: '带上相机去取景', dueAt: '2026-11-07T18:00:00', status: 'pending', remark: '提前准备电池' }])
    return ok([])
  })
  return { settings: () => settings }
}

test('入口、筛选、已读持久化、稍后提醒、设置和原记录定位', async ({ page }, testInfo) => {
  const api = await mockApi(page)
  await page.goto('/profile')
  await expect(page.getByLabel('3 条未读提醒')).toBeVisible()
  await page.getByRole('button', { name: /统一提醒/ }).click()
  const cards = page.locator('.reminder-card')
  await expect(cards).toHaveCount(3)
  await page.screenshot({ path: testInfo.outputPath('reminders-light.png'), fullPage: true, animations: 'disabled' })
  await page.getByLabel('提醒类型').selectOption('renewal')
  await expect(cards).toHaveCount(1)
  await page.getByRole('button', { name: '全部已读', exact: true }).click()
  await expect(cards).toHaveCount(0)
  await page.reload()
  await expect(cards).toHaveCount(2)
  await cards.filter({ hasText: '带上相机去取景' }).getByRole('button', { name: '稍后提醒', exact: true }).click()
  await page.getByRole('button', { name: '1 小时后', exact: true }).click()
  await expect(cards).toHaveCount(1)
  await page.getByRole('button', { name: '稍后 1', exact: true }).click()
  await expect(cards).toHaveCount(1)
  await page.getByRole('button', { name: '恢复提醒', exact: true }).click()
  await expect(cards).toHaveCount(0)
  await page.getByRole('button', { name: '提醒设置', exact: true }).click()
  await page.getByLabel('提前提醒天数').fill('7')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(api.settings().advanceDays).toBe(7)
  await page.getByRole('button', { name: '未读 2', exact: true }).click()
  await page.evaluate(() => { document.documentElement.dataset.theme = 'dark' })
  await page.screenshot({ path: testInfo.outputPath('reminders-dark.png'), fullPage: true, animations: 'disabled' })
  await cards.filter({ hasText: '带上相机去取景' }).getByRole('button', { name: /查看详情/ }).click()
  await expect(page).toHaveURL(/\/tools\/todo-items\?reminderId=1$/)
  await expect(page.locator('#todo-1')).toBeVisible()
  await expect(page.locator('#todo-1').getByRole('button', { name: '编辑', exact: true })).toBeVisible()
})

test('加载失败可重试，空列表正常显示，窄屏不横向溢出', async ({ page }) => {
  await mockApi(page)
  let failed = true
  await page.route('**/api/tools/reminders', route => {
    if (failed) return route.fulfill({ status: 503, json: { message: '提醒暂不可用' } })
    return route.fulfill({ json: { code: 0, message: '成功', timestamp: Date.now(), data: { items: [], unreadCount: 0, snoozedCount: 0 } } })
  })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/profile/reminders')
  await expect(page.getByRole('alert')).toContainText('提醒暂不可用')
  failed = false
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByText('暂时没有未读提醒', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
