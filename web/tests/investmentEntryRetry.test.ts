import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { computed, effectScope, nextTick, ref } from 'vue'
import { createInvestmentPosition, createInvestmentTransaction, type Account, type InvestmentPosition } from '@/api/modules/finance'
import { useAddAsset } from '@/pages/finance/InvestmentAccountPage/useAddAsset'
import { useGoldPositionActions } from '@/pages/finance/GoldAccountPositionPage/useGoldPositionActions'

vi.mock('@/api/modules/finance', () => ({
  createInvestmentPosition: vi.fn(), createInvestmentTransaction: vi.fn(), getInvestmentProducts: vi.fn(),
  deleteInvestmentPosition: vi.fn(), getInvestmentPosition: vi.fn(), updateAccount: vi.fn(), updateInvestmentPosition: vi.fn(),
}))
vi.mock('@/utils/current-user', () => ({ getStoredCurrentUser: () => ({ id: 1 }) }))

let scope: ReturnType<typeof effectScope>
beforeEach(() => { vi.resetAllMocks(); vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-06T10:00:00')); scope = effectScope() })
afterEach(() => { scope.stop(); vi.useRealTimers() })

it.each(['stock', 'fund'] as const)('%s 建仓超时重试保持请求 ID 和时间，成功后新建一笔使用新 ID', async productType => {
  vi.mocked(createInvestmentPosition).mockRejectedValueOnce(new Error('network')).mockResolvedValue({ id: 7 } as InvestmentPosition)
  const form = scope.run(() => useAddAsset({ fundingAccounts: ref([]), getDefaultCategory: () => productType,
    getAccountId: () => 3, onSaved: vi.fn(), showFeedback: vi.fn() }))!
  form.addAssetCategory.value = productType
  await nextTick()
  form.addAssetName.value = '测试产品'; form.addAssetSymbol.value = 'TEST'; form.addAssetFundingAccount.value = '1'
  form.addAssetQuantity.value = '10'; form.addAssetAmount.value = '100'; form.addAssetCurrentPrice.value = '100'
  await form.saveAsset()
  const first = vi.mocked(createInvestmentPosition).mock.calls[0]![0]
  expect(first.requestId).toBeTruthy()
  expect(form.formError.value).toBe('network')
  vi.setSystemTime(new Date('2026-11-06T10:05:00'))
  await form.saveAsset()
  expect(vi.mocked(createInvestmentPosition).mock.calls[1]![0]).toEqual(first)
  await form.saveAsset()
  expect(vi.mocked(createInvestmentPosition).mock.calls[2]![0].requestId).not.toBe(first.requestId)
})

function goldForm() {
  return scope.run(() => useGoldPositionActions({
    goldAccounts: ref([{ id: 3 } as Account]), isScopedToAccount: computed(() => false), scopedAccount: computed(() => null),
    realtimeGoldPrice: computed(() => 100), formAccountId: ref('3'), formFundingAccountId: ref('1'),
    syncFormSelections: vi.fn(), loadGoldPosition: vi.fn().mockResolvedValue(undefined), showFeedback: vi.fn(),
  }))!
}

it('黄金建仓失败重试复用请求，修改数量生成新请求', async () => {
  vi.mocked(createInvestmentPosition).mockRejectedValue(new Error('network'))
  const form = goldForm()
  form.formWeight.value = '2'; form.formBuyPrice.value = '100'
  await form.savePosition()
  const first = vi.mocked(createInvestmentPosition).mock.calls[0]![0]
  expect(first.requestId).toBeTruthy()
  vi.setSystemTime(new Date('2026-11-06T10:05:00'))
  await form.savePosition()
  expect(vi.mocked(createInvestmentPosition).mock.calls[1]![0]).toEqual(first)
  form.formWeight.value = '3'
  await form.savePosition()
  expect(vi.mocked(createInvestmentPosition).mock.calls[2]![0].requestId).not.toBe(first.requestId)
})

it('黄金卖出响应丢失后保留时间和请求 ID，成功后重置', async () => {
  vi.mocked(createInvestmentTransaction).mockRejectedValueOnce(new Error('network')).mockResolvedValue({ id: 8 } as never)
  const form = goldForm()
  const position = { id: 7, accountId: 3, productId: 1, availableQuantity: 10, currencyCode: 'CNY' } as InvestmentPosition
  form.sellingPosition.value = position
  form.sellWeight.value = '2'; form.sellPrice.value = '100'; form.sellFundingAccountId.value = '1'
  await form.submitSellPosition()
  const first = vi.mocked(createInvestmentTransaction).mock.calls[0]![0]
  expect(first.requestId).toBeTruthy()
  vi.setSystemTime(new Date('2026-11-06T10:05:00'))
  await form.submitSellPosition()
  expect(vi.mocked(createInvestmentTransaction).mock.calls[1]![0]).toEqual(first)
  form.sellingPosition.value = position
  await form.submitSellPosition()
  expect(vi.mocked(createInvestmentTransaction).mock.calls[2]![0].requestId).not.toBe(first.requestId)
})
