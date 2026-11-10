import { expect, test } from '@playwright/test'
import { modules } from '../src/data/modules'
import { setup } from './fixtures'

test('all module entries mount and show a recoverable service failure without crashing', async ({
  page,
}) => {
  test.setTimeout(180_000)
  await setup(page, true)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  for (const group of modules.filter((x) => x.id !== 'access'))
    for (const item of group.entries) {
      await page.goto(item.path)
      await expect(page.locator('.admin-shell')).toBeVisible()
      await expect(page.getByRole('main', { name: '功能区' })).not.toBeEmpty()
      await expect(page.locator('.topbar')).toContainText(group.title)
      await expect(
        page.locator('.el-sub-menu.is-opened > .el-sub-menu__title'),
      ).toContainText(group.title)
    }
  expect(errors).toEqual([])
})

test('contact CRUD uses desktop dialogs and displays actual contact data', async ({
  page,
}, info) => {
  const { calls } = await setup(page)
  await page.goto('/tools/contacts')
  await expect(page.getByRole('row').filter({ hasText: '王琳' })).toContainText(
    '13911112222',
  )
  await expect(page.getByRole('row').filter({ hasText: '王琳' })).toContainText(
    '实际备注',
  )
  await page.getByRole('button', { name: '新增联系人', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('姓名', { exact: false }).fill('新增联系人测试')
  await dialog.getByLabel('手机', { exact: false }).fill('13612345678')
  await dialog.getByRole('button', { name: /保存|新增/ }).click()
  await expect(dialog).not.toBeVisible()
  let row = page.getByRole('row').filter({ hasText: '新增联系人测试' })
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await dialog.getByLabel('姓名', { exact: false }).fill('已修改联系人')
  await dialog.getByRole('button', { name: /保存|新增/ }).click()
  row = page.getByRole('row').filter({ hasText: '已修改联系人' })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '删除', exact: true }).click()
  await dialog.getByRole('button', { name: /确认删除|^删除$/ }).click()
  await expect(row).toHaveCount(0)
  await expect(dialog).not.toBeVisible()
  await expect(page.locator('.el-overlay:visible')).toHaveCount(0)
  expect(calls.some((x) => x.method === 'POST' && x.data.userId === 7)).toBe(
    true,
  )
  for (const width of [1440, 1024]) {
    await page.setViewportSize({ width, height: 900 })
    await expect(
      page.getByRole('button', { name: '新增联系人', exact: true }),
    ).toBeInViewport()
    await expect(
      page.getByRole('button', { name: '编辑', exact: true }),
    ).toBeInViewport()
    await page.screenshot({
      animations: 'disabled',
      path: info.outputPath(`contacts-${width}.png`),
    })
    expect(
      await page.evaluate(
        () =>
          document.documentElement.scrollWidth <= innerWidth &&
          document.querySelector('.page-container')!.scrollWidth <=
            document.querySelector('.page-container')!.clientWidth,
      ),
    ).toBe(true)
  }
})

test('module links keep the same shell and do not request another document', async ({
  page,
}, info) => {
  await setup(page)
  await page.goto('/overview')
  const shell = await page.locator('.admin-shell').elementHandle(),
    origin = await page.evaluate(() => performance.timeOrigin)
  const requests: string[] = []
  page.on('request', (r) => {
    if (r.isNavigationRequest() && r.frame() === page.mainFrame())
      requests.push(r.url())
  })
  for (const [group, entry, path] of [
    ['生活工具', '联系人', '/tools/contacts'],
    ['资产管理', '现金账户', '/finance/accounts/cash'],
    ['家庭餐饮', '菜品管理', '/food/dishes'],
    ['账号与家庭', '账号设置', '/profile'],
  ]) {
    await page.locator('.el-sub-menu__title').filter({ hasText: group }).click()
    await page.getByRole('menuitem', { name: entry, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(path + '$'))
    expect(
      await shell!.evaluate(
        (n) => n === document.querySelector('.admin-shell'),
      ),
    ).toBe(true)
  }
  await page.goBack()
  await expect(page).toHaveURL(/\/food\/dishes$/)
  expect(await page.evaluate(() => performance.timeOrigin)).toBe(origin)
  expect(requests).toEqual([])
  await page.screenshot({
    animations: 'disabled',
    path: info.outputPath('dishes-desktop.png'),
  })
})

test('desktop transaction form preserves account, category and idempotent request fields', async ({
  page,
}, info) => {
  const { calls } = await setup(page)
  await page.goto('/finance/entry/expense')
  await page.getByRole('textbox', { name: '金额', exact: true }).fill('28.50')
  await expect(
    page.getByRole('button', { name: '保存', exact: true }),
  ).toBeEnabled()
  await page.screenshot({
    animations: 'disabled',
    path: info.outputPath('entry-desktop.png'),
  })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect
    .poll(
      () =>
        calls.filter(
          (x) => x.method === 'POST' && x.path === '/api/finance/transactions',
        ).length,
    )
    .toBe(1)
  const saved = calls.find(
    (x) => x.method === 'POST' && x.path === '/api/finance/transactions',
  )!.data
  expect(saved).toMatchObject({
    userId: 7,
    accountId: 10,
    categoryId: 2,
    amount: 28.5,
    type: 'expense',
  })
  expect(saved.requestId).toEqual(expect.any(String))
})

test('food selection creates a menu and fund detail renders a desktop chart', async ({
  page,
}, info) => {
  const { calls } = await setup(page)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/food/dishes?entry=menu')
  await page
    .getByRole('row')
    .filter({ hasText: '番茄炒蛋' })
    .getByRole('button', { name: '选择', exact: true })
    .click()
  await page.getByRole('button', { name: '生成菜单', exact: true }).click()
  await expect
    .poll(
      () =>
        calls.filter(
          (x) => x.method === 'POST' && x.path === '/api/tools/food/orders',
        ).length,
    )
    .toBe(1)
  await page.goto('/finance/global-market/funds')
  await expect(page.getByText('测试优选混合A', { exact: true })).toBeVisible()
  await page.getByText('测试优选混合A', { exact: true }).click()
  await expect(page).toHaveURL(/\/funds\/001234$/)
  const chart = page.getByRole('img', { name: '单位净值走势图' })
  await expect(chart.locator('canvas')).toHaveCount(1)
  expect((await chart.boundingBox())!.height).toBeGreaterThan(200)
  await page.screenshot({
    animations: 'disabled',
    path: info.outputPath('fund-detail-desktop.png'),
  })
  expect(errors).toEqual([])
})

test('desktop calendar and financial projections use full-size grids and charts', async ({
  page,
}, info) => {
  await setup(page)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/tools/calendar')
  await expect(page.getByText('结婚纪念日', { exact: true })).toBeVisible()
  expect(
    await page
      .locator('.calendar-page .calendar-grid')
      .evaluate(
        (el) => getComputedStyle(el).gridTemplateColumns.split(' ').length,
      ),
  ).toBe(7)
  await page.screenshot({
    path: info.outputPath('calendar-desktop.png'),
    animations: 'disabled',
  })
  await page.goto('/finance/free-estimate')
  await page.getByLabel('每月生活费', { exact: true }).fill('4000')
  await page.getByRole('button', { name: '乐观方案', exact: true }).click()
  const chart = page.getByRole('img', { name: /未来30年资产趋势/ })
  await expect(chart.locator('canvas')).toHaveCount(1)
  await expect(chart).toHaveAttribute('aria-label', /突出乐观方案/)
  await page.screenshot({
    path: info.outputPath('projection-desktop.png'),
    animations: 'disabled',
  })
  for (const width of [1440, 1024]) {
    await page.setViewportSize({ width, height: 900 })
    expect(
      await page
        .locator('.page-container')
        .evaluate((el) => el.scrollWidth <= el.clientWidth),
    ).toBe(true)
  }
  expect(errors).toEqual([])
})

test('weight history renders on initial load and desktop form records a check-in', async ({
  page,
}, info) => {
  const { calls } = await setup(page)
  await page.goto('/tools/health/records')
  await expect(
    page.getByRole('row').filter({ hasText: '晨起空腹' }),
  ).toContainText('68.5')
  await expect(page.locator('.record-chart canvas')).toHaveCount(1)
  await page.getByLabel('体重（kg）', { exact: true }).fill('68.2')
  await page.getByLabel('备注（选填）', { exact: true }).fill('桌面打卡')
  await page.getByRole('button', { name: '保存打卡', exact: true }).click()
  await expect(
    page.getByRole('row').filter({ hasText: '桌面打卡' }),
  ).toContainText('68.2')
  expect(
    calls.find(
      (x) => x.path === '/api/tools/weight-records' && x.method === 'POST',
    )?.data,
  ).toMatchObject({ userId: 7, weight: 68.2 })
  await page.screenshot({
    path: info.outputPath('weight-desktop.png'),
    animations: 'disabled',
  })
})
