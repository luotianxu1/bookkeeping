import { test, expect, type Page } from '@playwright/test'
import type { ExpenseRecovery, ExpenseReceipt, CategoryBudget } from '../src/api/modules/finance'
const user = { id: 1, username: 'finance-test', displayName: '财务测试' }
async function setup(page: Page) {
  const expense: ExpenseRecovery = { id: 1, title: '出差车费', categoryName: '交通', accountName: '钱包', currencyCode: 'CNY',
    occurredAt: '2026-01-31T12:00:00', amount: 300, reimbursementAmount: 0, refundedAmount: 0,
    reimbursedAmount: 0, recoverableAmount: 300, pendingAmount: 0, reimbursementStatus: 'none', receipts: [] }
  let categoryBudgets: CategoryBudget[] = []
  const submissions: Record<string, unknown>[] = []
  let failNext = false
  function calculate() {
    expense.refundedAmount = expense.receipts!.filter(r => r.kind === 'refund').reduce((s, r) => s + r.amount, 0)
    expense.reimbursedAmount = expense.receipts!.filter(r => r.kind === 'reimbursement').reduce((s, r) => s + r.amount, 0)
    expense.pendingAmount = expense.reimbursementAmount - expense.reimbursedAmount
    expense.recoverableAmount = expense.amount - expense.refundedAmount - expense.reimbursementAmount
    expense.reimbursementStatus = !expense.reimbursementAmount ? 'none' : !expense.reimbursedAmount ? 'pending' : expense.pendingAmount ? 'partial' : 'received'
  }
  await page.addInitScript(user => {
    localStorage.setItem('bookkeeping_current_user', JSON.stringify(user))
    Object.defineProperty(navigator, 'connection', { configurable: true, value: { saveData: true } })
  }, user)
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname, method = route.request().method()
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data, message: '成功', timestamp: Date.now() } })
    if (path.endsWith('/auth/me')) return ok(user)
    if (path.endsWith('/auth/family')) return ok({ hasFamily: false, members: [] })
    if (path.endsWith('/reminders')) return ok({ items: [], unreadCount: 0, snoozedCount: 0 })
    if (path.endsWith('/account-types')) return ok([{ id: 1, code: 'cash', name: '现金' }])
    if (path.endsWith('/accounts')) return ok([{ id: 2, name: '银行卡', currencyCode: 'CNY', currentBalance: 500 }])
    if (path.endsWith('/categories')) return ok([{ id: 1, name: '出行', type: 'expense' }, { id: 2, parentId: 1, name: '交通', type: 'expense' }])
    if (path.endsWith('/monthly-budgets/current')) return ok(null)
    if (path.endsWith('/monthly-budgets')) return ok([])
    if (path.endsWith('/category-budgets')) {
      if (method === 'PUT') {
        const body = route.request().postDataJSON()
        categoryBudgets = [{ id: 1, budgetMonth: body.budgetMonth, categoryId: body.categoryId, categoryName: '交通', amount: body.amount,
          usedAmount: 80, remainingAmount: body.amount - 80, usagePercent: 80 / body.amount * 100, currencyCode: 'CNY' }]
      }
      return ok(categoryBudgets.filter(b => b.budgetMonth === url.searchParams.get('month')))
    }
    if (path.endsWith('/category-budgets/1') && method === 'DELETE') { categoryBudgets = []; return ok(null) }
    if (path.endsWith('/expense-recoveries/1/plan')) { expense.reimbursementAmount = route.request().postDataJSON().amount; calculate(); return ok(expense) }
    if (path.endsWith('/expense-recoveries/1')) return ok(expense)
    if (path.endsWith('/expense-recoveries')) {
      if (method === 'POST') {
        const body = route.request().postDataJSON(); submissions.push(body)
        if (failNext) { failNext = false; return route.fulfill({ status: 503, json: { message: '到账暂不可用，请重试' } }) }
        const receipt: ExpenseReceipt = { ...body, id: submissions.length + 10, accountName: '银行卡' }
        expense.receipts!.push(receipt); calculate(); return ok(receipt)
      }
      const state = url.searchParams.get('state')
      const items = state === 'all' || state === expense.reimbursementStatus ? [expense] : []
      return ok({ items, page: 1, totalPages: 1, total: items.length })
    }
    if (/\/transactions\/\d+$/.test(path) && method === 'DELETE') {
      const id = Number(path.split('/').pop()); expense.receipts = expense.receipts!.filter(r => r.id !== id); calculate(); return ok(null)
    }
    return ok([])
  })
  return { expense, submissions, failNext: () => { failNext = true } }
}

test('报销计划、部分到账、失败重试、退款和撤销', async ({ page }, testInfo) => {
  const api = await setup(page)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/finance/expense-recoveries?transactionId=1')
  await expect(page.getByRole('heading', { name: '支出详情', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '报销计划', exact: true }).click()
  await page.getByLabel('计划报销总额').fill('200')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.expense-card')).toContainText('待报销')
  await page.getByRole('button', { name: '报销到账', exact: true }).click()
  await page.getByLabel('本次到账金额').fill('80')
  api.failNext()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('到账暂不可用')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.expense-card')).toContainText('部分到账')
  expect(api.submissions[0]?.requestId).toBe(api.submissions[1]?.requestId)
  expect(api.submissions[0]?.occurredAt).toBe(api.submissions[1]?.occurredAt)
  await page.getByRole('button', { name: '登记退款', exact: true }).click()
  await expect(page.getByLabel('本次到账金额')).toHaveValue('100')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.receipt')).toHaveCount(2)
  await page.getByRole('button', { name: '报销到账', exact: true }).click()
  await expect(page.getByLabel('本次到账金额')).toHaveValue('120')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.expense-card')).toContainText('全部到账')
  await expect(page.getByRole('heading', { name: '支出详情', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '登记报销到账', exact: true })).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath('recovery-detail.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.receipt').filter({ hasText: '80.00' }).getByRole('button', { name: '撤销', exact: true }).click()
  await page.getByRole('button', { name: '确认撤销', exact: true }).click()
  await expect(page.locator('.expense-card')).toContainText('部分到账')
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('button', { name: '部分到账', exact: true }).click()
  await expect(page.locator('.expense-card')).toHaveCount(1)
  await page.getByRole('button', { name: '全部到账', exact: true }).click()
  await expect(page.getByText('暂无符合条件的支出')).toBeVisible()
})

test('分类预算增改删、80%预警和月份切换', async ({ page }, testInfo) => {
  await setup(page)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/finance/budgets')
  await page.getByRole('button', { name: '新增分类预算', exact: true }).click()
  await page.getByLabel('预算金额', { exact: true }).fill('100')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.category-card')).toContainText('剩余 ¥20.00')
  await expect(page.locator('.category-card')).toContainText('预算即将用完')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath('category-budget.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.category-card').getByRole('button', { name: '修改', exact: true }).click()
  await page.getByLabel('预算金额', { exact: true }).fill('50')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.category-card')).toContainText('超出 ¥30.00')
  const month = await page.getByLabel('查看月份').inputValue()
  await page.getByLabel('查看月份').fill('2025-01')
  await expect(page.getByText('该月还没有分类预算')).toBeVisible()
  await page.getByLabel('查看月份').fill(month)
  await expect(page.locator('.category-card')).toHaveCount(1)
  await page.locator('.category-card').getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  await expect(page.locator('.category-card')).toHaveCount(0)
})
