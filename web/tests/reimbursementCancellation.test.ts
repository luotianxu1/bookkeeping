import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import RecoveryPage from '@/pages/finance/ExpenseRecoveryPage/index.vue'
import { getExpenseRecoveries, getExpenseRecovery, getAccountTypes, saveReimbursementPlan,
  deleteTransaction, receiveExpenseRecovery, type ExpenseRecovery } from '@/api/modules/finance'

vi.mock('@/api/modules/finance', () => ({
  getExpenseRecoveries: vi.fn(), getExpenseRecovery: vi.fn(), getAccountTypes: vi.fn(), getAccounts: vi.fn(),
  saveReimbursementPlan: vi.fn(), deleteTransaction: vi.fn(), receiveExpenseRecovery: vi.fn(),
}))
vi.mock('@/utils/current-user', () => ({ getStoredCurrentUser: () => ({ id: 1 }) }))
vi.mock('@/api/request', () => ({ getErrorMessage: (error: Error) => error.message }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { transactionId: '1' } }), useRouter: () => ({ replace: vi.fn() }) }))

let expense: ExpenseRecovery
let wrapper: VueWrapper | undefined
beforeEach(() => {
  vi.resetAllMocks()
  expense = { id: 1, title: '出差车费', categoryName: '交通', accountName: '钱包', currencyCode: 'CNY',
    occurredAt: '2026-01-31T12:00:00', amount: 300, reimbursementAmount: 200, refundedAmount: 0,
    reimbursedAmount: 0, recoverableAmount: 100, pendingAmount: 200, reimbursementStatus: 'pending', receipts: [] }
  vi.mocked(getAccountTypes).mockResolvedValue([])
  vi.mocked(getExpenseRecovery).mockImplementation(async () => structuredClone(expense))
  vi.mocked(getExpenseRecoveries).mockImplementation(async ({ state }) => {
    const items = state === 'all' || state === expense.reimbursementStatus ? [structuredClone(expense)] : []
    return { items, page: 1, totalPages: 1, total: items.length }
  })
  vi.mocked(saveReimbursementPlan).mockImplementation(async (_id, amount) => {
    expense.reimbursementAmount = amount
    expense.pendingAmount = amount - expense.reimbursedAmount
    expense.recoverableAmount = expense.amount - expense.refundedAmount - amount
    expense.reimbursementStatus = amount === 0 ? 'none' : 'received'
    return structuredClone(expense)
  })
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined })

async function open() {
  wrapper = mount(RecoveryPage, { global: { stubs: { Teleport: true, PageHeader: true } } })
  await flushPromises()
}
async function click(label: string) {
  const button = wrapper!.findAll('button').find(b => b.text() === label)
  expect(button, `找不到按钮：${label}`).toBeDefined()
  await button!.trigger('click')
  await flushPromises()
}

describe('撤销待报销', () => {
  it('确认后取消计划、刷新待报销列表，并保留原支出', async () => {
    await open()
    await click('待报销')
    await click('撤销报销')
    expect(wrapper!.get('[role="dialog"]').text()).toContain('200.00')
    expect(saveReimbursementPlan).not.toHaveBeenCalled()
    await click('取消')
    expect(saveReimbursementPlan).not.toHaveBeenCalled()
    await click('撤销报销')
    await click('确认撤销')
    expect(saveReimbursementPlan).toHaveBeenCalledWith(1, 0)
    expect(wrapper!.findAll('.expense-card')).toHaveLength(0)
    expect(wrapper!.find('.feedback').exists()).toBe(false)
    expect(wrapper!.get('[role="dialog"]').text()).toContain('支出详情')
    expect(wrapper!.get('[role="dialog"]').text()).not.toContain('撤销报销')
    await click('全部支出')
    expect(wrapper!.get('.expense-card').text()).toContain('未申请报销')
    expect(deleteTransaction).not.toHaveBeenCalled()
    expect(receiveExpenseRecovery).not.toHaveBeenCalled()
  })

  it('部分到账仅撤销剩余金额，保留到账记录', async () => {
    expense.reimbursedAmount = 80; expense.pendingAmount = 120; expense.reimbursementStatus = 'partial'
    expense.receipts = [{ id: 10, kind: 'reimbursement', amount: 80, accountId: 2, accountName: '银行卡', occurredAt: '2026-02-01T12:00:00' }]
    await open()
    await click('撤销剩余报销')
    expect(wrapper!.get('[role="dialog"]').text()).toContain('120.00')
    expect(wrapper!.get('[role="dialog"]').text()).toContain('已到账的 ¥80.00 及到账记录会保留')
    await click('确认撤销')
    expect(saveReimbursementPlan).toHaveBeenCalledWith(1, 80)
    expect(wrapper!.get('.receipt').text()).toContain('80.00')
    expect(wrapper!.get('.expense-card').text()).toContain('待到账 ¥0.00')
    expect(deleteTransaction).not.toHaveBeenCalled()
  })

  it('撤销失败保留确认弹窗与待报销状态，可重试', async () => {
    vi.mocked(saveReimbursementPlan).mockRejectedValueOnce(new Error('撤销暂不可用'))
    await open()
    await click('撤销报销')
    await click('确认撤销')
    expect(wrapper!.get('[role="alert"]').text()).toBe('撤销暂不可用')
    expect(wrapper!.get('.expense-card').text()).toContain('待报销')
    expect(wrapper!.find('.feedback').exists()).toBe(false)
    await click('确认撤销')
    expect(wrapper!.find('.feedback').exists()).toBe(false)
  })

  it('没有待到账金额时不显示撤销计划入口', async () => {
    expense.reimbursedAmount = 200; expense.pendingAmount = 0; expense.reimbursementStatus = 'received'
    await open()
    expect(wrapper!.findAll('.detail-actions button').map(b => b.text())).toEqual(['报销计划', '登记退款', '报销到账'])
  })
})
