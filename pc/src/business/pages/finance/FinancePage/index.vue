<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import CashFlowChart from '@/business/components/CashFlowChart.vue'
import TransactionTable from '@/business/components/TransactionTable.vue'
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

import { formatAmount } from '@/business/utils/format'
import { amountTone } from '@/business/utils/amount-tone'
// 财务首页：组合资产总览、统计入口和收支明细列表。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  deleteTransaction,
  getAccountSummary,
  getAccountTypes,
  getCurrentMonthlyBudget,
  getTransactions,
  type Transaction,
} from '@/business/api/modules/finance'
import CommonButton from '@/business/components/common/CommonButton/index.vue'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { buildTransactionDayGroups } from '@/business/utils/transaction-day-groups'
import type { Transaction as DayTransaction } from '@/business/types/finance'

import type { FinanceOverview } from '@/business/types/finance'
import { getErrorMessage } from '@/business/api/request'

const router = useRouter()
const emptyOverview: FinanceOverview = {
  totalAssets: '',
  monthlyBalance: '当月结余 --',
  monthlyIncome: '收入 --',
  monthlyExpense: '支出 --',
  budget: '月预算 --',
  budgetUsageLabel: '已用 --',
  budgetUsagePercent: 0,
  isBudgetExceeded: false,
}

const overview = ref({ ...emptyOverview })
const monthlyAmounts = ref({ income: 0, expense: 0, balance: 0 })
const transactions = ref<Transaction[]>([])
const isLoadingTransactions = ref(true)
const transactionListError = ref('')
const deletingId = ref<number | null>(null)
const pendingDeleteTransaction = ref<DayTransaction | null>(null)
const showDeleteConfirmModal = ref(false)
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const dayGroups = computed(() =>
  buildTransactionDayGroups(
    transactions.value.filter((transaction) =>
      isWithinRecentWeek(transaction.occurredAt),
    ),
  ),
)

onMounted(() => {
  void loadCashTransactions()
})

function openExpenseEntryPage() {
  router.push('/finance/entry/expense')
}

function openEditTransaction(transaction: DayTransaction) {
  if (
    !transaction.id ||
    transaction.sourceType !== 'transaction' ||
    !transaction.accountId ||
    !transaction.categoryId ||
    !transaction.occurredAt
  ) {
    showFeedback('当前记录暂不支持修改', 'error')
    return
  }

  router.push({
    path: '/finance/entry/expense',
    query: {
      transactionId: String(transaction.id),
      type: transaction.type,
      amount: String(transaction.rawAmount ?? 0),
      accountId: String(transaction.accountId),
      categoryId: String(transaction.categoryId),
      occurredAt: transaction.occurredAt,
      remark: transaction.remark ?? '',
      redirect: '/finance',
    },
  })
}

async function loadCashTransactions() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    transactionListError.value = '请先登录后查看收支记录'
    isLoadingTransactions.value = false
    return
  }

  isLoadingTransactions.value = true
  transactionListError.value = ''

  try {
    const currentMonth = getCurrentMonthKey()
    const currentMonthDate = `${currentMonth}-01`
    const typeList = await getAccountTypes({ status: 'active' })
    const cashType = typeList.find((type) => type.code === 'cash')
    if (!cashType) {
      transactions.value = []
      updateOverview(0, 0, 0)
      transactionListError.value = '现金账户类型不存在'
      return
    }

    const [accountSummary, transactionList, currentBudget] = await Promise.all([
      getAccountSummary(currentUser.id),
      getTransactions({
        userId: currentUser.id,
      }),
      getCurrentMonthlyBudget(currentUser.id, currentMonthDate),
    ])

    const cashAccounts = accountSummary.accounts.filter(
      (account) => account.accountTypeId === cashType.id,
    )
    const cashAccountIds = new Set(
      cashAccounts.map((account) => String(account.id)),
    )
    transactions.value = transactionList.filter((transaction) =>
      cashAccountIds.has(String(transaction.accountId)),
    )
    const currentMonthTransactions = transactions.value.filter((transaction) =>
      isSameMonth(transaction.occurredAt, currentMonth),
    )
    const monthlyIncome = sumTransactions(currentMonthTransactions, 'income')
    const transactionExpense = sumTransactions(
      currentMonthTransactions,
      'expense',
    )
    const budgetAmount = toNumber(currentBudget?.amount)
    const monthlyExpense = currentBudget
      ? toNumber(currentBudget.usedAmount)
      : transactionExpense

    updateOverview(monthlyIncome, monthlyExpense, budgetAmount)
    overview.value = {
      ...overview.value,
      totalAssets:
        accountSummary.totalAssets == null
          ? ''
          : formatAmount(toNumber(accountSummary.totalAssets)),
    }
  } catch (error) {
    transactions.value = []
    transactionListError.value = getErrorMessage(error, '收支记录加载失败')
  } finally {
    isLoadingTransactions.value = false
  }
}

function getCurrentMonthKey() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function updateOverview(
  monthlyIncome: number,
  monthlyExpense: number,
  budgetAmount: number,
) {
  const monthlyBalance = monthlyIncome - monthlyExpense
  monthlyAmounts.value = {
    income: monthlyIncome,
    expense: monthlyExpense,
    balance: monthlyBalance,
  }
  const budgetUsagePercent =
    budgetAmount > 0 ? (monthlyExpense / budgetAmount) * 100 : 0
  const isBudgetExceeded = budgetAmount > 0 && budgetUsagePercent > 100

  overview.value = {
    ...overview.value,
    monthlyBalance: `当月结余 ${formatAmount(monthlyBalance)}`,
    monthlyIncome: `收入 ${formatAmount(monthlyIncome)}`,
    monthlyExpense: `支出 ${formatAmount(monthlyExpense)}`,
    budget:
      budgetAmount > 0
        ? `月预算 ${formatAmount(budgetAmount)}`
        : '月预算 未设置',
    budgetUsageLabel:
      budgetAmount > 0
        ? `已用 ${formatPercent(budgetUsagePercent)}`
        : '已用 0.0%',
    budgetUsagePercent:
      budgetAmount > 0 ? Math.min(Math.max(budgetUsagePercent, 0), 100) : 0,
    isBudgetExceeded,
  }
}

function sumTransactions(list: Transaction[], type: Transaction['type']) {
  return list
    .filter((transaction) => transaction.type === type)
    .reduce((sum, transaction) => sum + toNumber(transaction.amount), 0)
}

function toNumber(value: unknown) {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) ? parsed : 0
}

function isSameMonth(value: string, monthKey: string) {
  if (value.startsWith(monthKey)) {
    return true
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return false
  }

  const dateMonthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  return dateMonthKey === monthKey
}

function isWithinRecentWeek(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return false
  }

  const now = new Date()
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - 6)

  return date.getTime() >= start.getTime() && date.getTime() <= now.getTime()
}

function formatPercent(value: number) {
  return formatPercentage(value)
}

function openDeleteConfirm(transaction: DayTransaction) {
  pendingDeleteTransaction.value = transaction
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirm() {
  if (deletingId.value) {
    return
  }

  showDeleteConfirmModal.value = false
  pendingDeleteTransaction.value = null
  deleteError.value = ''
}

async function confirmDeleteTransaction() {
  const transactionId = pendingDeleteTransaction.value?.id
  const currentUser = getStoredCurrentUser()
  if (!transactionId || !currentUser) {
    deleteError.value = '请选择要删除的收支记录'
    return
  }

  deletingId.value = transactionId
  deleteError.value = ''

  try {
    await deleteTransaction(transactionId, currentUser.id)
    showDeleteConfirmModal.value = false
    pendingDeleteTransaction.value = null
    showFeedback('删除成功', 'success')
    await loadCashTransactions()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    deletingId.value = null
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

const dashboardMetrics = computed(() => [
  { label: '总资产', value: overview.value.totalAssets || '—' },
  {
    label: '本月收入',
    value: overview.value.monthlyIncome.replace('收入 ', ''),
    tone: amountTone(monthlyAmounts.value.income),
  },
  {
    label: '本月净支出',
    value: overview.value.monthlyExpense.replace('支出 ', ''),
    tone: amountTone(-monthlyAmounts.value.expense),
  },
  {
    label: '本月结余',
    value: overview.value.monthlyBalance.replace('当月结余 ', ''),
    tone: amountTone(monthlyAmounts.value.balance),
  },
])
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="财务总览">
      <template #right>
        <el-button
          :loading="isLoadingTransactions"
          @click="loadCashTransactions"
        >
          刷新
        </el-button>
        <el-button type="primary" @click="openExpenseEntryPage">
          记一笔
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="transactionListError"
      :title="transactionListError"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="loadCashTransactions">
        重试
      </el-button>
    </el-alert>
    <MetricStrip :items="dashboardMetrics" />
    <div class="desktop-columns dashboard-columns">
      <PagePanel title="现金收支 · 近两周">
        <CashFlowChart :rows="transactions" />
      </PagePanel>
      <PagePanel title="本月预算">
        <div class="budget-focus">
          <el-progress
            :format="formatPercentage"
            type="dashboard"
            :percentage="overview.budgetUsagePercent"
            :status="overview.isBudgetExceeded ? 'exception' : undefined"
            :width="150"
          />
          <strong>{{ overview.budget }}</strong>
          <span>{{ overview.budgetUsageLabel }}</span>
        </div>
        <template #actions>
          <el-button text @click="router.push('/finance/budgets')">
            管理预算
          </el-button>
        </template>
      </PagePanel>
    </div>
    <TransactionTable
      :rows="dayGroups.flatMap((group) => group.transactions)"
      :loading="isLoadingTransactions"
      title="近七日收支"
      show-delete
      :deleting-id="deletingId"
      @edit="openEditTransaction"
      @delete="openDeleteConfirm"
    />
    <div class="desktop-bottom-actions">
      <el-button @click="router.push('/finance/transactions')">
        查看全部流水
      </el-button>
    </div>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :close-on-overlay="!deletingId"
      @close="closeDeleteConfirm"
    >
      <p class="transaction-delete-confirm-text">
        删除后会同步恢复账户余额，确认删除这条收支记录吗？
      </p>
      <p v-if="deleteError" class="transaction-delete-error">
        {{ deleteError }}
      </p>
      <template #footer>
        <div class="pc-toolbar transaction-delete-actions">
          <CommonButton
            variant="secondary"
            :disabled="Boolean(deletingId)"
            @click="closeDeleteConfirm"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="Boolean(deletingId)"
            @click="confirmDeleteTransaction"
          >
            {{ deletingId ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
