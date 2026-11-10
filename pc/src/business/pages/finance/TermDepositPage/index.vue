<script setup lang="ts">
import {
  formatPercentage,
  formatPercentageText,
} from '@/business/utils/percentage'

import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'

import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import SavingsAllocationFields, {
  type SavingsAllocationDraft,
} from '../ExpenseEntryPage/components/SavingsAllocationFields.vue'
import {
  getAccounts,
  getAccountTypes,
  createAccount,
  getCategories,
  getSavingsGoals,
  getTermDeposits,
  getTermDeposit,
  createTermDeposit,
  updateTermDeposit,
  settleTermDeposit,
  reverseTermDeposit,
  cancelTermDeposit,
  type Account,
  type AccountType,
  type Category,
  type SavingsGoal,
  type TermDeposit,
  type TermDetail,
  type TermOperation,
  type TermAction,
} from '@/business/api/modules/finance'
import { getErrorMessage } from '@/business/api/request'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { useAsyncSection } from '@/business/composables/useAsyncSection'
import { useIdempotentSubmit } from '@/business/composables/useIdempotentSubmit'
import { useVisibilityPolling } from '@/business/composables/useVisibilityPolling'

const route = useRoute()
const deposits = ref<TermDeposit[]>([]),
  accounts = ref<Account[]>([]),
  types = ref<AccountType[]>([])
const categories = ref<Category[]>([]),
  goals = ref<SavingsGoal[]>([])
const incomeCategoryOptions = computed(() =>
  categories.value
    .filter((c) => !categories.value.some((child) => child.parentId === c.id))
    .map((c) => ({
      value: String(c.id),
      label: [
        categories.value.find((parent) => parent.id === c.parentId)?.name,
        c.name,
      ]
        .filter(Boolean)
        .join(' / '),
    })),
)
const defaultCategoryOptions = computed(() => [
  { value: '', label: '结算时选择' },
  ...incomeCategoryOptions.value,
])
const hasIncomeCategory = (id: string) =>
  incomeCategoryOptions.value.some((c) => c.value === id)
const filter = ref('all'),
  accountFilter = ref(String(route.query.accountId ?? ''))
const filters = [
  { value: 'all', label: '全部' },
  { value: 'active', label: '存续中' },
  { value: 'due', label: '待处理' },
  { value: 'closed', label: '已结清' },
]
const actionOptions = [
  { value: 'withdraw', label: '取回本息' },
  { value: 'renew_principal', label: '本金续存' },
  { value: 'renew_all', label: '本息续存' },
]
const monthOptions = [
  { value: '3', label: '3 个月' },
  { value: '6', label: '6 个月' },
  { value: '12', label: '1 年' },
  { value: '24', label: '2 年' },
  { value: '36', label: '3 年' },
  { value: '60', label: '5 年' },
  { value: '', label: '自定义日期' },
]
const termAccounts = computed(() =>
  accounts.value.filter((a) => a.accountTypeCode === 'term_deposit'),
)
const visible = computed(() =>
  deposits.value.filter(
    (d) =>
      (!accountFilter.value || d.accountId === Number(accountFilter.value)) &&
      (filter.value === 'all' || d.status === filter.value),
  ),
)
const list = useAsyncSection(
  getTermDeposits,
  (value) => {
    deposits.value = value
  },
  '存单加载失败',
)
const options = useAsyncSection(
  async (signal) => {
    const user = getStoredCurrentUser()
    if (!user) throw new Error('请先登录')
    return Promise.all([
      getAccounts({ userId: user.id, status: 'active' }, { signal }),
      getAccountTypes({ status: 'active' }),
      getCategories({ userId: user.id, type: 'income', status: 'active' }),
      getSavingsGoals(signal),
    ])
  },
  ([a, t, c, g]) => {
    accounts.value = a
    types.value = t
    categories.value = c
    goals.value = g
  },
  '账户和分类加载失败',
)
const busy = ref(false),
  modal = ref<'form' | 'settle' | 'history' | 'confirm' | ''>(''),
  error = ref('')
const opened = computed({
  get: () => !!modal.value,
  set: (v) => {
    if (!v && !busy.value && !accountSaving.value) modal.value = ''
  },
})
const editing = ref<TermDeposit | null>(null),
  selected = ref<TermDeposit | null>(null)
const submission = useIdempotentSubmit(),
  accountSaving = ref(false)
const title = computed(() =>
  modal.value === 'form'
    ? editing.value
      ? '修改存单'
      : '新增存单'
    : modal.value === 'settle'
      ? '支取与续存'
      : modal.value === 'history'
        ? '存单详情'
        : confirmation.value?.operationId
          ? '撤销结算'
          : '撤销存单',
)
function today() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(
    new Date(),
  )
}
function plusMonths(date: string, months: string) {
  if (!date || !months) return ''
  const [y, m, d] = date.split('-').map(Number)
  const value = new Date(Date.UTC(y, m - 1 + Number(months), 1))
  value.setUTCDate(
    Math.min(
      d,
      new Date(
        Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + 1, 0),
      ).getUTCDate(),
    ),
  )
  return value.toISOString().slice(0, 10)
}
function money(value: number, currency = 'CNY') {
  try {
    return new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency,
    }).format(value)
  } catch {
    return `${currency} ${Number(value).toFixed(2)}`
  }
}
function statusText(d: TermDeposit) {
  return {
    active: '存续中',
    due: '待处理',
    closed: '已结清',
    voided: '已撤销',
  }[d.status]
}
function isOpen(d: TermDeposit) {
  return d.status === 'active' || d.status === 'due'
}
const form = reactive({
  name: '',
  accountId: '',
  fundingAccountId: '',
  payoutAccountId: '',
  interestCategoryId: '',
  principal: '',
  annualRate: '',
  startDate: today(),
  maturityDate: '',
  months: '12',
  maturityAction: 'withdraw' as TermAction,
  openingMode: 'new' as 'new' | 'existing',
  remark: '',
})
const allocations = ref<SavingsAllocationDraft[]>([])
const newAccount = ref(false),
  accountName = ref(''),
  accountCurrency = ref('CNY')
const currency = computed(
  () =>
    accounts.value.find((a) => a.id === Number(form.accountId))?.currencyCode ??
    'CNY',
)
const cashAccounts = (code: string) =>
  accounts.value.filter(
    (a) => a.accountTypeCode === 'cash' && a.currencyCode === code,
  )
const cashOptions = (code: string) =>
  cashAccounts(code).map((a) => ({
    value: String(a.id),
    label: `${a.name} · ${money(a.currentBalance, code)}`,
  }))
const depositAccountOptions = computed(() =>
  termAccounts.value.map((a) => ({
    value: String(a.id),
    label: `${a.name} · ${a.currencyCode}`,
  })),
)
const goalOptions = computed(() =>
  goals.value.filter(
    (g) => g.accountId === Number(form.fundingAccountId) && g.currentAmount > 0,
  ),
)
watch(
  () => [form.startDate, form.months],
  () => {
    if (form.months) form.maturityDate = plusMonths(form.startDate, form.months)
  },
)
watch(
  () => form.fundingAccountId,
  () => {
    allocations.value = []
  },
)
watch(
  () => form.accountId,
  () => {
    if (
      !cashAccounts(currency.value).some(
        (a) => a.id === Number(form.fundingAccountId),
      )
    )
      form.fundingAccountId = ''
    if (
      !cashAccounts(currency.value).some(
        (a) => a.id === Number(form.payoutAccountId),
      )
    )
      form.payoutAccountId = ''
  },
)
const estimate = computed(() => {
  const days =
    (Date.parse(form.maturityDate) - Date.parse(form.startDate)) / 86400000
  const years =
    form.months && plusMonths(form.startDate, form.months) === form.maturityDate
      ? Number(form.months) / 12
      : days / 365
  return ((Number(form.principal) * Number(form.annualRate)) / 100) * years
})
function begin(mode: typeof modal.value) {
  error.value = ''
  submission.reset()
  modal.value = mode
}
async function openForm(d: TermDeposit | null = null) {
  editing.value = d
  Object.assign(form, {
    name: d?.name ?? '',
    accountId: d
      ? String(d.accountId)
      : accountFilter.value || String(termAccounts.value[0]?.id ?? ''),
    fundingAccountId: d?.fundingAccountId ? String(d.fundingAccountId) : '',
    payoutAccountId: d ? String(d.payoutAccountId) : '',
    interestCategoryId: d?.interestCategoryId
      ? String(d.interestCategoryId)
      : '',
    principal: d ? String(d.principal) : '',
    annualRate: d ? String(d.annualRate) : '',
    startDate: d?.startDate ?? today(),
    maturityDate: d?.maturityDate ?? plusMonths(today(), '12'),
    months: d?.termMonths ? String(d.termMonths) : d ? '' : '12',
    maturityAction: d?.maturityAction ?? 'withdraw',
    openingMode: d?.openingMode === 'existing' ? 'existing' : 'new',
    remark: d?.remark ?? '',
  })
  allocations.value = []
  newAccount.value = false
  begin('form')
  await options.run()
  if (modal.value !== 'form' || editing.value?.id !== d?.id) return
  if (!hasIncomeCategory(form.interestCategoryId)) form.interestCategoryId = ''
  if (!d && !form.accountId)
    form.accountId = String(termAccounts.value[0]?.id ?? '')
}
async function addAccount() {
  if (accountSaving.value || !accountName.value.trim()) return
  const type = types.value.find((t) => t.code === 'term_deposit'),
    user = getStoredCurrentUser()
  if (!type || !user) {
    error.value = '定期账户类型不可用，请检查账户类型配置'
    return
  }
  accountSaving.value = true
  error.value = ''
  try {
    const a = await createAccount({
      userId: user.id,
      accountTypeId: type.id,
      name: accountName.value.trim(),
      currencyCode: accountCurrency.value,
      currentBalance: 0,
      includeInNetWorth: true,
      status: 'active',
    })
    accounts.value.push(a)
    form.accountId = String(a.id)
    newAccount.value = false
    accountName.value = ''
  } catch (cause) {
    error.value = getErrorMessage(cause, '账户创建失败')
  } finally {
    accountSaving.value = false
  }
}
function validMoney(v: string, zero = false) {
  return (
    /^\d{1,16}(\.\d{1,2})?$/.test(v.trim()) &&
    (zero ? Number(v) >= 0 : Number(v) > 0)
  )
}
function validRate(v: string) {
  return /^\d{1,3}(\.\d{1,4})?$/.test(v.trim()) && Number(v) <= 100
}
async function save() {
  if (busy.value || accountSaving.value) return
  error.value = ''
  if (!form.name.trim() || form.name.trim().length > 80) {
    error.value = '请输入不超过80字的存单名称'
    return
  }
  if (
    !form.accountId ||
    !form.payoutAccountId ||
    (!editing.value && form.openingMode === 'new' && !form.fundingAccountId)
  ) {
    error.value = '请选择定期账户和现金账户'
    return
  }
  if (form.interestCategoryId && !hasIncomeCategory(form.interestCategoryId)) {
    error.value = '请重新选择有效的利息收入分类'
    return
  }
  if (!validMoney(form.principal) || !validRate(form.annualRate)) {
    error.value =
      '本金须大于0且最多两位小数，年利率须在0至100之间且最多四位小数'
    return
  }
  if (
    !form.startDate ||
    form.startDate > today() ||
    form.maturityDate <= form.startDate
  ) {
    error.value = '起息日不能晚于今天，到期日须晚于起息日'
    return
  }
  if (
    form.openingMode === 'new' &&
    allocations.value.some((a) => !a.goalId || !validMoney(a.amount))
  ) {
    error.value = '请输入有效的目标扣减金额'
    return
  }
  const terms = {
    name: form.name.trim(),
    payoutAccountId: Number(form.payoutAccountId),
    annualRate: Number(form.annualRate),
    startDate: form.startDate,
    interestCategoryId: form.interestCategoryId
      ? Number(form.interestCategoryId)
      : null,
    maturityDate: form.maturityDate,
    termMonths:
      form.months &&
      plusMonths(form.startDate, form.months) === form.maturityDate
        ? Number(form.months)
        : null,
    maturityAction: form.maturityAction,
    remark: form.remark.trim() || null,
  }
  busy.value = true
  try {
    if (editing.value)
      await updateTermDeposit(editing.value.id, submission.withRequestId(terms))
    else
      await createTermDeposit(
        submission.withRequestId({
          ...terms,
          accountId: Number(form.accountId),
          principal: Number(form.principal),
          fundingAccountId:
            form.openingMode === 'new' ? Number(form.fundingAccountId) : null,
          openingMode: form.openingMode,
          savingsAllocations:
            form.openingMode === 'new'
              ? allocations.value.map((a) => ({
                  goalId: a.goalId!,
                  amount: Number(a.amount),
                }))
              : [],
        }),
      )
    modal.value = ''
    submission.reset()
    await refresh()
  } catch (cause) {
    error.value = getErrorMessage(cause, '存单保存失败')
  } finally {
    busy.value = false
  }
}
const settlement = reactive({
  action: 'withdraw' as TermAction,
  payoutAccountId: '',
  interestCategoryId: '',
  actualInterest: '',
  occurredOn: today(),
  annualRate: '',
  months: '12',
  maturityDate: '',
  remark: '',
})
watch(
  () => [settlement.occurredOn, settlement.months],
  () => {
    if (settlement.months)
      settlement.maturityDate = plusMonths(
        settlement.occurredOn,
        settlement.months,
      )
  },
)
const early = computed(
  () => !!selected.value && settlement.occurredOn < selected.value.maturityDate,
)
async function openSettle(d: TermDeposit) {
  selected.value = d
  Object.assign(settlement, {
    action: d.status === 'due' ? d.maturityAction : 'withdraw',
    payoutAccountId: String(d.payoutAccountId),
    interestCategoryId: d.interestCategoryId
      ? String(d.interestCategoryId)
      : '',
    actualInterest: d.status === 'due' ? String(d.expectedInterest) : '0',
    occurredOn: today(),
    annualRate: String(d.annualRate),
    months:
      d.termMonths && monthOptions.some((m) => m.value === String(d.termMonths))
        ? String(d.termMonths)
        : '12',
    remark: '',
  })
  settlement.maturityDate = plusMonths(settlement.occurredOn, settlement.months)
  begin('settle')
  await options.run()
  if (
    modal.value === 'settle' &&
    selected.value?.id === d.id &&
    !hasIncomeCategory(settlement.interestCategoryId)
  )
    settlement.interestCategoryId = ''
}
async function settle() {
  if (busy.value || !selected.value) return
  error.value = ''
  if (
    !validMoney(settlement.actualInterest, true) ||
    !settlement.payoutAccountId
  ) {
    error.value = '请选择到账账户并填写实际利息'
    return
  }
  if (
    (Number(settlement.actualInterest) > 0 || settlement.interestCategoryId) &&
    !hasIncomeCategory(settlement.interestCategoryId)
  ) {
    error.value = '请选择利息收入分类'
    return
  }
  if (
    !settlement.occurredOn ||
    settlement.occurredOn < selected.value.startDate ||
    settlement.occurredOn > today()
  ) {
    error.value = '到账日期须在起息日至今天之间'
    return
  }
  const renew = settlement.action !== 'withdraw'
  if (
    renew &&
    (early.value ||
      !validRate(settlement.annualRate) ||
      settlement.maturityDate <= settlement.occurredOn)
  ) {
    error.value = '到期后才能续存，请填写新利率和有效到期日'
    return
  }
  busy.value = true
  try {
    await settleTermDeposit(
      selected.value.id,
      submission.withRequestId({
        action: settlement.action,
        payoutAccountId: Number(settlement.payoutAccountId),
        actualInterest: Number(settlement.actualInterest),
        interestCategoryId: settlement.interestCategoryId
          ? Number(settlement.interestCategoryId)
          : null,
        occurredOn: settlement.occurredOn,
        renewalAnnualRate: renew ? Number(settlement.annualRate) : null,
        renewalMaturityDate: renew ? settlement.maturityDate : null,
        renewalTermMonths:
          renew &&
          settlement.months &&
          plusMonths(settlement.occurredOn, settlement.months) ===
            settlement.maturityDate
            ? Number(settlement.months)
            : null,
        remark: settlement.remark.trim() || null,
      }),
    )
    modal.value = ''
    submission.reset()
    await refresh()
  } catch (cause) {
    error.value = getErrorMessage(cause, '结算失败')
  } finally {
    busy.value = false
  }
}
const detailId = ref(0),
  detail = ref<TermDetail | null>(null)
const history = useAsyncSection(
  (signal) => getTermDeposit(detailId.value, signal),
  (value) => {
    detail.value = value
  },
  '详情加载失败',
)
function openHistory(id: number) {
  history.reset()
  detailId.value = id
  detail.value = null
  begin('history')
  void history.run()
}
const operationLabels: Record<string, string> = {
  opening: '补录存单',
  deposit: '存入定期',
  edit: '修改资料',
  withdraw: '到期支取',
  early_withdraw: '提前支取',
  renew_principal: '本金续存',
  renew_all: '本息续存',
  renewal_open: '续存登记',
  cancel: '撤销存单',
  reverse: '撤销结算',
}
function reversible(op: TermOperation) {
  return (
    detail.value?.deposit.status === 'closed' &&
    !op.reversedAt &&
    ['withdraw', 'early_withdraw', 'renew_principal', 'renew_all'].includes(
      op.operation,
    )
  )
}
const confirmation = ref<{ deposit: TermDeposit; operationId?: number } | null>(
  null,
)
function confirm(d: TermDeposit, operationId?: number) {
  confirmation.value = { deposit: d, operationId }
  begin('confirm')
}
async function undo() {
  if (busy.value || !confirmation.value) return
  busy.value = true
  error.value = ''
  try {
    const { deposit, operationId } = confirmation.value
    if (operationId)
      await reverseTermDeposit(
        deposit.id,
        submission.withRequestId({ operationId }),
      )
    else await cancelTermDeposit(deposit.id, submission.withRequestId({}))
    modal.value = ''
    submission.reset()
    await refresh()
  } catch (cause) {
    error.value = getErrorMessage(cause, '撤销失败')
  } finally {
    busy.value = false
  }
}
async function refresh() {
  await Promise.all([list.run(), options.run()])
}
let openedReminder = ''
watch(
  () => [route.query.reminderId, list.ready.value],
  () => {
    const id = String(route.query.reminderId ?? '')
    if (list.ready.value && /^\d+$/.test(id) && openedReminder !== id) {
      openedReminder = id
      openHistory(Number(id))
    }
  },
)
watch(
  () => route.query.accountId,
  (v) => {
    accountFilter.value = String(v ?? '')
  },
)
onMounted(refresh)
useVisibilityPolling(
  () => (modal.value ? Promise.resolve() : list.run()),
  60_000,
)

type DesktopRow = (typeof visible.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="定期存款">
      <template #right>
        <el-button :loading="list.loading.value" @click="refresh">
          刷新
        </el-button>
        <el-button type="primary" @click="openForm()">新增存单</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="list.error.value"
      :title="list.error.value"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopTable
      title="存单列表"
      searchable
      search-placeholder="名称 / 账户"
      :rows="visible"
      :loading="list.loading.value"
    >
      <template #toolbar>
        <el-select v-model="accountFilter" placeholder="全部账户">
          <el-option label="全部账户" value="" />
          <el-option
            v-for="a in termAccounts"
            :key="a.id"
            :label="a.name"
            :value="String(a.id)"
          />
        </el-select>
        <SegmentedControl
          v-model="filter"
          :options="filters"
          label="存单状态"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="存单名称"
        prop="name"
        sortable="custom"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="账户"
        prop="accountName"
        sortable="custom"
        min-width="150"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="本金" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.principal, row.currencyCode) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="年利率" min-width="100">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatPercentage(row.annualRate) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="起息日"
        prop="startDate"
        sortable="custom"
        min-width="130"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="到期日"
        prop="maturityDate"
        sortable="custom"
        min-width="130"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="预计利息" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.expectedInterest, row.currencyCode) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }: { row: DesktopRow }">
          <el-tag :type="row.status === 'due' ? 'warning' : 'info'">
            {{ statusText(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openHistory(row.id)">
              详情
            </el-button>
            <el-button
              link
              type="primary"
              v-if="isOpen(row)"
              @click="openForm(row)"
            >
              修改
            </el-button>
            <el-button
              link
              type="primary"
              v-if="isOpen(row)"
              @click="openSettle(row)"
            >
              支取 / 续存
            </el-button>
            <el-button
              link
              type="danger"
              v-if="isOpen(row)"
              @click="confirm(row)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      v-model="opened"
      :title="title"
      scroll-body
      :close-on-overlay="!busy && !accountSaving"
      :show-close="!busy && !accountSaving"
    >
      <div class="pc-form-grid term-form">
        <template v-if="modal === 'form'">
          <CommonInput
            v-model="form.name"
            label="存单名称"
            placeholder="例如：招商银行一年定期"
            :disabled="busy"
          />
          <CommonSelect
            v-model="form.accountId"
            label="定期账户"
            :options="depositAccountOptions"
            :disabled="busy || !!editing"
          />
          <el-button
            v-if="!editing"
            class="text-button"
            :disabled="busy || accountSaving"
            @click="newAccount = !newAccount"
          >
            {{ newAccount ? '收起新增账户' : '＋ 新增定期账户' }}
          </el-button>
          <div v-if="newAccount" class="inline-account">
            <CommonInput
              v-model="accountName"
              label="账户名称"
              :disabled="accountSaving"
            />
            <CommonSelect
              v-model="accountCurrency"
              label="账户币种"
              :options="[
                ...new Set(['CNY', ...accounts.map((a) => a.currencyCode)]),
              ]"
              :disabled="accountSaving"
            />
            <CommonButton
              variant="secondary"
              :disabled="accountSaving || !accountName.trim()"
              @click="addAccount"
            >
              {{ accountSaving ? '创建中...' : '创建账户' }}
            </CommonButton>
          </div>
          <CommonSelect
            v-if="!editing"
            v-model="form.openingMode"
            label="登记方式"
            :options="[
              { value: 'new', label: '现在存入' },
              { value: 'existing', label: '补录已有存单' },
            ]"
            :disabled="busy"
          />
          <p v-if="!editing && form.openingMode === 'existing'" class="hint">
            补录只增加定期资产，不扣现金、不记收入。请确认现金账户余额已扣除这笔本金，避免重复计算。
          </p>
          <CommonSelect
            v-if="!editing && form.openingMode === 'new'"
            v-model="form.fundingAccountId"
            label="转出现金账户"
            :options="cashOptions(currency)"
            :disabled="busy"
          />
          <div class="field-pair">
            <CommonInput
              v-model="form.principal"
              :label="`本金（${currency}）`"
              input-mode="decimal"
              :disabled="busy || !!editing"
            />
            <CommonInput
              percentage
              v-model="form.annualRate"
              label="年利率（%）"
              input-mode="decimal"
              :disabled="busy || (!!editing && !isOpen(editing))"
            />
          </div>
          <div class="field-pair">
            <CommonInput
              v-model="form.startDate"
              label="起息日"
              input-type="date"
              :disabled="busy || !!editing"
            />
            <CommonSelect
              v-model="form.months"
              label="存期"
              :options="monthOptions"
              :disabled="busy || (!!editing && !isOpen(editing))"
            />
          </div>
          <CommonInput
            v-model="form.maturityDate"
            label="到期日"
            input-type="date"
            :disabled="busy || (!!editing && !isOpen(editing))"
          />
          <p v-if="Number.isFinite(estimate) && estimate >= 0" class="hint">
            预计利息 {{ money(estimate, currency) }}，不计入资产；整月按月数 /
            12、自定义日期按实际天数 / 365 估算，到账以银行结算为准。
          </p>
          <CommonSelect
            v-model="form.payoutAccountId"
            label="默认到账账户"
            :options="cashOptions(currency)"
            :disabled="busy"
          />
          <CommonSelect
            v-model="form.interestCategoryId"
            label="默认利息收入分类"
            :options="defaultCategoryOptions"
            :disabled="busy"
          />
          <CommonSelect
            v-model="form.maturityAction"
            label="到期意向"
            :options="actionOptions"
            :disabled="busy"
          />
          <p class="hint">
            到期仅提醒，取回或续存需手动确认。更正本金或起息日请撤销后重新登记。
          </p>
          <SavingsAllocationFields
            v-if="!editing && form.openingMode === 'new'"
            v-model="allocations"
            :goals="goalOptions"
            label="扣减储蓄目标"
            :disabled="busy"
          />
          <CommonInput
            v-model="form.remark"
            label="备注（可选）"
            :disabled="busy"
          />
        </template>
        <template v-else-if="modal === 'settle' && selected">
          <p>
            {{ selected.name }} · 本金
            {{ money(selected.principal, selected.currencyCode) }}
          </p>
          <CommonSelect
            v-model="settlement.action"
            label="处理方式"
            :options="
              actionOptions.map((o) => ({
                ...o,
                disabled: early && o.value !== 'withdraw',
              }))
            "
            :disabled="busy"
          />
          <CommonInput
            v-model="settlement.occurredOn"
            label="实际到账日"
            input-type="date"
            :disabled="busy"
          />
          <p v-if="early" class="hint">
            本次为整笔提前支取，请填写银行实际结算的利息。
          </p>
          <CommonInput
            v-model="settlement.actualInterest"
            :label="`实际利息（${selected.currencyCode}）`"
            input-mode="decimal"
            :disabled="busy"
          />
          <p v-if="!early" class="hint">
            预计利息
            {{
              money(selected.expectedInterest, selected.currencyCode)
            }}，请核对后填写实际到账金额。
          </p>
          <CommonSelect
            v-model="settlement.payoutAccountId"
            label="到账现金账户"
            :options="cashOptions(selected.currencyCode)"
            :disabled="busy"
          />
          <CommonSelect
            v-model="settlement.interestCategoryId"
            label="利息收入分类"
            :options="[
              {
                value: '',
                label:
                  Number(settlement.actualInterest) > 0
                    ? '请选择收入分类'
                    : '无利息可不选',
              },
              ...incomeCategoryOptions,
            ]"
            :disabled="busy"
          />
          <template v-if="settlement.action !== 'withdraw'">
            <p class="hint">
              新存单本金
              {{
                money(
                  Number(selected.principal) +
                    (settlement.action === 'renew_all'
                      ? Number(settlement.actualInterest) || 0
                      : 0),
                  selected.currencyCode,
                )
              }}。{{
                settlement.action === 'renew_all'
                  ? '本息全部续存。'
                  : '利息留在到账现金账户。'
              }}
            </p>
            <div class="field-pair">
              <CommonInput
                percentage
                v-model="settlement.annualRate"
                label="新年利率（%）"
                input-mode="decimal"
                :disabled="busy"
              />
              <CommonSelect
                v-model="settlement.months"
                label="新存期"
                :options="monthOptions"
                :disabled="busy"
              />
            </div>
            <CommonInput
              v-model="settlement.maturityDate"
              label="新到期日"
              input-type="date"
              :disabled="busy"
            />
          </template>
          <CommonInput
            v-model="settlement.remark"
            label="结算备注（可选）"
            :disabled="busy"
          />
        </template>
        <template v-else-if="modal === 'history'">
          <CommonLoading v-if="history.loading.value" />
          <p v-if="history.error.value" class="error" role="alert">
            {{ history.error.value }}
            <el-button @click="history.run">重试</el-button>
          </p>
          <template v-if="detail">
            <h3>
              {{ detail.deposit.name }}
              <span class="status">{{ statusText(detail.deposit) }}</span>
            </h3>
            <p>
              {{ detail.deposit.accountName }} · 本金
              {{ money(detail.deposit.principal, detail.deposit.currencyCode) }}
            </p>
            <p class="hint">
              {{ detail.deposit.startDate }} →
              {{ detail.deposit.maturityDate }} · 年利率
              {{ formatPercentage(detail.deposit.annualRate) }}
            </p>
            <p class="hint">
              预计本息
              {{
                money(detail.deposit.expectedTotal, detail.deposit.currencyCode)
              }}
              ·
              {{
                actionOptions.find(
                  (a) => a.value === detail?.deposit.maturityAction,
                )?.label
              }}
            </p>
            <p v-if="detail.deposit.interestCategoryName" class="hint">
              默认利息分类 {{ detail.deposit.interestCategoryName }}
            </p>
            <p v-if="detail.deposit.remark" class="hint">
              {{ detail.deposit.remark }}
            </p>
            <el-button
              v-if="detail.deposit.parentDepositId"
              class="text-button"
              @click="openHistory(detail.deposit.parentDepositId)"
            >
              查看原存单（撤销续存在此操作） →
            </el-button>
            <div
              v-for="op in detail.operations"
              :key="op.id"
              class="history-row"
            >
              <div class="history-heading">
                <strong>
                  {{ operationLabels[op.operation] ?? op.operation }}
                </strong>
                <time>{{ op.occurredOn }}</time>
                <span v-if="op.reversedAt" class="hint">已撤销</span>
              </div>
              <p
                v-if="
                  [
                    'withdraw',
                    'early_withdraw',
                    'renew_all',
                    'renew_principal',
                  ].includes(op.operation)
                "
              >
                实际利息 {{ money(op.interest, detail.deposit.currencyCode) }}
              </p>
              <p v-if="op.interestCategoryName" class="hint">
                收入分类 {{ op.interestCategoryName }}
              </p>
              <p v-if="op.cashAccountName" class="hint">
                {{ op.cashAccountName }} · 现金变动
                {{ money(op.cashDelta, detail.deposit.currencyCode) }}
              </p>
              <p v-if="op.detail" class="hint">
                {{ formatPercentageText(op.detail) }}
              </p>
              <div class="pc-toolbar history-actions">
                <el-button
                  v-if="op.childDepositId"
                  class="text-button"
                  @click="openHistory(op.childDepositId)"
                >
                  查看续存存单 →
                </el-button>
                <el-button
                  v-if="reversible(op)"
                  class="text-button danger"
                  @click="confirm(detail.deposit, op.id)"
                  type="danger"
                  plain
                >
                  撤销结算
                </el-button>
              </div>
            </div>
          </template>
        </template>
        <template v-else-if="modal === 'confirm' && confirmation">
          <p>{{ confirmation.deposit.name }}</p>
          <p v-if="confirmation.operationId" class="hint">
            撤销后恢复原存单，收回本次到账现金及利息，并撤销续存生成的新存单。新存单有后续结算时，请先撤销后续结算；现金余额需足够且覆盖储蓄目标预留。
          </p>
          <p v-else class="hint">
            撤销这笔存单及其存入流水；现在存入的本金将退回原现金账户，并恢复当时扣减的储蓄目标。补录存单不会改变现金余额。
          </p>
        </template>
        <template v-if="modal === 'form' || modal === 'settle'">
          <p v-if="options.error.value" class="error" role="alert">
            {{ options.error.value }}
            <el-button :disabled="busy" @click="options.run">重试</el-button>
          </p>
          <p
            v-if="
              options.ready.value &&
              !accounts.some((a) => a.accountTypeCode === 'cash')
            "
            class="hint"
          >
            请先到
            <RouterLink to="/finance/accounts/cash" @click="modal = ''">
              现金账户
            </RouterLink>
            创建到账账户。
          </p>
          <p
            v-if="options.ready.value && !incomeCategoryOptions.length"
            class="hint"
          >
            暂无可用的收入分类，请到
            <RouterLink to="/finance/categories" @click="modal = ''">
              分类管理
            </RouterLink>
            添加或启用收入分类。
          </p>
        </template>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="busy || accountSaving"
            @click="modal = ''"
          >
            {{ modal === 'history' ? '关闭' : '取消' }}
          </CommonButton>
          <CommonButton
            v-if="modal !== 'history'"
            variant="primary"
            :disabled="
              busy ||
              accountSaving ||
              ((modal === 'form' || modal === 'settle') &&
                (!options.ready.value ||
                  options.loading.value ||
                  !!options.error.value))
            "
            @click="
              modal === 'form' ? save() : modal === 'settle' ? settle() : undo()
            "
          >
            {{
              busy
                ? '处理中...'
                : modal === 'form'
                  ? '保存'
                  : modal === 'settle'
                    ? '确认结算'
                    : '确认撤销'
            }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
