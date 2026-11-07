import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

const api = '/finance-api/api/finance'

test('记一笔可分配多个储蓄目标，扣减可选且账户余额受预留保护', async ({ page }, testInfo) => {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('live-user-1')
  await page.getByPlaceholder('请输入密码').fill('live-test-password')
  await page.getByRole('button', { name: '登录并继续' }).click()
  await expect(page).toHaveURL(/\/finance$/)
  const account = (await (await page.request.get(`${api}/accounts/1`)).json()).data
  const initial = Number(account.currentBalance)
  const createGoal = async (name: string) => {
    const response = await page.request.post(`${api}/savings-goals`, { data: { name, accountId: account.id,
      targetAmount: 1000, targetDate: null, remark: null, requestId: randomUUID() } })
    expect(response.status()).toBe(200)
    return (await response.json()).data
  }
  const emergency = await createGoal('预留应急金'), travel = await createGoal('预留旅行金')
  const readAccount = async () => (await (await page.request.get(`${api}/accounts/${account.id}`)).json()).data
  const readGoals = async () => (await (await page.request.get(`${api}/savings-goals`)).json()).data as { id: number; currentAmount: number }[]
  const money = async (cash: number, a: number, b: number) => {
    expect(Number((await readAccount()).currentBalance)).toBe(cash)
    const goals = await readGoals()
    expect(Number(goals.find(g => g.id === emergency.id)!.currentAmount)).toBe(a)
    expect(Number(goals.find(g => g.id === travel.id)!.currentAmount)).toBe(b)
  }
  const enter = async (type: string, amount: string) => {
    await page.goto(`/finance/entry/expense?type=${type}`)
    await page.getByRole('button', { name: /账户.*测试现金/ }).click()
    await page.getByRole('option', { name: /测试现金/ }).click()
    for (const digit of amount) await page.getByRole('button', { name: digit, exact: true }).click()
  }
  const save = async (status = 200, method = 'POST') => {
    const result = page.waitForResponse(r => /\/transactions(?:\/\d+)?$/.test(new URL(r.url()).pathname) && r.request().method() === method)
    await page.getByRole('button', { name: '保存', exact: true }).click()
    const response = await result
    expect(response.status()).toBe(status)
    return response
  }
  await enter('income', '500')
  await page.getByRole('button', { name: '添加计入储蓄目标', exact: true }).click()
  await page.getByRole('button', { name: /^计入储蓄目标 1 / }).click()
  await page.getByRole('option', { name: /预留应急金/ }).click()
  await page.getByLabel('计入储蓄目标金额 1', { exact: true }).fill('300')
  await page.getByRole('button', { name: '添加计入储蓄目标', exact: true }).click()
  await page.getByLabel('计入储蓄目标金额 2', { exact: true }).fill('200')
  await page.setViewportSize({ width: 375, height: 812 })
  await page.screenshot({ path: testInfo.outputPath('income-allocation.png'), animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const incomeResponse = await save()
  const income = (await incomeResponse.json()).data
  const payload = incomeResponse.request().postDataJSON()
  expect(payload.savingsAllocations).toEqual([{ goalId: emergency.id, amount: 300 }, { goalId: travel.id, amount: 200 }])
  expect((await page.request.post(`${api}/transactions`, { data: payload })).status()).toBe(200)
  await money(initial + 500, 300, 200)

  const editUrl = `/finance/entry/expense?transactionId=${income.id}&type=income&amount=500&accountId=${account.id}&categoryId=2&occurredAt=2026-11-07T12:00:00`
  await page.goto(editUrl)
  await expect(page.getByLabel('计入储蓄目标金额 1', { exact: true })).toHaveValue('300')
  await expect(page.getByLabel('计入储蓄目标金额 2', { exact: true })).toHaveValue('200')
  await page.getByLabel('计入储蓄目标金额 1', { exact: true }).fill('250')
  await save(200, 'PUT')
  await money(initial + 500, 250, 200)
  await enter('expense', String(initial + 50))
  const ordinaryExpense = (await (await save()).json()).data
  await money(450, 250, 200)
  await page.goto(editUrl)
  await expect(page.getByLabel('计入储蓄目标金额 1', { exact: true })).toHaveValue('250')
  await page.getByLabel('输入备注', { exact: true }).fill('只修改备注')
  await save(200, 'PUT')
  await money(450, 250, 200)

  await enter('expense', '50')
  await save(400)
  await expect(page.getByText(/还需从目标释放 50.00/)).toBeVisible()
  await page.getByRole('button', { name: '添加扣减储蓄目标', exact: true }).click()
  await page.getByRole('button', { name: /^扣减储蓄目标 1 / }).click()
  await page.getByRole('option', { name: /预留应急金/ }).click()
  await page.getByLabel('扣减储蓄目标金额 1', { exact: true }).fill('50')
  await page.screenshot({ path: testInfo.outputPath('expense-allocation.png'), animations: 'disabled' })
  const expense = (await (await save()).json()).data
  await money(400, 200, 200)
  expect((await page.request.put(`${api}/accounts/${account.id}`, { data: { ...await readAccount(), currentBalance: 399 } })).status()).toBe(400)
  expect((await page.request.post(`${api}/transactions`, { data: { userId: 1, type: 'transfer', amount: 1,
    fromAccountId: account.id, toAccountId: 2, occurredAt: '2026-11-07T12:00:00', requestId: randomUUID() } })).status()).toBe(400)
  expect((await page.request.post(`${api}/savings-goals/${emergency.id}/entries`, { data: { type: 'deposit', amount: 1,
    occurredOn: '2026-11-07', remark: null, requestId: randomUUID() } })).status()).toBe(400)
  await money(400, 200, 200)
  expect((await page.request.delete(`${api}/transactions/${expense.id}?userId=1`)).status()).toBe(200)
  await money(450, 250, 200)
  expect((await page.request.delete(`${api}/transactions/${ordinaryExpense.id}?userId=1`)).status()).toBe(200)
  expect((await page.request.delete(`${api}/transactions/${income.id}?userId=1`)).status()).toBe(200)
  await money(initial, 0, 0)
  expect((await page.request.delete(`${api}/savings-goals/${emergency.id}`)).status()).toBe(200)
  expect((await page.request.delete(`${api}/savings-goals/${travel.id}`)).status()).toBe(200)
})
