import { expect, test, type Route } from '@playwright/test'
import { setupDesktop } from './desktop-fixtures'
import { accounts } from './fixtures'
import type { TermDeposit } from '../src/business/api/modules/finance'

const ok = (route: Route, data: unknown) =>
  route.fulfill({
    json: { code: 0, message: '成功', timestamp: Date.now(), data },
  })

test('rounded percentage displays preserve the original interest rate when saving', async ({
  page,
}) => {
  await setupDesktop(page)
  const deposit: TermDeposit = {
    id: 1,
    accountId: 20,
    accountName: '定期账户',
    fundingAccountId: 10,
    payoutAccountId: 10,
    interestCategoryId: null,
    interestCategoryName: null,
    parentDepositId: null,
    name: '精度验证存单',
    currencyCode: 'CNY',
    principal: 10000,
    annualRate: 3.2575,
    startDate: '2026-01-01',
    maturityDate: '2027-01-01',
    termMonths: 12,
    maturityAction: 'withdraw',
    openingMode: 'existing',
    status: 'active',
    remark: null,
    expectedInterest: 325.75,
    expectedTotal: 10325.75,
    daysRemaining: 52,
    asOfDate: '2026-11-10',
    canCancel: true,
  }
  const saved: Record<string, unknown>[] = []
  await page.route('**/api/finance/accounts?**', (route) =>
    ok(route, [
      ...accounts,
      {
        ...accounts[0],
        id: 20,
        accountTypeId: 2,
        accountTypeCode: 'term_deposit',
        name: '定期账户',
        currentBalance: 0,
      },
    ]),
  )
  await page.route('**/api/finance/term-deposits**', (route) => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON()
      saved.push(payload)
      Object.assign(deposit, payload)
      return ok(route, deposit)
    }
    return ok(route, [deposit])
  })

  await page.goto('/finance/term-deposits')
  const row = page.getByRole('row').filter({ hasText: '精度验证存单' })
  await expect(row).toContainText('3.3%')
  await row.getByRole('button', { name: '修改', exact: true }).click()
  const dialog = page.getByRole('dialog')
  const rate = dialog.getByLabel('年利率（%）', { exact: true })
  await expect(rate).toHaveValue('3.3')
  await rate.focus()
  await expect(rate).toHaveValue('3.2575')
  await rate.blur()
  await expect(rate).toHaveValue('3.3')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(saved).toHaveLength(1)
  expect(saved[0].annualRate).toBe(3.2575)

  // Editing and blurring must also keep the newly entered precision intact.
  await row.getByRole('button', { name: '修改', exact: true }).click()
  await rate.fill('3.125')
  await rate.blur()
  await expect(rate).toHaveValue('3.1')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(saved).toHaveLength(2)
  expect(saved[1].annualRate).toBe(3.125)
  await expect(row).toContainText('3.1%')

  await page.goto('/finance/free-estimate')
  const withdrawalRate = page.getByRole('spinbutton', { name: '安全提现率' })
  await withdrawalRate.fill('3.25')
  await withdrawalRate.blur()
  await expect(withdrawalRate).toHaveValue('3.3')
  await withdrawalRate.focus()
  await expect(withdrawalRate).toHaveValue('3.25')
  await withdrawalRate.blur()
  await withdrawalRate.fill('4.125')
  await withdrawalRate.blur()
  await expect(withdrawalRate).toHaveValue('4.1')
  await withdrawalRate.focus()
  await expect(withdrawalRate).toHaveValue('4.125')
})
