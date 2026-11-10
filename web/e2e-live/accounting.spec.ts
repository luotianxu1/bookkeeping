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
  for (const query of ['userId=', 'userId=%20', 'userIds=', 'userIds=1,', 'userId=1&userId=']) {
    expect((await page.request.get(`${finance}/accounts?${query}`)).status()).toBe(400)
  }
  const summary = await page.request.get(`${finance}/accounts/summary?userId=2`)
  expect(summary.status()).toBe(200)
  expect((await summary.json()).data.accounts.every((account: { userId: number }) => account.userId === 2)).toBe(true)
  expect((await page.request.get(`${finance}/accounts/summary?userId=3`)).status()).toBe(403)
  expect((await page.request.post(`${finance}/scheduled-tasks/today/salary-settlement/scheduled-08:10/execute`)).status()).toBe(403)
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

test('真实支出 → 分类预算 → 报销分次到账和退款 → 撤销与越权校验', async ({ page }) => {
  await login(page)
  const month = new Date()
  const currentMonth = `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, '0')}-01`
  expect((await page.request.put(`${finance}/category-budgets`, { data: { budgetMonth: currentMonth, categoryId: 1, amount: 1 } })).status()).toBe(200)
  const reminderResponse = await page.request.get('/tool-api/api/tools/reminders')
  expect(reminderResponse.status()).toBe(200)
  const reminders = (await reminderResponse.json()).data.items
  expect(reminders.some((r: { type: string; urgency: string }) => r.type === 'budget' && r.urgency === 'exceeded')).toBe(true)
  const before = await balance(page.request, 2)
  const created = await page.request.post(`${finance}/transactions`, { data: {
    userId: 1, type: 'expense', accountId: 1, categoryId: 1, amount: 100, currencyCode: 'CNY',
    title: '报销联通测试', occurredAt: '2026-01-31T12:00:00', requestId: randomUUID(),
  } })
  expect(created.status()).toBe(200)
  const id = (await created.json()).data.id
  expect((await page.request.put(`${finance}/category-budgets`, { data: { budgetMonth: '2026-01-01', categoryId: 1, amount: 100 } })).status()).toBe(200)
  const budgets = await (await page.request.get(`${finance}/category-budgets?month=2026-01-01`)).json()
  expect(budgets.data[0]).toMatchObject({ usedAmount: 100, remainingAmount: 0, usagePercent: 100 })
  expect((await page.request.put(`${finance}/expense-recoveries/${id}/plan`, { data: { amount: 80 } })).status()).toBe(200)
  expect((await page.request.put(`${finance}/expense-recoveries/${id}/plan`, { data: { amount: 100.001 } })).status()).toBe(400)
  await page.goto(`/finance/expense-recoveries?transactionId=${id}`)
  await expect(page.getByRole('heading', { name: '支出详情', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '报销到账', exact: true }).click()
  await page.getByLabel('本次到账金额').fill('30')
  // Select the actual second cash account through the shared select.
  await page.getByRole('button', { name: /到账账户/ }).click()
  await page.getByRole('option', { name: '测试储蓄', exact: true }).click()
  const submitted = page.waitForResponse(r => r.url().endsWith('/api/finance/expense-recoveries') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  const receiptResponse = await submitted
  expect(receiptResponse.status()).toBe(200)
  const receipt = (await receiptResponse.json()).data
  await expect(page.locator('.expense-card')).toContainText('部分到账')
  expect(await balance(page.request, 2)).toBe(before + 30)
  const retry = await page.request.post(`${finance}/expense-recoveries`, { data: receiptResponse.request().postDataJSON() })
  expect((await retry.json()).data.id).toBe(receipt.id)
  expect(await balance(page.request, 2)).toBe(before + 30)
  const receive = (amount: number, kind: string, accountId = 2) => page.request.post(`${finance}/expense-recoveries`, { data: {
    transactionId: id, accountId, amount, kind, occurredAt: '2026-02-01T12:00:00', requestId: randomUUID(),
  } })
  expect((await receive(1, 'refund', 3)).status()).toBe(400)
  expect((await receive(21, 'refund')).status()).toBe(400)
  expect((await receive(51, 'reimbursement')).status()).toBe(400)
  expect((await receive(50, 'reimbursement')).status()).toBe(200)
  expect((await receive(20, 'refund')).status()).toBe(200)
  expect(await balance(page.request, 2)).toBe(before + 100)
  const final = await (await page.request.get(`${finance}/expense-recoveries/${id}`)).json()
  expect(final.data).toMatchObject({ reimbursementStatus: 'received', refundedAmount: 20, reimbursedAmount: 80, pendingAmount: 0 })
  expect((await page.request.delete(`${finance}/transactions/${id}?userId=1`)).status()).toBe(400)
  expect((await page.request.delete(`${finance}/transactions/${receipt.id}?userId=1`)).status()).toBe(200)
  expect(await balance(page.request, 2)).toBe(before + 70)
  const partial = await (await page.request.get(`${finance}/expense-recoveries/${id}`)).json()
  expect(partial.data).toMatchObject({ reimbursementStatus: 'partial', pendingAmount: 30 })
  const january = await (await page.request.get(`${finance}/transactions/analysis?period=month&month=2026-01`)).json()
  const february = await (await page.request.get(`${finance}/transactions/analysis?period=month&month=2026-02`)).json()
  expect(january.data.summary.expense).toBe(100)
  expect(february.data.summary).toMatchObject({ expense: -70, income: 0, surplus: 70 })
  await page.request.post(`${auth}/logout`)
  const other = await page.request.post(`${auth}/login`, { data: { username: 'live-user-2', password: 'live-test-password' } })
  expect(other.status()).toBe(200)
  expect((await page.request.get(`${finance}/expense-recoveries/${id}`)).status()).toBe(400)
  expect((await page.request.put(`${finance}/expense-recoveries/${id}/plan`, { data: { amount: 80 } })).status()).toBe(400)
})

test('真实流水搜索与报销跟进提醒、部分到账和撤销联动', async ({ page }, testInfo) => {
  await login(page)
  const created = await page.request.post(`${finance}/transactions`, { data: { userId: 1, type: 'expense', accountId: 1, categoryId: 1,
    amount: 100, currencyCode: 'CNY', title: '报销跟进检索', remark: '采购出差', occurredAt: '2026-01-31T12:00:00', requestId: randomUUID() } })
  expect(created.status()).toBe(200)
  const id = (await created.json()).data.id
  const search = await page.request.get(`${finance}/transactions/page?keyword=采购&categoryId=1&minAmount=50&maxAmount=100&cashOnly=true`)
  const searched = (await search.json()).data
  expect(searched.total).toBe(1); expect(searched.items[0].id).toBe(id); expect(searched.expenseTotal).toBe(100)
  expect((await page.request.get(`${finance}/transactions/page?minAmount=100&maxAmount=50`)).status()).toBe(400)
  const before = await balance(page.request, 1)
  await page.goto(`/finance/expense-recoveries?transactionId=${id}`)
  await page.getByRole('button', { name: '报销计划', exact: true }).click()
  await page.getByLabel('计划报销总额').fill('80')
  await page.getByLabel('报销对象').fill('公司财务')
  await page.getByLabel('预计到账日期').fill('2026-02-01')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('heading', { name: '支出详情' })).toBeVisible()
  await expect(page.locator('.detail-content')).toContainText('公司财务')
  await expect(page.getByLabel('待报销汇总')).toContainText('80.00')
  expect(await balance(page.request, 1)).toBe(before)
  const reminders = async () => (await (await page.request.get('/tool-api/api/tools/reminders')).json()).data.items as {type: string; targetPath: string; description: string; urgency: string}[]
  const reminder = (await reminders()).find(r => r.targetPath === `/finance/expense-recoveries?transactionId=${id}`)!
  expect(reminder.type).toBe('reimbursement'); expect(reminder.urgency).toBe('overdue'); expect(reminder.description).toContain('公司财务')
  await page.getByRole('button', { name: '报销到账', exact: true }).click()
  await page.getByLabel('本次到账金额').fill('30')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByLabel('待报销汇总')).toContainText('50.00')
  expect((await reminders()).find(r => r.targetPath.endsWith(`transactionId=${id}`))?.description).toContain('CNY 50')
  await page.screenshot({ path: testInfo.outputPath('reimbursement-followup.png'), animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const receivedBalance = await balance(page.request, 1)
  await page.getByRole('button', { name: '撤销剩余报销', exact: true }).click()
  await page.getByRole('button', { name: '确认撤销', exact: true }).click()
  await expect(page.getByLabel('待报销汇总')).toHaveCount(0)
  await expect(page.locator('.receipt')).toHaveCount(1)
  expect(await balance(page.request, 1)).toBe(receivedBalance)
  expect((await reminders()).some(r => r.targetPath.endsWith(`transactionId=${id}`))).toBe(false)
  await page.reload()
  await expect(page.locator('.detail-content')).toContainText('公司财务')
  await expect(page.locator('.detail-content')).toContainText('2026-02-01')
})
