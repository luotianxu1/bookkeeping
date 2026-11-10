import { expect, test, type Page } from '@playwright/test'
import { setupDesktop } from './desktop-fixtures'

const incomeColor = 'rgb(214, 83, 101)'
const expenseColor = 'rgb(36, 156, 120)'
const metric = (page: Page, label: string) =>
  page
    .locator('.desktop-metric')
    .filter({ has: page.getByText(label, { exact: true }) })
    .locator('strong')

for (const theme of ['light', 'dark']) {
  test(`cash amounts keep income red, expenses green and transfers neutral in ${theme} mode`, async ({
    page,
  }, info) => {
    await setupDesktop(page)
    await page.addInitScript(
      (mode) => localStorage.setItem('pc-theme-mode', mode),
      theme,
    )
    for (const path of [
      '/finance',
      '/finance/transactions',
      '/finance/accounts/cash/10',
      '/finance/analysis',
    ]) {
      await page.goto(path)
      if (path.endsWith('analysis'))
        await page.getByRole('radiogroup', { name: '分析指标' })
          .getByText('结余', { exact: true }).click()
      const rowAmount = (title: string) =>
        page.getByRole('row').filter({ hasText: title }).locator('.amount-text')
      await expect(rowAmount('月度工资')).toHaveCSS('color', incomeColor)
      await expect(rowAmount('工作午餐')).toHaveCSS('color', expenseColor)
      await expect(rowAmount('午餐退款')).toHaveCSS('color', incomeColor)
      await expect(rowAmount('定期本金')).not.toHaveCSS('color', incomeColor)
      await expect(rowAmount('定期本金')).not.toHaveCSS('color', expenseColor)
      if (path === '/finance') {
        await expect(metric(page, '本月收入')).toHaveCSS('color', incomeColor)
        await expect(metric(page, '本月净支出')).toHaveCSS(
          'color',
          expenseColor,
        )
        await expect(metric(page, '本月结余')).toHaveCSS('color', incomeColor)
        await expect(
          page
            .getByRole('img', { name: '近两周现金收支趋势' })
            .locator('canvas'),
        ).toBeVisible()
        await page.screenshot({
          path: info.outputPath('finance-colors.png'),
          animations: 'disabled',
        })
      }
      if (path === '/finance/transactions' || path === '/finance/analysis') {
        await expect(metric(page, '收入')).toHaveCSS('color', incomeColor)
        await expect(
          metric(page, path.endsWith('analysis') ? '支出' : '净支出'),
        ).toHaveCSS('color', expenseColor)
        await expect(metric(page, '结余')).toHaveCSS('color', incomeColor)
      }
    }
  })
}

test('refund-only totals, negative balances and zero amounts use their actual cash direction', async ({
  page,
}) => {
  await setupDesktop(page)
  const income = 0
  let expense = -12
  await page.route('**/api/finance/transactions/page?**', (route) =>
    route.fulfill({
      json: {
        code: 0,
        message: '成功',
        timestamp: Date.now(),
        data: {
          items: [],
          total: 0,
          page: 1,
          totalPages: 1,
          pageSize: 50,
          incomeTotal: income,
          expenseTotal: expense,
          balanceTotal: income - expense,
        },
      },
    }),
  )
  await page.goto('/finance/transactions')
  await expect(metric(page, '净支出')).toContainText('-¥12.00')
  await expect(metric(page, '净支出')).toHaveCSS('color', incomeColor)
  await expect(metric(page, '结余')).toHaveCSS('color', incomeColor)
  await expect(metric(page, '收入')).not.toHaveCSS('color', incomeColor)
  await expect(metric(page, '收入')).not.toHaveCSS('color', expenseColor)
  expense = 50
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(metric(page, '结余')).toContainText('-¥50.00')
  await expect(metric(page, '结余')).toHaveCSS('color', expenseColor)
  expense = 0
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(metric(page, '结余')).toContainText('0.00')
  await expect(metric(page, '结余')).not.toHaveCSS('color', incomeColor)
  await expect(metric(page, '结余')).not.toHaveCSS('color', expenseColor)
})

test('refund and reimbursement colors match between the table and the teleported detail dialog', async ({
  page,
}, info) => {
  await setupDesktop(page)
  const detail = {
    id: 1,
    title: '差旅支出',
    amount: 100,
    refundedAmount: 12,
    reimbursedAmount: 20,
    reimbursementAmount: 60,
    pendingAmount: 40,
    currencyCode: 'CNY',
    categoryName: '差旅',
    accountName: '日常账户',
    occurredAt: '2026-11-10T09:00:00',
    receipts: [
      {
        id: 2,
        kind: 'refund',
        amount: 12,
        accountName: '日常账户',
        occurredAt: '2026-11-10T10:00:00',
      },
      {
        id: 3,
        kind: 'reimbursement',
        amount: 20,
        accountName: '日常账户',
        occurredAt: '2026-11-10T11:00:00',
      },
    ],
  }
  await page.route('**/api/finance/expense-recoveries**', (route) =>
    route.fulfill({
      json: {
        code: 0,
        message: '成功',
        timestamp: Date.now(),
        data: new URL(route.request().url()).pathname.endsWith('/1')
          ? detail
          : {
              items: [detail],
              total: 1,
              page: 1,
              totalPages: 1,
              pendingSummary: [],
            },
      },
    }),
  )
  await page.goto('/finance/expense-recoveries')
  const amounts = page
    .getByRole('row')
    .filter({ hasText: '差旅支出' })
    .locator('.amount-text')
  await expect(amounts.nth(0)).toHaveCSS('color', expenseColor)
  await expect(amounts.nth(1)).toHaveCSS('color', incomeColor)
  await expect(amounts.nth(2)).toHaveCSS('color', incomeColor)
  await page.getByRole('button', { name: '详情 / 登记到账' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.locator('.card-heading > .amount-text')).toHaveCSS(
    'color',
    expenseColor,
  )
  for (const amount of await dialog.locator('.receipt .amount-text').all())
    await expect(amount).toHaveCSS('color', incomeColor)
  await expect(dialog.locator('.receipt .amount-text')).toHaveCount(2)
  await page.screenshot({
    path: info.outputPath('recovery-colors.png'),
    animations: 'disabled',
  })
})
