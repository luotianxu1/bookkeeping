import {
  test,
  expect,
  type APIRequestContext,
  type Page,
} from '@playwright/test'
const base = '/finance-api/api/finance'
async function login(page: Page) {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('live-user-1')
  await page.getByLabel('密码', { exact: true }).fill('live-test-password')
  await page.getByRole('button', { name: '登录工作台' }).click()
  await expect(page).toHaveURL(/\/overview$/)
}
async function balance(request: APIRequestContext) {
  const r = await request.get(base + '/accounts/1')
  expect(r.status()).toBe(200)
  return Number((await r.json()).data.currentBalance)
}
test('PC cookie login, expense posting, idempotent retry and foreign user isolation', async ({
  page,
  context,
}) => {
  await login(page)
  expect(
    (await context.cookies()).find((x) => x.name === 'access_token')?.httpOnly,
  ).toBe(true)
  const initial = await balance(page.request)
  await page.goto('/finance/entry/expense')
  await page.getByRole('textbox', { name: '金额', exact: true }).fill('12.50')
  await expect(
    page.getByRole('button', { name: '保存', exact: true }),
  ).toBeEnabled()
  const saved = page.waitForResponse(
    (r) =>
      r.url().endsWith('/api/finance/transactions') &&
      r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const response = await saved
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.code).toBe(0)
  const payload = response.request().postDataJSON()
  expect(payload.requestId).toBeTruthy()
  expect(await balance(page.request)).toBe(initial - 12.5)
  const replay = await page.request.post(base + '/transactions', {
    data: payload,
  })
  expect((await replay.json()).data.id).toBe(body.data.id)
  expect(await balance(page.request)).toBe(initial - 12.5)
  const denied = await page.request.get(base + '/accounts', {
    params: { userId: 3 },
  })
  expect(denied.status()).toBe(403)
  await page.goto('/finance/transactions')
  await expect(
    page.getByRole('row').filter({ hasText: '测试餐饮' }),
  ).toBeVisible()
})

test('PC savings reserves funds and opens records in a standalone page', async ({
  page,
}, testInfo) => {
  await login(page)
  const initial = await balance(page.request)
  await page.goto('/finance/savings-goals')
  await page.getByRole('button', { name: '新增储蓄目标', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('目标名称', { exact: true }).fill('桌面储蓄目标')
  await dialog.getByLabel('目标金额', { exact: false }).fill('500')
  await dialog.getByRole('combobox', { name: '关联现金账户' }).click()
  await page.getByRole('option', { name: /测试现金/ }).click()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  const row = page.getByRole('row').filter({ hasText: '桌面储蓄目标' })
  await row.getByRole('button', { name: '存入', exact: true }).click()
  await dialog.getByLabel('存入金额', { exact: false }).fill('100')
  await dialog.getByRole('button', { name: '确认记录', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  const goals = await (await page.request.get(base + '/savings-goals')).json()
  expect(
    Number(
      goals.data.find((x: { name: string }) => x.name === '桌面储蓄目标')
        .currentAmount,
    ),
  ).toBe(100)
  expect(await balance(page.request)).toBe(initial)
  const goal = goals.data.find((x: { name: string }) => x.name === '桌面储蓄目标')
  await row.getByRole('button', { name: '存取记录', exact: true }).click()
  await expect(page).toHaveURL(`/finance/savings-goals/${goal.id}/entries`)
  await expect(page.getByRole('heading', { name: '存取记录', exact: true })).toBeVisible()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: '手动分配' })).toContainText('+¥100.00')
  await page.reload()
  await expect(page.getByRole('row').filter({ hasText: '手动分配' })).toContainText('+¥100.00')
  await page.screenshot({ path: testInfo.outputPath('savings-history.png'), animations: 'disabled' })
  await page.getByRole('button', { name: '返回储蓄目标', exact: true }).click()
  await expect(page).toHaveURL('/finance/savings-goals')
  await expect(row).toBeVisible()
})
