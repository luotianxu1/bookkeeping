import { mount, flushPromises } from '@vue/test-utils'
import { computed, defineComponent, h, ref } from 'vue'
import { expect, test, vi } from 'vitest'
import { getInvestmentTransactionPage, type InvestmentTransactionPage } from '../src/api/modules/finance'
import { useTransactionPagination } from '../src/pages/finance/InvestmentDetailPage/useTransactionPagination'

vi.mock('../src/api/modules/finance', () => ({ getInvestmentTransactionPage: vi.fn() }))
vi.mock('../src/utils/current-user', () => ({ getStoredCurrentUser: () => ({ id: 1 }) }))

const page = (number: number): InvestmentTransactionPage => ({ items: [], total: 30, page: number, pageSize: 10, totalPages: 3 })

test('reload and route change cancel pending pages and ignore late responses', async () => {
  let resolveOld!: (value: InvestmentTransactionPage) => void
  const old = new Promise<InvestmentTransactionPage>(resolve => { resolveOld = resolve })
  vi.mocked(getInvestmentTransactionPage).mockResolvedValueOnce(page(1)).mockReturnValueOnce(old).mockResolvedValueOnce(page(1))
  let pagination!: ReturnType<typeof useTransactionPagination>
  const id = ref(1)
  const wrapper = mount(defineComponent({ setup() {
    pagination = useTransactionPagination({ positionId: computed(() => id.value) })
    return () => h('div', String(pagination.currentTransactionPage.value))
  } }))
  await pagination.loadTransactionPage(1)
  void pagination.loadTransactionPage(2)
  const oldSignal = vi.mocked(getInvestmentTransactionPage).mock.calls[1][1]!.signal!
  pagination.resetTransactionPage()
  await pagination.loadTransactionPage(1)
  resolveOld(page(2)); await flushPromises()
  expect(oldSignal.aborted).toBe(true)
  expect(pagination.currentTransactionPage.value).toBe(1)
  id.value = 2
  expect(pagination.transactionTotal.value).toBe(0)
  wrapper.unmount()
})
