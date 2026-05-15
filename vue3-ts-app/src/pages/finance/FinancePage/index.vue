<script setup lang="ts">
// 财务首页：组合资产总览、统计入口和收支明细列表。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeOverview } from '@/data/finance'
import {
  deleteTransaction,
  getAccounts,
  getAccountTypes,
  getCurrentMonthlyBudget,
  getTransactions,
  type Transaction,
} from '@/api/modules/finance'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import type { Transaction as DayTransaction } from '@/types/finance'
import AssetOverviewCard from '../components/AssetOverviewCard/index.vue'
import StatsEntry from '../components/StatsEntry/index.vue'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'

const router = useRouter()
const overview = ref({ ...financeOverview })
const transactions = ref<Transaction[]>([])
const isLoadingTransactions = ref(false)
const transactionListError = ref('')
const deletingId = ref<number | null>(null)
const pendingDeleteTransaction = ref<DayTransaction | null>(null)
const showDeleteConfirmModal = ref(false)
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const dayGroups = computed(() => buildTransactionDayGroups(transactions.value))

onMounted(() => {
  loadCashTransactions()
})

function openExpenseEntryPage() {
  router.push('/finance/entry/expense')
}

async function loadCashTransactions() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    transactionListError.value = '请先登录后查看收支记录'
    return
  }

  isLoadingTransactions.value = true
  transactionListError.value = ''

  try {
    const currentMonth = getCurrentMonthKey()
    const currentMonthDate = `${currentMonth}-01`
    const accountTypes = await getAccountTypes({ status: 'active' })
    const cashType = accountTypes.find((type) => type.code === 'cash')
    if (!cashType) {
      transactions.value = []
      updateOverview([], 0)
      transactionListError.value = '现金账户类型不存在'
      return
    }

    const [cashAccounts, transactionList, currentBudget] = await Promise.all([
      getAccounts({
        userId: currentUser.id,
        accountTypeId: cashType.id,
        status: 'active',
      }),
      getTransactions({
        userId: currentUser.id,
      }),
      getCurrentMonthlyBudget(currentUser.id, currentMonthDate),
    ])
    const cashAccountIds = new Set(cashAccounts.map((account) => String(account.id)))
    transactions.value = transactionList.filter((transaction) => cashAccountIds.has(String(transaction.accountId)))
    const currentMonthTransactions = transactions.value.filter((transaction) =>
      isSameMonth(transaction.occurredAt, currentMonth),
    )
    updateOverview(
      currentMonthTransactions,
      toNumber(currentBudget?.amount),
      currentBudget ? toNumber(currentBudget.usedAmount) : undefined,
    )
  } catch (error) {
    transactionListError.value = error instanceof Error ? error.message : '收支记录加载失败'
  } finally {
    isLoadingTransactions.value = false
  }
}

function getCurrentMonthKey() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function updateOverview(
  currentMonthTransactions: Transaction[],
  budgetAmount: number,
  budgetUsedAmount?: number,
) {
  const monthlyIncome = sumTransactions(currentMonthTransactions, 'income')
  const transactionExpense = sumTransactions(currentMonthTransactions, 'expense')
  const monthlyExpense = budgetUsedAmount ?? transactionExpense
  const monthlyBalance = monthlyIncome - monthlyExpense
  const budgetUsagePercent = budgetAmount > 0
    ? (monthlyExpense / budgetAmount) * 100
    : 0

  overview.value = {
    ...overview.value,
    monthlyBalance: `当月结余 ${formatAmount(monthlyBalance)}`,
    monthlyIncome: `收入 ${formatAmount(monthlyIncome)}`,
    monthlyExpense: `支出 ${formatAmount(monthlyExpense)}`,
    budget: budgetAmount > 0 ? `月预算 ${formatAmount(budgetAmount)}` : '月预算 未设置',
    budgetUsageLabel: budgetAmount > 0 ? `已用 ${formatPercent(budgetUsagePercent)}` : '已用 0%',
    budgetUsagePercent: budgetAmount > 0 ? Math.min(Math.max(budgetUsagePercent, 0), 100) : 0,
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

function formatAmount(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatPercent(value: number) {
  return `${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 1,
  }).format(value)}%`
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
    const message = error instanceof Error ? error.message : '删除失败'
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
</script>

<template>
  <CommonFeedback
    v-model="showFeedbackModal"
    :message="feedbackMessage"
    :type="feedbackType"
  />

  <AssetOverviewCard :overview="overview" />
  <section class="feature-block" aria-label="更多功能">
    <header class="feature-header">
      <strong>更多功能</strong>
      <RouterLink class="feature-link" to="/finance/more-features" aria-label="进入更多功能">
        >
      </RouterLink>
    </header>
    <StatsEntry />
  </section>

  <section class="records" aria-label="收支明细">
    <p v-if="transactionListError" class="records-message records-message-error">
      {{ transactionListError }}
    </p>
    <p v-else-if="isLoadingTransactions" class="records-message">
      加载中...
    </p>
    <p v-else-if="dayGroups.length === 0" class="records-message">
      暂无收支记录
    </p>

    <template v-else>
      <TransactionDayCard
        v-for="group in dayGroups"
        :key="group.date"
        :group="group"
        summary-mode="stacked"
        show-delete
        :deleting-id="deletingId"
        @delete="openDeleteConfirm"
      />
    </template>
  </section>

  <FloatingAddButton aria-label="新增记账" @click="openExpenseEntryPage" />

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
    <p v-if="deleteError" class="transaction-delete-error">{{ deleteError }}</p>

    <template #footer>
      <div class="transaction-delete-actions">
        <CommonButton variant="secondary" :disabled="Boolean(deletingId)" @click="closeDeleteConfirm">
          取消
        </CommonButton>
        <CommonButton variant="primary" :disabled="Boolean(deletingId)" @click="confirmDeleteTransaction">
          {{ deletingId ? '删除中...' : '确认删除' }}
        </CommonButton>
      </div>
    </template>
  </CommonModal>
</template>

<style scoped lang="scss" src="./style.scss"></style>
