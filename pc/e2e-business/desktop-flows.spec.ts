import { expect, test, type Route } from '@playwright/test'
import { setupDesktop } from './desktop-fixtures'
import type { TravelPlanDetail } from '../src/business/api/modules/tool'

const ok = (route: Route, data: unknown) =>
  route.fulfill({
    json: { code: 0, message: '成功', timestamp: Date.now(), data },
  })

test('desktop investment workspace renders its chart and retries a trade with the same request ID', async ({
  page,
}, info) => {
  await setupDesktop(page)
  const requests: Record<string, unknown>[] = []
  await page.route('**/api/finance/investments/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/positions/1/detail'))
      return ok(route, {
        name: '桌面测试基金',
        symbol: '',
        productType: 'fund',
        chartType: 'line',
        latestPrice: 1.1,
        position: {
          id: 1,
          userId: 7,
          accountId: 3,
          productId: 1,
          productType: 'fund',
          productName: '桌面测试基金',
          productSymbol: '',
          holdingQuantity: 100,
          availableQuantity: 100,
          frozenQuantity: 0,
          costAmount: 100,
          currentPrice: 1.1,
          marketValue: 110,
          cumulativeProfit: 10,
          holdingProfit: 10,
          currencyCode: 'CNY',
          subscriptionStatus: 'confirmed',
          dividendType: 'cash',
        },
        marketStats: [{ label: '单位净值', value: '1.1000' }],
        holdingStats: [{ label: '持有份额', value: '100.00' }],
        dividendRecords: [],
        fundRedeemFeeOptions: [],
        chartPoints: [
          { label: '2026-11-04', value: 1 },
          { label: '2026-11-05', value: 1.1 },
        ],
      })
    if (path.endsWith('/transactions/page'))
      return ok(route, {
        items: [],
        total: 0,
        page: 1,
        pageSize: 10,
        totalPages: 1,
      })
    if (path.endsWith('/transactions') && route.request().method() === 'POST') {
      const data = route.request().postDataJSON()
      requests.push(data)
      if (requests.length === 1) return route.abort('failed')
      return ok(route, { ...data, id: 7, settlementStatus: 'pending' })
    }
    return ok(route, [])
  })
  await page.goto('/finance/accounts/investment/detail?positionId=1')
  await expect(
    page.getByRole('heading', { name: '桌面测试基金' }),
  ).toBeVisible()
  await expect(
    page.locator('.investment-detail-chart canvas').first(),
  ).toBeVisible()
  await page.getByRole('tab', { name: '行情数据' }).click()
  await expect(page.getByText('单位净值', { exact: true })).toBeVisible()
  await page.screenshot({
    path: info.outputPath('investment-1440.png'),
    animations: 'disabled',
  })
  await page.getByRole('button', { name: '加仓', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('请输入申购金额').fill('100')
  await dialog.getByRole('button', { name: '加仓', exact: true }).click()
  await expect(dialog.locator('.investment-detail-modal-error')).toBeVisible()
  await dialog.getByRole('button', { name: '加仓', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(requests).toHaveLength(2)
  expect(requests[0].requestId).toMatch(/^[a-f0-9-]{36}$/)
  expect(requests[1].requestId).toBe(requests[0].requestId)
  expect(requests[1].tradeAt).toBe(requests[0].tradeAt)
})

test('desktop travel tabs expose itinerary actions and save expenses directly', async ({
  page,
}, info) => {
  await setupDesktop(page)
  const date = '2026-11-10'
  const detail: TravelPlanDetail = {
    id: 1,
    userId: 7,
    name: '杭州周末游',
    destination: '杭州',
    startDate: date,
    endDate: date,
    status: 'planning',
    sortOrder: 0,
    createdAt: date,
    updatedAt: date,
    overview: {
      companionCount: 0,
      travelerCount: 1,
      dayCount: 1,
      itineraryCount: 1,
      expenseCount: 0,
      totalExpenseAmount: 0,
      perPersonExpenseAmount: 0,
    },
    companions: [],
    expenses: [],
    days: [
      {
        id: 11,
        travelPlanId: 1,
        dayIndex: 1,
        travelDate: date,
        sortOrder: 0,
        createdAt: date,
        updatedAt: date,
        expenses: [],
        itineraries: [
          {
            id: 21,
            travelPlanDayId: 11,
            type: 'scenic',
            title: '西湖散步',
            startTime: '09:00',
            remark: '沿湖步行',
            sortOrder: 0,
            createdAt: date,
            updatedAt: date,
          },
        ],
      },
    ],
  }
  await page.route('**/api/tools/travel-plans/**', async (route) => {
    if (route.request().method() === 'POST') {
      const data = route.request().postDataJSON()
      const saved = {
        ...data,
        id: 31,
        travelPlanId: 1,
        travelPlanDayId: 11,
        createdAt: date,
        updatedAt: date,
      }
      detail.days[0].expenses.push(saved)
      detail.expenses.push(saved)
      detail.overview.totalExpenseAmount = data.amount
      return ok(route, saved)
    }
    return ok(route, detail)
  })
  await page.goto('/tools/travel-plans/1')
  await expect(page.getByRole('heading', { name: '杭州周末游' })).toBeVisible()
  await page.getByRole('tab', { name: '行程安排' }).click()
  const itinerary = page.getByRole('row').filter({ hasText: '西湖散步' })
  await expect(
    itinerary.getByRole('button', { name: '修改', exact: true }),
  ).toBeVisible()
  await page.getByRole('tab', { name: '费用清单' }).click()
  await page.getByRole('button', { name: '添加费用', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('搜索当天行程作为费用名称').fill('午餐费用')
  await dialog.getByLabel('费用金额', { exact: true }).fill('88')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(
    page.getByRole('row').filter({ hasText: '午餐费用' }),
  ).toContainText('88')
  expect(detail.days[0].expenses[0]).toMatchObject({
    userId: 7,
    title: '午餐费用',
    amount: 88,
  })
  await page.screenshot({
    path: info.outputPath('travel-1440.png'),
    animations: 'disabled',
  })
})
