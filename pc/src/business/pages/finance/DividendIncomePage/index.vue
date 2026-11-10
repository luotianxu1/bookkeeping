<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { formatAmount as formatNumberAmount } from '@/business/utils/format'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

import CommonButton from '@/business/components/common/CommonButton/index.vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import {
  createInvestmentFixedExpense,
  deleteInvestmentFixedExpense,
  getInvestmentFixedExpenses,
  getInvestmentDividendIncome,
  refreshInvestmentDividendIncome,
  getRenewalSubscriptions,
  type InvestmentFixedExpense,
  type InvestmentDividendIncomeItem,
  type InvestmentDividendIncomePage,
  type RenewalBillingCycle,
  type RenewalSubscription,
  updateInvestmentFixedExpense,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type FixedExpenseSource = 'investment' | 'renewal'

type FixedExpenseItem = InvestmentFixedExpense & {
  itemKey: string
  source: FixedExpenseSource
  editable: boolean
}

type ExpenseCoverageItem = FixedExpenseItem & {
  coveredAmount: number
  coveredPercent: number
  status: 'covered' | 'partial' | 'pending'
}

function formatAmount(value: number, digits = 2) {
  return formatNumberAmount(Math.abs(value), digits)
}

const router = useRouter()
const isLoading = ref(true)
const isRefreshing = ref(false)
const pageError = ref('')
const expenseSaving = ref(false)
const pageData = ref<InvestmentDividendIncomePage | null>(null)
const currentUserId = ref<number | null>(null)
const investmentExpenseItems = ref<FixedExpenseItem[]>([])
const renewalExpenseItems = ref<FixedExpenseItem[]>([])

const showExpenseModal = ref(false)
const editingExpenseId = ref<number | null>(null)
const expenseName = ref('')
const expenseAmount = ref('')
const expenseError = ref('')

const summary = computed(
  () =>
    pageData.value?.summary ?? {
      estimatedDividendAmount: 0,
      estimatedDividendRate: 0,
      actualDividendAmount: 0,
      actualDividendRate: 0,
      holdingCount: 0,
    },
)

const holdings = computed(() => pageData.value?.items ?? [])

const estimatedMonthlyIncome = computed(
  () => summary.value.estimatedDividendAmount / 12,
)
const expenseModalTitle = computed(() =>
  editingExpenseId.value ? '修改固定支出' : '新增固定支出',
)
const expenseItems = computed<FixedExpenseItem[]>(() => [
  ...investmentExpenseItems.value,
  ...renewalExpenseItems.value,
])
const expenseCoverageItems = computed<ExpenseCoverageItem[]>(() => {
  let remaining = estimatedMonthlyIncome.value
  return expenseItems.value.map((item) => {
    const amount = Number(item.amount || 0)
    const coveredAmount = Math.max(0, Math.min(remaining, amount))
    const coveredPercent = amount > 0 ? (coveredAmount / amount) * 100 : 0
    const status =
      coveredAmount >= amount
        ? 'covered'
        : coveredAmount > 0
          ? 'partial'
          : 'pending'
    remaining = Math.max(0, remaining - amount)
    return {
      ...item,
      coveredAmount,
      coveredPercent,
      status,
    }
  })
})
const totalMonthlyExpense = computed(() =>
  expenseItems.value.reduce((total, item) => total + item.amount, 0),
)
const incomeCoveragePercent = computed(() => {
  if (totalMonthlyExpense.value <= 0) {
    return 0
  }
  return (estimatedMonthlyIncome.value / totalMonthlyExpense.value) * 100
})

onMounted(() => {
  void loadPageData()
})

async function loadPageData() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看攒股收息'
    isLoading.value = false
    return
  }

  currentUserId.value = currentUser.id
  isLoading.value = true
  pageError.value = ''
  try {
    const [dividendIncomeData, renewalSubscriptions] = await Promise.all([
      getInvestmentDividendIncome(currentUser.id),
      getRenewalSubscriptions({ userId: currentUser.id, status: 'active' }),
    ])
    pageData.value = dividendIncomeData
    investmentExpenseItems.value = normalizeInvestmentExpenseItems(
      dividendIncomeData.fixedExpenses,
    )
    renewalExpenseItems.value = normalizeRenewalExpenseItems(
      renewalSubscriptions,
      currentUser.id,
    )
  } catch (error) {
    pageError.value = getErrorMessage(error, '攒股收息加载失败')
  } finally {
    isLoading.value = false
  }
}

async function refreshDividendIncomeData() {
  if (isLoading.value || isRefreshing.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后刷新攒股收息'
    return
  }

  currentUserId.value = currentUser.id
  isRefreshing.value = true
  pageError.value = ''
  try {
    const [dividendIncomeData, renewalSubscriptions] = await Promise.all([
      refreshInvestmentDividendIncome(currentUser.id),
      getRenewalSubscriptions({ userId: currentUser.id, status: 'active' }),
    ])
    pageData.value = dividendIncomeData
    investmentExpenseItems.value = normalizeInvestmentExpenseItems(
      dividendIncomeData.fixedExpenses,
    )
    renewalExpenseItems.value = normalizeRenewalExpenseItems(
      renewalSubscriptions,
      currentUser.id,
    )
  } catch (error) {
    pageError.value = getErrorMessage(error, '攒股收息刷新失败')
  } finally {
    isRefreshing.value = false
  }
}

async function refreshExpenseItems() {
  if (!currentUserId.value) {
    investmentExpenseItems.value = []
    renewalExpenseItems.value = []
    return
  }
  const [investmentExpenses, renewalSubscriptions] = await Promise.all([
    getInvestmentFixedExpenses(currentUserId.value),
    getRenewalSubscriptions({ userId: currentUserId.value, status: 'active' }),
  ])
  investmentExpenseItems.value =
    normalizeInvestmentExpenseItems(investmentExpenses)
  renewalExpenseItems.value = normalizeRenewalExpenseItems(
    renewalSubscriptions,
    currentUserId.value,
  )
}

function formatSignedCurrency(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `${value > 0 ? '+' : '-'} ¥${formatAmount(value)}`
}

function formatSignedPercent(value: number) {
  return formatPercentage(value, true)
}

function formatPlainCurrency(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `${value < 0 ? '-' : ''}¥${formatAmount(value)}`
}

function formatPlainPercent(value: number) {
  return formatPercentage(value)
}

function formatHoldingQuantityText(quantity: number, unitName?: string | null) {
  return `${formatAmount(quantity)} ${unitName || '份'}`
}

function formatHoldingMetaText(
  value: number,
  quantity: number,
  unitName?: string | null,
) {
  return `${formatMonthlyIncome(value)} · ${formatHoldingQuantityText(quantity, unitName)}`
}

function formatMonthlyIncome(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `¥${formatAmount(value)}`
}

function openHoldingDetail(item: InvestmentDividendIncomeItem) {
  if (!item.positionId) {
    return
  }
  router.push({
    path: '/finance/accounts/investment/detail',
    query: { positionId: item.positionId },
  })
}

function normalizeInvestmentExpenseItems(
  items?: InvestmentFixedExpense[] | null,
) {
  if (!Array.isArray(items)) {
    return []
  }
  return items
    .map(
      (item): FixedExpenseItem => ({
        ...item,
        id: Number(item.id),
        userId: Number(item.userId),
        name: String(item.name ?? '').trim(),
        amount: Number(item.amount ?? 0),
        sortOrder: Number(item.sortOrder ?? 0),
        itemKey: `investment-${item.id}`,
        source: 'investment',
        editable: true,
      }),
    )
    .filter(
      (item) =>
        Number.isFinite(item.id) &&
        item.id > 0 &&
        item.name &&
        Number.isFinite(item.amount) &&
        item.amount > 0,
    )
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
}

function normalizeRenewalExpenseItems(
  items: RenewalSubscription[] | null | undefined,
  userId: number,
) {
  if (!Array.isArray(items)) {
    return []
  }
  return items
    .map(
      (item, index): FixedExpenseItem => ({
        id: Number(item.id),
        userId,
        name: String(item.name ?? '').trim(),
        amount: toMonthlyRenewalAmount(
          Number(item.amount ?? 0),
          item.billingCycle,
        ),
        currencyCode: item.currencyCode || 'CNY',
        sortOrder: 10000 + index,
        status: item.status,
        remark: item.remark ?? null,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
        itemKey: `renewal-${item.id}`,
        source: 'renewal',
        editable: false,
      }),
    )
    .filter(
      (item) =>
        Number.isFinite(item.id) &&
        item.id > 0 &&
        item.name &&
        Number.isFinite(item.amount) &&
        item.amount > 0,
    )
}

function toMonthlyRenewalAmount(
  amount: number,
  billingCycle: RenewalBillingCycle,
) {
  if (!Number.isFinite(amount) || amount <= 0) {
    return 0
  }
  if (billingCycle === 'quarterly') {
    return amount / 3
  }
  if (billingCycle === 'yearly') {
    return amount / 12
  }
  return amount
}

function openCreateExpenseModal() {
  editingExpenseId.value = null
  expenseName.value = ''
  expenseAmount.value = ''
  expenseError.value = ''
  showExpenseModal.value = true
}

function openEditExpenseModal(item: FixedExpenseItem) {
  if (!item.editable) {
    return
  }
  editingExpenseId.value = item.id
  expenseName.value = item.name
  expenseAmount.value = String(item.amount)
  expenseError.value = ''
  showExpenseModal.value = true
}

function closeExpenseModal() {
  showExpenseModal.value = false
  editingExpenseId.value = null
  expenseName.value = ''
  expenseAmount.value = ''
  expenseError.value = ''
}

function saveExpenseItem() {
  void submitExpenseItem()
}

async function submitExpenseItem() {
  const name = expenseName.value.trim()
  const amount = Number(expenseAmount.value)
  if (!name) {
    expenseError.value = '请输入固定支出名称'
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    expenseError.value = '请输入大于 0 的固定支出金额'
    return
  }
  if (!currentUserId.value) {
    expenseError.value = '用户信息缺失，请重新登录'
    return
  }
  if (expenseSaving.value) {
    return
  }

  expenseSaving.value = true
  expenseError.value = ''
  try {
    if (editingExpenseId.value) {
      const currentItem = investmentExpenseItems.value.find(
        (item) => item.id === editingExpenseId.value,
      )
      await updateInvestmentFixedExpense(editingExpenseId.value, {
        userId: currentUserId.value,
        name,
        amount,
        currencyCode: currentItem?.currencyCode || 'CNY',
        sortOrder: currentItem?.sortOrder ?? 0,
        remark: currentItem?.remark ?? null,
      })
    } else {
      await createInvestmentFixedExpense({
        userId: currentUserId.value,
        name,
        amount,
        currencyCode: 'CNY',
        remark: null,
      })
    }
    await refreshExpenseItems()
    closeExpenseModal()
  } catch (error) {
    expenseError.value = getErrorMessage(error, '保存固定支出失败')
  } finally {
    expenseSaving.value = false
  }
}

function getCoveragePercentText(item: ExpenseCoverageItem) {
  return formatPercentage(item.coveredPercent)
}

function getIncomeCoveragePercentText() {
  return formatPercentage(incomeCoveragePercent.value)
}

function deleteExpenseItem(id: number) {
  void removeExpenseItem(id)
}

async function removeExpenseItem(id: number) {
  if (!currentUserId.value) {
    return
  }
  try {
    await deleteInvestmentFixedExpense(id, currentUserId.value)
    await refreshExpenseItems()
  } catch (error) {
    pageError.value = getErrorMessage(error, '删除固定支出失败')
  }
}

type HoldingRow = (typeof holdings.value)[number]
type ExpenseRow = (typeof expenseCoverageItems.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="攒股收息">
      <template #right>
        <el-button type="primary" @click="openCreateExpenseModal">
          新增固定支出
        </el-button>
        <el-button
          :loading="isRefreshing"
          :disabled="isLoading"
          @click="refreshDividendIncomeData"
        >
          刷新
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        {
          label: '预计分红',
          value: formatSignedCurrency(summary.estimatedDividendAmount),
        },
        {
          label: '预计股息率',
          value: formatSignedPercent(summary.estimatedDividendRate),
        },
        {
          label: '预估月薪',
          value: formatMonthlyIncome(estimatedMonthlyIncome),
        },
        { label: '支出覆盖', value: getIncomeCoveragePercentText() },
      ]"
    />
    <DesktopTable
      :rows="holdings"
      :loading="isLoading"
      title="持仓分红计划"
      search-placeholder="搜索产品"
      searchable
    >
      <el-table-column
        prop="productName"
        label="产品"
        min-width="220"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {HoldingRow} -->
      <el-table-column label="持有金额 / 数量" min-width="220">
        <template #default="{ row }: { row: HoldingRow }">
          {{
            formatHoldingMetaText(
              row.marketValue,
              row.holdingQuantity,
              row.unitName,
            )
          }}
        </template>
      </el-table-column>
      <!-- @vue-generic {HoldingRow} -->
      <el-table-column label="股息率" min-width="150">
        <template #default="{ row }: { row: HoldingRow }">
          {{ formatPlainPercent(row.estimatedDividendRate) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {HoldingRow} -->
      <el-table-column label="预计分红" min-width="150">
        <template #default="{ row }: { row: HoldingRow }">
          {{ formatPlainCurrency(row.estimatedDividendAmount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {HoldingRow} -->
      <el-table-column label="预估月薪" min-width="150">
        <template #default="{ row }: { row: HoldingRow }">
          {{ formatMonthlyIncome(row.estimatedDividendAmount / 12) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {HoldingRow} -->
      <el-table-column fixed="right" label="操作" width="150">
        <template #default="{ row }: { row: HoldingRow }">
          <el-button
            link
            type="primary"
            :disabled="!row.positionId"
            @click="openHoldingDetail(row)"
          >
            持仓详情
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
    <DesktopTable
      :rows="expenseCoverageItems"
      :loading="false"
      title="固定支出覆盖"
    >
      <el-table-column
        prop="name"
        label="支出项目"
        min-width="200"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {ExpenseRow} -->
      <el-table-column label="月支出" min-width="150">
        <template #default="{ row }: { row: ExpenseRow }">
          {{ formatMonthlyIncome(row.amount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {ExpenseRow} -->
      <el-table-column label="覆盖率" min-width="150">
        <template #default="{ row }: { row: ExpenseRow }">
          {{ getCoveragePercentText(row) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {ExpenseRow} -->
      <el-table-column fixed="right" label="操作" width="150">
        <template #default="{ row }: { row: ExpenseRow }">
          <el-button
            v-if="row.editable"
            link
            type="primary"
            @click="openEditExpenseModal(row)"
          >
            修改
          </el-button>
          <el-button
            v-if="row.editable"
            link
            type="danger"
            @click="deleteExpenseItem(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      v-model="showExpenseModal"
      :title="expenseModalTitle"
      size="compact"
    >
      <div class="pc-form-grid expense-modal-form">
        <CommonInput
          v-model="expenseName"
          label="支出名称"
          placeholder="例如：房租、房贷、车位费"
        />
        <CommonInput
          v-model="expenseAmount"
          label="每月金额"
          placeholder="请输入金额"
          input-type="number"
          input-mode="decimal"
        />
        <p v-if="expenseError" class="expense-form-error">{{ expenseError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar expense-modal-actions">
          <CommonButton
            type="button"
            variant="secondary"
            :disabled="expenseSaving"
            @click="closeExpenseModal"
          >
            取消
          </CommonButton>
          <CommonButton
            type="button"
            variant="primary"
            :disabled="expenseSaving"
            @click="saveExpenseItem"
          >
            {{ expenseSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
