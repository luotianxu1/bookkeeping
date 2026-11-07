import { randomUUID } from 'node:crypto'
import { expect, test, type Page, type APIRequestContext } from '@playwright/test'

const auth = '/auth-api/api/auth'
const finance = '/finance-api/api/finance'

async function login(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('live-user-1')
  await page.getByPlaceholder('请输入密码').fill('live-test-password')
  const response = page.waitForResponse(r => r.url().endsWith('/api/auth/login'))
  await page.getByRole('button', { name: '登录并继续' }).click()
  expect((await response).status()).toBe(200)
  await expect(page).toHaveURL(/\/finance$/)
}

async function balance(request: APIRequestContext, id = 1) {
  const response = await request.get(`${finance}/accounts/${id}`)
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.code).toBe(0)
  return Number(body.data.currentBalance)
}

test('真实登录 Cookie → 页面记账 → HTTP 重放只扣款一次', async ({ page, context }) => {
  await login(page)
  const cookie = (await context.cookies()).find(cookie => cookie.name === 'access_token')!
  expect(cookie).toMatchObject({ httpOnly: true, sameSite: 'Lax', path: '/' })
  expect(await page.evaluate(() => document.cookie)).not.toContain('access_token')
  expect(await page.evaluate(() => Object.keys(localStorage).filter(key => /token/i.test(key)))).toEqual([])
  const initial = await balance(page.request)
  await page.goto('/finance/entry/expense')
  await expect(page.getByRole('button', { name: /测试餐饮/ })).toBeVisible()
  await page.getByRole('button', { name: '1', exact: true }).click()
  await page.getByRole('button', { name: '2', exact: true }).click()
  const saved = page.waitForResponse(r => r.url().endsWith('/api/finance/transactions') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const response = await saved
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.code).toBe(0)
  expect(body.data.userId).toBe(1)
  const payload = response.request().postDataJSON()
  expect(payload.requestId).toBeTruthy()
  expect(response.request().headers().authorization).toBeUndefined()
  expect(await balance(page.request)).toBe(initial - 12)
  for (const replay of await Promise.all([
    page.request.post(`${finance}/transactions`, { data: payload }),
    page.request.post(`${finance}/transactions`, { data: payload }),
  ])) {
    expect(replay.status()).toBe(200)
    expect((await replay.json()).data.id).toBe(body.data.id)
  }
  expect(await balance(page.request)).toBe(initial - 12)
  const rows = await (await page.request.get(`${finance}/transactions`)).json()
  expect(rows.data.filter((row: { id: number }) => row.id === body.data.id)).toHaveLength(1)
  const changed = await page.request.post(`${finance}/transactions`, { data: { ...payload, amount: 13 } })
  expect(changed.status()).toBe(400)
  expect(await balance(page.request)).toBe(initial - 12)
})

test('完整鉴权链限制家庭读取和写入归属，并校验 HTTP 请求参数', async ({ page }) => {
  await login(page)
  expect((await page.request.get(`${finance}/accounts?userId=2`)).status()).toBe(200)
  expect((await page.request.get(`${finance}/accounts?userId=3`)).status()).toBe(403)
  expect((await page.request.get(`${finance}/accounts?userIds=1,3`)).status()).toBe(403)
  const initial = await balance(page.request)
  const familyBalance = await balance(page.request, 3)
  const payload = { userId: 2, accountId: 1, categoryId: 1, type: 'expense', amount: 1,
    occurredAt: '2026-11-06T10:00:00', requestId: randomUUID() }
  const rewritten = await page.request.post(`${finance}/transactions`, { data: payload })
  expect(rewritten.status()).toBe(200)
  expect((await rewritten.json()).data.userId).toBe(1)
  expect(await balance(page.request)).toBe(initial - 1)
  const forbidden = await page.request.post(`${finance}/transactions`, {
    data: { ...payload, requestId: randomUUID(), accountId: 3 },
  })
  expect(forbidden.status()).toBe(400)
  expect(await balance(page.request, 3)).toBe(familyBalance)
  const invalid = await page.request.post(`${finance}/transactions`, {
    data: { ...payload, requestId: randomUUID(), amount: -1 },
  })
  expect(invalid.status()).toBe(400)
  expect(await balance(page.request)).toBe(initial - 1)
  // 内部接口即使携带用户 Cookie 也必须单独校验服务密钥。
  expect((await page.request.get('/auth-api/api/internal/auth/user-status?userId=1')).status()).toBe(401)
})

test('无 Cookie、伪造 Cookie 和真实登出均无法继续提交交易', async ({ page, context }) => {
  expect((await page.request.get(`${finance}/accounts`)).status()).toBe(401)
  await context.addCookies([{ name: 'access_token', value: 'invalid.jwt.signature', url: 'http://127.0.0.1:4176' }])
  expect((await page.request.get(`${finance}/accounts`)).status()).toBe(401)
  await context.clearCookies()
  await login(page)
  expect((await page.request.post(`${auth}/logout`)).status()).toBe(200)
  expect((await context.cookies()).some(cookie => cookie.name === 'access_token')).toBe(false)
  expect((await page.request.post(`${finance}/transactions`, { data: {} })).status()).toBe(401)
  await page.goto('/finance/entry/expense')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  expect(await page.evaluate(() => localStorage.getItem('bookkeeping_current_user'))).toBeNull()
})
