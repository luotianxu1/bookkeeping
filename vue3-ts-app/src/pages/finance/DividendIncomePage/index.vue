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
const isExpenseEditing = ref(false)
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
const expenseModalTitle = computed(() => editingExpenseId.value ? '修改固定支出' : '新增固定支出')
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

function formatPlainCurrency(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `${value < 0 ? '-' : ''}¥${formatAmount(value)}`
}

function formatPlainPercent(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '0.00%'
  }
  return `${value < 0 ? '-' : ''}${formatAmount(value)}%`
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

function getCoveragePercentText(item: ExpenseCoverageItem) {
  return `${formatAmount(item.coveredPercent)}%`
}

function toggleExpenseEditing() {
  isExpenseEditing.value = !isExpenseEditing.value
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

function getCoverageBackgroundStyle(item: ExpenseCoverageItem) {
  const percent = Math.max(0, Math.min(100, item.coveredPercent))
  const activeColor = '#86efac'
  const baseColor = '#f8fafc'

  return {
    background: `linear-gradient(90deg, ${activeColor} 0%, ${activeColor} ${percent}%, ${baseColor} ${percent}%, ${baseColor} 100%)`,
  }
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
        <div class="expense-card-actions">
          <CommonButton variant="secondary" size="sm" @click="toggleExpenseEditing">
            {{ isExpenseEditing ? '完成' : '编辑' }}
          </CommonButton>
          <CommonButton variant="secondary" size="sm" @click="openCreateExpenseModal">新增支出</CommonButton>
        </div>
      </header>

      <p v-if="expenseCoverageItems.length === 0" class="dividend-message">
        暂未设置固定支出，点击右上角新增后即可查看覆盖进度
      </p>

      <div v-else class="expense-coverage-list">
        <article
          v-for="item in expenseCoverageItems"
          :key="item.id"
          :class="['expense-coverage-item', `is-${item.status}`, { 'is-editing': isExpenseEditing }]"
          :style="getCoverageBackgroundStyle(item)"
        >
          <div class="expense-coverage-main">
            <div class="expense-coverage-top">
              <strong>{{ item.name }}</strong>
              <span>{{ formatMonthlyIncome(item.amount) }}</span>
            </div>
            <p class="expense-coverage-note">
              {{ getCoveragePercentText(item) }}
            </p>
            <div v-if="isExpenseEditing" class="expense-coverage-actions">
              <button type="button" class="expense-action-icon" aria-label="修改" @click="openEditExpenseModal(item)">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M4 20h4.75L19 9.75 14.25 5 4 15.25V20Zm3.5-1.5H5.5v-2l8.75-8.75 2 2L7.5 18.5ZM18.3 4.95l.75-.75a1.5 1.5 0 0 1 2.12 0l.63.63a1.5 1.5 0 0 1 0 2.12l-.75.75-2.75-2.75Z"
                  />
                </svg>
              </button>
              <button type="button" class="expense-action-icon danger" aria-label="删除" @click="deleteExpenseItem(item.id)">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M9 3.75h6a1.25 1.25 0 0 1 1.25 1.25V6H20a.75.75 0 0 1 0 1.5h-1.03l-.72 10.12A2.5 2.5 0 0 1 15.76 20H8.24a2.5 2.5 0 0 1-2.49-2.38L5.03 7.5H4a.75.75 0 0 1 0-1.5h3.75V5A1.25 1.25 0 0 1 9 3.75Zm1.5 2.25h3V5.25h-3V6Zm-3.24 1.5.68 9.99c.03.53.47.95 1 .95h7.52c.53 0 .97-.42 1-.95l.68-9.99H7.26Zm2.49 2.25a.75.75 0 0 1 .75.75v4.5a.75.75 0 0 1-1.5 0v-4.5a.75.75 0 0 1 .75-.75Zm4.5 0a.75.75 0 0 1 .75.75v4.5a.75.75 0 0 1-1.5 0v-4.5a.75.75 0 0 1 .75-.75Z"
                  />
                </svg>
              </button>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="holding-card" aria-label="持仓分红计划">
      <header class="holding-header">
        <span class="holding-spacer" />
        <div class="holding-header-right">
          <span>股息率</span>
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
              :value="formatPlainPercent(item.estimatedDividendRate)"
              :tone="amountTone(item.estimatedDividendRate)"
            />
          </div>
          <div class="value-column">
            <AmountText
              tag="strong"
              :value="formatPlainCurrency(item.estimatedDividendAmount)"
              :tone="amountTone(item.estimatedDividendAmount)"
            />
          </div>
          <div class="value-column">
            <AmountText
              tag="strong"
              :value="formatMonthlyIncome(item.estimatedDividendAmount / 12)"
              :tone="amountTone(item.estimatedDividendAmount)"
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
