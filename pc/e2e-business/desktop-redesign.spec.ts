import { expect, test } from '@playwright/test'
import { setupDesktop } from './desktop-fixtures'

test('desktop ledger keeps signed recoveries, protected term deposits and query controls', async ({
  page,
}, info) => {
  await setupDesktop(page)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (msg) => {
    if (msg.type() === 'error' && /TypeError|ReferenceError/.test(msg.text()))
      errors.push(msg.text())
  })
  await page.goto('/finance/transactions')
  await expect(
    page.getByRole('row').filter({ hasText: '午餐退款' }),
  ).toContainText('12.00')
  await expect(
    page.getByRole('row').filter({ hasText: '定期本金' }).getByRole('button'),
  ).toHaveCount(0)
  await expect(
    page
      .getByRole('row')
      .filter({ hasText: '午餐退款' })
      .getByRole('button', { name: '编辑' }),
  ).toHaveCount(0)
  await page.getByLabel('关键词', { exact: true }).fill('工作午餐')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(
    page.getByRole('row').filter({ hasText: '月度工资' }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('row').filter({ hasText: '工作午餐' }),
  ).toBeVisible()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(
    page.getByRole('row').filter({ hasText: '月度工资' }),
  ).toBeVisible()
  await page.screenshot({
    path: info.outputPath('ledger-1440.png'),
    animations: 'disabled',
  })
  expect(errors).toEqual([])
})

test('populated desktop workspaces render metrics, charts, tables and contextual details', async ({
  page,
}, info) => {
  test.setTimeout(120_000)
  await setupDesktop(page)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (msg) => {
    if (msg.type() === 'error' && /TypeError|ReferenceError/.test(msg.text()))
      errors.push(msg.text())
  })
  for (const [path, content] of [
    ['/finance', '工作午餐'],
    ['/finance/analysis', '分类明细'],
    ['/finance/accounts', '日常账户'],
    ['/finance/accounts/cash/10', '工作午餐'],
    ['/finance/budgets', '午餐'],
    ['/finance/salary', '养老保险'],
    ['/finance/salary/records', '绩效已包含'],
    ['/food/dishes/1', '切番茄并打散鸡蛋'],
    ['/food/menu/1', '番茄炒蛋'],
    ['/profile/security', 'Chrome · macOS'],
    ['/profile/family-members', '家庭成员'],
    ['/finance/global-market/a', '测试工业'],
  ]) {
    await page.goto(path)
    await expect(page.getByRole('main', { name: '功能区' })).toContainText(
      content,
    )
    await expect(page.locator('.el-skeleton')).toHaveCount(0)
    for (const width of [1440, 1024]) {
      await page.setViewportSize({ width, height: 1000 })
      await page.screenshot({
        path: info.outputPath(path.replace(/\//g, '-') + '-' + width + '.png'),
        animations: 'disabled',
      })
      expect(
        await page
          .locator('.page-container')
          .evaluate((el) => el.scrollWidth <= el.clientWidth),
        path + ' overflows at ' + width,
      ).toBe(true)
    }
  }
  expect(errors).toEqual([])
})

test('desktop table searches all records and sorts before pagination', async ({
  page,
}) => {
  await setupDesktop(page)
  await page.route('**/api/tools/contacts?**', (route) =>
    route.fulfill({
      json: {
        code: 0,
        message: '成功',
        timestamp: Date.now(),
        data: Array.from({ length: 35 }, (_, i) => ({
          id: i + 1,
          userId: 7,
          name: `联系人${35 - i}`,
          phone: `1390000${String(i).padStart(4, '0')}`,
          remark: i === 34 ? '末页记录' : '',
          status: 'active',
        })),
      },
    }),
  )
  await page.goto('/tools/contacts')
  await expect(
    page.getByRole('row').filter({ hasText: '联系人35' }),
  ).toBeVisible()
  await page.getByRole('columnheader', { name: '姓名' }).click()
  await expect(page.locator('.el-table__body tr').first()).toContainText(
    '联系人1',
  )
  await page.getByPlaceholder('姓名 / 电话 / 备注').fill('末页记录')
  await expect(page.locator('.el-table__body tr')).toHaveCount(1)
  await expect(page.locator('.el-table__body tr')).toContainText('联系人1')
})
