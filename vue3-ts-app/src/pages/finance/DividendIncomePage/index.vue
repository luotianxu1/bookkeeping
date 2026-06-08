<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import {
  createInvestmentFixedExpense,
  deleteInvestmentFixedExpense,
  getInvestmentFixedExpenses,
  getInvestmentDividendIncome,
  type InvestmentFixedExpense,
  type InvestmentDividendIncomeItem,
  type InvestmentDividendIncomePage,
  updateInvestmentFixedExpense,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type FixedExpenseItem = InvestmentFixedExpense

type ExpenseCoverageItem = FixedExpenseItem & {
  coveredAmount: number
  coveredPercent: number
  status: 'covered' | 'partial' | 'pending'
}

const router = useRouter()
const isLoading = ref(false)
const pageError = ref('')
const expenseSaving = ref(false)
const pageData = ref<InvestmentDividendIncomePage | null>(null)
const currentUserId = ref<number | null>(null)
const expenseItems = ref<FixedExpenseItem[]>([])
const showExpenseModal = ref(false)
const editingExpenseId = ref<number | null>(null)
const expenseName = ref('')
const expenseAmount = ref('')
const expenseError = ref('')

const summary = computed(() => pageData.value?.summary ?? {
  estimatedDividendAmount: 0,
  estimatedDividendRate: 0,
  actualDividendAmount: 0,
  actualDividendRate: 0,
  holdingCount: 0,
})

const holdings = computed(() => pageData.value?.items ?? [])
const updateText = computed(() => {
  const updatedAt = pageData.value?.updatedAt
  return updatedAt ? `数据更新于 ${formatDateTime(updatedAt)}` : ''
})
const estimatedMonthlyIncome = computed(() => summary.value.estimatedDividendAmount / 12)
const expenseCoverageItems = computed<ExpenseCoverageItem[]>(() => {
  let remaining = estimatedMonthlyIncome.value
  return expenseItems.value.map((item) => {
    const amount = Number(item.amount || 0)
    const coveredAmount = Math.max(0, Math.min(remaining, amount))
    const coveredPercent = amount > 0 ? (coveredAmount / amount) * 100 : 0
    const status = coveredAmount >= amount
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
const expenseModalTitle = computed(() => editingExpenseId.value ? '修改固定支出' : '新增固定支出')

onMounted(() => {
  void loadPageData()
})

async function loadPageData() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看攒股收息'
    return
  }

  currentUserId.value = currentUser.id
  isLoading.value = true
  pageError.value = ''
  try {
    pageData.value = await getInvestmentDividendIncome(currentUser.id)
    expenseItems.value = normalizeExpenseItems(pageData.value.fixedExpenses)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '攒股收息加载失败'
  } finally {
    isLoading.value = false
  }
}

async function refreshExpenseItems() {
  if (!currentUserId.value) {
    expenseItems.value = []
    return
  }
  expenseItems.value = normalizeExpenseItems(await getInvestmentFixedExpenses(currentUserId.value))
}

function amountTone(value: number) {
  if (value > 0) return 'positive'
  if (value < 0) return 'negative'
  return 'neutral'
}

function formatAmount(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(value))
}

function formatSignedCurrency(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `${value > 0 ? '+' : '-'} ¥${formatAmount(value)}`
}

function formatSignedPercent(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '0.00%'
  }
  return `${value > 0 ? '+' : '-'}${formatAmount(value)}%`
}

function formatHoldingAmountText(value: number, quantity: number, unitName?: string | null) {
  return `${formatMonthlyIncome(value)} · ${formatAmount(quantity)} ${unitName || '份'}`
}

function formatMonthlyIncome(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `¥${formatAmount(value)}`
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hour}:${minute}`
}

function openHoldingDetail(item: InvestmentDividendIncomeItem) {
  if (!item.positionId) {
    return
  }
  router.push({ path: '/finance/accounts/investment/detail', query: { positionId: item.positionId } })
}

function normalizeExpenseItems(items?: InvestmentFixedExpense[] | null) {
  if (!Array.isArray(items)) {
    return []
  }
  return items
    .map((item) => ({
      ...item,
      id: Number(item.id),
      userId: Number(item.userId),
      name: String(item.name ?? '').trim(),
      amount: Number(item.amount ?? 0),
      sortOrder: Number(item.sortOrder ?? 0),
    }))
    .filter((item) => Number.isFinite(item.id) && item.id > 0 && item.name && Number.isFinite(item.amount) && item.amount > 0)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
}

function openCreateExpenseModal() {
  editingExpenseId.value = null
  expenseName.value = ''
  expenseAmount.value = ''
  expenseError.value = ''
  showExpenseModal.value = true
}

function openEditExpenseModal(item: FixedExpenseItem) {
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
      const currentItem = expenseItems.value.find((item) => item.id === editingExpenseId.value)
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
    expenseError.value = error instanceof Error ? error.message : '保存固定支出失败'
  } finally {
    expenseSaving.value = false
  }
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
    pageError.value = error instanceof Error ? error.message : '删除固定支出失败'
  }
}

function moveExpenseItem(index: number, direction: -1 | 1) {
  void reorderExpenseItem(index, direction)
}

async function reorderExpenseItem(index: number, direction: -1 | 1) {
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= expenseItems.value.length) {
    return
  }
  if (!currentUserId.value) {
    return
  }

  const next = expenseItems.value.slice()
  const currentItem = next[index]
  const targetItem = next[targetIndex]
  if (!currentItem || !targetItem) {
    return
  }

  next[index] = { ...targetItem, sortOrder: currentItem.sortOrder }
  next[targetIndex] = { ...currentItem, sortOrder: targetItem.sortOrder }
  expenseItems.value = normalizeExpenseItems(next)

  try {
    await Promise.all([
      updateInvestmentFixedExpense(currentItem.id, {
        userId: currentUserId.value,
        name: currentItem.name,
        amount: currentItem.amount,
        currencyCode: currentItem.currencyCode || 'CNY',
        sortOrder: targetItem.sortOrder ?? targetIndex + 1,
        remark: currentItem.remark ?? null,
      }),
      updateInvestmentFixedExpense(targetItem.id, {
        userId: currentUserId.value,
        name: targetItem.name,
        amount: targetItem.amount,
        currencyCode: targetItem.currencyCode || 'CNY',
        sortOrder: currentItem.sortOrder ?? index + 1,
        remark: targetItem.remark ?? null,
      }),
    ])
    await refreshExpenseItems()
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '固定支出排序保存失败'
    await loadPageData()
  }
}

function getCoveragePercentText(item: ExpenseCoverageItem) {
  return `${formatAmount(item.coveredPercent)}%`
}
</script>

<template>
  <section class="dividend-income-page" aria-label="攒股收息">
    <PageHeader title="攒股收息" back-label="返回更多功能" />

    <p v-if="pageError" class="dividend-message dividend-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
    <section class="summary-card" aria-label="收息总览">
      <div class="summary-row">
        <AmountText
          tag="strong"
          :value="formatSignedCurrency(summary.estimatedDividendAmount)"
          :tone="amountTone(summary.estimatedDividendAmount)"
        />
        <AmountText
          tag="span"
          :value="`预计 ${formatSignedPercent(summary.estimatedDividendRate)}`"
          :tone="amountTone(summary.estimatedDividendRate)"
        />
      </div>
      <p class="summary-monthly">预估月薪 {{ formatMonthlyIncome(estimatedMonthlyIncome) }}</p>
      <p v-if="updateText" class="summary-footnote">仅展示历史稳定分红标的，{{ updateText }}</p>
      <p v-else class="summary-footnote">仅展示历史稳定分红标的</p>
    </section>

    <section class="expense-card" aria-label="固定支出覆盖">
      <header class="expense-card-head">
        <div />
        <CommonButton variant="secondary" size="sm" @click="openCreateExpenseModal">新增支出</CommonButton>
      </header>

      <p v-if="expenseCoverageItems.length === 0" class="dividend-message">
        暂未设置固定支出，点击右上角新增后即可查看覆盖进度
      </p>

      <div v-else class="expense-coverage-list">
        <article
          v-for="(item, index) in expenseCoverageItems"
          :key="item.id"
          :class="['expense-coverage-item', `is-${item.status}`]"
        >
          <div class="expense-coverage-main">
            <div class="expense-coverage-top">
              <strong>{{ item.name }}</strong>
              <span>{{ formatMonthlyIncome(item.amount) }}</span>
            </div>
            <div class="expense-progress-track">
              <span class="expense-progress-fill" :style="{ width: `${Math.min(100, item.coveredPercent)}%` }"></span>
            </div>
            <p class="expense-coverage-note">
              <template v-if="item.status === 'covered'">
                已覆盖 100%
              </template>
              <template v-else-if="item.status === 'partial'">
                已覆盖 {{ formatMonthlyIncome(item.coveredAmount) }} · 剩余可覆盖 {{ getCoveragePercentText(item) }}
              </template>
              <template v-else>
                暂未覆盖
              </template>
            </p>
          </div>

          <div class="expense-coverage-actions">
            <button type="button" :disabled="index === 0" @click="moveExpenseItem(index, -1)">上移</button>
            <button type="button" :disabled="index === expenseCoverageItems.length - 1" @click="moveExpenseItem(index, 1)">下移</button>
            <button type="button" @click="openEditExpenseModal(item)">编辑</button>
            <button type="button" class="danger" @click="deleteExpenseItem(item.id)">删除</button>
          </div>
        </article>
      </div>
    </section>

    <section class="holding-card" aria-label="持仓分红计划">
      <header class="holding-header">
        <span class="holding-spacer" />
        <div class="holding-header-right">
          <span>预估分红</span>
          <span>预估月薪</span>
        </div>
      </header>

      <p v-if="holdings.length === 0" class="dividend-message">
        暂无符合条件的稳定分红股票或基金持仓
      </p>

      <article
        v-for="item in holdings"
        :key="item.productId"
        :class="['holding-row', { clickable: !!item.positionId }]"
        @click="openHoldingDetail(item)"
      >
        <div class="holding-left">
          <p>{{ item.productName }}</p>
          <small>{{ formatHoldingAmountText(item.marketValue, item.holdingQuantity, item.unitName) }}</small>
        </div>

        <div class="holding-right">
          <div class="value-column">
            <AmountText
              tag="strong"
              :value="formatSignedCurrency(item.estimatedDividendAmount)"
              :tone="amountTone(item.estimatedDividendAmount)"
            />
            <AmountText
              tag="small"
              :value="formatSignedPercent(item.estimatedDividendRate)"
              :tone="amountTone(item.estimatedDividendRate)"
            />
          </div>
          <div class="value-column">
            <AmountText
              tag="strong"
              :value="formatMonthlyIncome(item.estimatedDividendAmount / 12)"
              tone="inherit"
            />
          </div>
        </div>
      </article>
    </section>
    </template>

    <CommonModal v-model="showExpenseModal" :title="expenseModalTitle" size="compact">
      <div class="expense-modal-form">
        <CommonInput v-model="expenseName" label="支出名称" placeholder="例如：房租、房贷、车位费" />
        <CommonInput v-model="expenseAmount" label="每月金额" placeholder="请输入金额" input-type="number" input-mode="decimal" />
        <p v-if="expenseError" class="expense-form-error">{{ expenseError }}</p>
      </div>
      <template #footer>
        <div class="expense-modal-actions">
          <CommonButton type="button" variant="secondary" :disabled="expenseSaving" @click="closeExpenseModal">取消</CommonButton>
          <CommonButton type="button" variant="primary" :disabled="expenseSaving" @click="saveExpenseItem">
            {{ expenseSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
