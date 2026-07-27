<script setup lang="ts">
// 收支列表页：还原 Pencil「收支列表页」画板中的筛选与日期分组流水。
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  deleteTransaction,
  getAccounts,
  getAccountTypes,
  getTransactionPage,
  type Account,
  type Transaction,
  type TransactionType,
} from '@/api/modules/finance'
import CommonBottomSheet from '@/components/common/CommonBottomSheet/index.vue'
import CommonConfirmSheet from '@/components/common/CommonConfirmSheet/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonOptionSheet from '@/components/common/CommonOptionSheet/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { useFinanceFamilyView } from '@/composables/useFinanceFamilyView'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import type { Transaction as DayTransaction } from '@/types/finance'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const filterOptions = ['全部', '收入', '支出']
const TRANSACTION_PAGE_SIZE = 50
type DateRangeKey = 'month' | 'lastMonth' | 'threeMonths' | 'custom'
type SortOrder = 'desc' | 'asc'
type SheetOption = {
  label: string
  value: string
  description?: string
}

const dateRangeOptions: SheetOption[] = [
  { label: '本月', value: 'month', description: '查看当前自然月收支' },
  { label: '上月', value: 'lastMonth', description: '查看上一个自然月收支' },
  { label: '近 3 个月', value: 'threeMonths', description: '从近三个月首日到今天' },
  { label: '自定义时间', value: 'custom', description: '手动选择开始和结束日期' },
]
const transactionTypeOptions: SheetOption[] = filterOptions.map((option) => ({
  label: option,
  value: option,
  description: option === '全部' ? '查看所有收支记录' : `只查看${option}记录`,
}))
const activeFilter = ref(filterOptions[0])
const router = useRouter()
const transactions = ref<Transaction[]>([])
const cashAccounts = ref<Account[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const totalItems = ref(0)
const summaryIncomeTotal = ref(0)
const summaryExpenseTotal = ref(0)
const summaryBalanceTotal = ref(0)
const isLoadingTransactions = ref(false)
const transactionListError = ref('')
const deletingId = ref<number | null>(null)
const pendingDeleteTransaction = ref<DayTransaction | null>(null)
const showDeleteConfirmModal = ref(false)
const showTypeSheet = ref(false)
const showDateRangeSheet = ref(false)
const showAccountSheet = ref(false)
const showStartDateSheet = ref(false)
const showEndDateSheet = ref(false)
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const transactionRequestSerial = ref(0)
const selectedDateRange = ref<DateRangeKey>('month')
const selectedAccountId = ref('all')
const selectedSortOrder = ref<SortOrder>('desc')
const customStartDate = ref(getMonthRange(0).startDate)
const customEndDate = ref(getMonthRange(0).endDate)
const {
  familyView,
  familyViewOptions,
  selectedFamilyView,
  canSwitchFamilyView,
  isSelfView,
  isReadOnlyFamilyView,
  selectedViewerUserIds,
  viewerNameByUserId,
  loadFamilyMembers,
} = useFinanceFamilyView()

const currentTransactionType = computed<TransactionType | undefined>(() => {
  if (activeFilter.value === '收入') {
    return 'income'
  }
  if (activeFilter.value === '支出') {
    return 'expense'
  }
  return undefined
})
const dayGroups = computed(() => buildTransactionDayGroups(transactions.value, selectedSortOrder.value))
const familyViewHint = computed(() => {
  if (!isReadOnlyFamilyView.value) {
    return ''
  }

  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家收支记录。'
    : `当前查看 ${selectedFamilyView.value.label} 的收支记录。`
})
const familyViewSelectOptions = computed<CommonSelectOption[]>(() =>
  familyViewOptions.value.map((option) => ({
    label: option.label,
    value: option.value,
  })),
)
const hasPagination = computed(() => totalPages.value > 1)
const paginationSummary = computed(() => `第 ${currentPage.value} / ${totalPages.value} 页，共 ${totalItems.value} 条`)
const canGoPrevPage = computed(() => currentPage.value > 1)
const canGoNextPage = computed(() => currentPage.value < totalPages.value)
const accountOptions = computed<SheetOption[]>(() => [
  { label: '全部账户', value: 'all', description: '包含所有现金账户' },
  ...cashAccounts.value.map((account) => ({
    label: accountLabel(account),
    value: String(account.id),
    description: account.accountTypeName ?? '现金账户',
  })),
])
const selectedDateRangeLabel = computed(() => {
  if (selectedDateRange.value === 'custom') {
    return `${formatShortDate(resolvedDateRange.value.startDate)}-${formatShortDate(resolvedDateRange.value.endDate)}`
  }
  return dateRangeOptions.find((option) => option.value === selectedDateRange.value)?.label ?? '本月'
})
const selectedTypeLabel = computed(() =>
  transactionTypeOptions.find((option) => option.value === activeFilter.value)?.label ?? '全部',
)
const selectedAccountLabel = computed(() =>
  accountOptions.value.find((option) => option.value === selectedAccountId.value)?.label ?? '全部账户',
)
const selectedSortLabel = computed(() =>
  selectedSortOrder.value === 'desc' ? '倒序' : '正序',
)
const resolvedDateRange = computed(() => {
  if (selectedDateRange.value === 'lastMonth') {
    return getMonthRange(-1)
  }
  if (selectedDateRange.value === 'threeMonths') {
    return getRecentThreeMonthRange()
  }
  if (selectedDateRange.value === 'custom') {
    return normalizeDateRange(customStartDate.value, customEndDate.value)
  }
  return getMonthRange(0)
})
const summaryTitle = computed(() => `${dateRangeOptions.find((option) => option.value === selectedDateRange.value)?.label ?? '本月'}统计`)

onMounted(() => {
  void initializePage()
})

watch(familyView, async () => {
  currentPage.value = 1
  selectedAccountId.value = 'all'
  if (showDeleteConfirmModal.value && !deletingId.value) {
    closeDeleteConfirm()
  }
  await loadCashAccountOptions()
  await loadCashTransactions()
})

watch([activeFilter, selectedDateRange, selectedAccountId, selectedSortOrder, customStartDate, customEndDate], () => {
  currentPage.value = 1
  void loadCashTransactions()
})

async function initializePage() {
  await loadFamilyMembers()
  await loadCashAccountOptions()
  await loadCashTransactions()
}

async function loadCashAccountOptions() {
  if (selectedViewerUserIds.value.length === 0) {
    cashAccounts.value = []
    return
  }

  try {
    const typeList = await getAccountTypes({ status: 'active' })
    const cashType = typeList.find((type) => type.code === 'cash')
    if (!cashType) {
      cashAccounts.value = []
      return
    }

    const accountGroups = await Promise.all(
      selectedViewerUserIds.value.map((userId) =>
        getAccounts({
          userId,
          accountTypeId: cashType.id,
          status: 'active',
        }),
      ),
    )
    const accountMap = new Map<number, Account>()
    accountGroups.flat().forEach((account) => accountMap.set(account.id, account))
    cashAccounts.value = Array.from(accountMap.values())
  } catch {
    cashAccounts.value = []
  }
}

function openEditTransaction(transaction: DayTransaction) {
  if (!isSelfView.value) {
    return
  }

  if (!transaction.id || transaction.sourceType !== 'transaction' || !transaction.accountId || !transaction.categoryId || !transaction.occurredAt) {
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
      redirect: '/finance/transactions',
    },
  })
}

async function loadCashTransactions() {
  if (selectedViewerUserIds.value.length === 0) {
    transactions.value = []
    totalItems.value = 0
    totalPages.value = 1
    summaryIncomeTotal.value = 0
    summaryExpenseTotal.value = 0
    summaryBalanceTotal.value = 0
    transactionListError.value = '请先登录后查看收支记录'
    return
  }

  const requestSerial = transactionRequestSerial.value + 1
  transactionRequestSerial.value = requestSerial
  isLoadingTransactions.value = true
  transactionListError.value = ''

  try {
    const pageData = await getTransactionPage({
      userIds: selectedViewerUserIds.value.join(','),
      type: currentTransactionType.value,
      accountId: selectedAccountId.value === 'all' ? undefined : Number(selectedAccountId.value),
      cashOnly: true,
      startDate: resolvedDateRange.value.startDate,
      endDate: resolvedDateRange.value.endDate,
      sortOrder: selectedSortOrder.value,
      page: currentPage.value,
      pageSize: TRANSACTION_PAGE_SIZE,
    })

    if (requestSerial !== transactionRequestSerial.value) {
      return
    }

    transactions.value = pageData.items.map((transaction) => {
      const viewerName = viewerNameByUserId.value.get(transaction.userId)
      return {
        ...transaction,
        accountName: isReadOnlyFamilyView.value && selectedFamilyView.value.kind === 'total' && viewerName
          ? `${viewerName} · ${transaction.accountName ?? '现金账户'}`
          : transaction.accountName,
      }
    })
    currentPage.value = pageData.page
    totalItems.value = pageData.total
    totalPages.value = pageData.totalPages
    summaryIncomeTotal.value = toNumber(pageData.incomeTotal)
    summaryExpenseTotal.value = toNumber(pageData.expenseTotal)
    summaryBalanceTotal.value = toNumber(pageData.balanceTotal)
  } catch (error) {
    if (requestSerial !== transactionRequestSerial.value) {
      return
    }
    transactions.value = []
    totalItems.value = 0
    totalPages.value = 1
    summaryIncomeTotal.value = 0
    summaryExpenseTotal.value = 0
    summaryBalanceTotal.value = 0
    transactionListError.value = error instanceof Error ? error.message : '收支记录加载失败'
  } finally {
    if (requestSerial === transactionRequestSerial.value) {
      isLoadingTransactions.value = false
    }
  }
}

function goToPrevPage() {
  if (!canGoPrevPage.value || isLoadingTransactions.value) {
    return
  }
  currentPage.value -= 1
  void loadCashTransactions()
}

function goToNextPage() {
  if (!canGoNextPage.value || isLoadingTransactions.value) {
    return
  }
  currentPage.value += 1
  void loadCashTransactions()
}

function openDeleteConfirm(transaction: DayTransaction) {
  if (!isSelfView.value) {
    return
  }

  pendingDeleteTransaction.value = transaction
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function selectDateRange(value: string) {
  selectedDateRange.value = value as DateRangeKey
  if (selectedDateRange.value !== 'custom') {
    showDateRangeSheet.value = false
  }
}

function selectTransactionType(value: string) {
  activeFilter.value = value
}

function selectAccount(value: string) {
  selectedAccountId.value = value
}

function toggleSortOrder() {
  selectedSortOrder.value = selectedSortOrder.value === 'desc' ? 'asc' : 'desc'
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

function getMonthRange(offset: number) {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth() + offset, 1)
  const end = new Date(now.getFullYear(), now.getMonth() + offset + 1, 0)
  return {
    startDate: formatInputDate(start),
    endDate: formatInputDate(end),
  }
}

function getRecentThreeMonthRange() {
  const now = new Date()
  return {
    startDate: formatInputDate(new Date(now.getFullYear(), now.getMonth() - 2, 1)),
    endDate: formatInputDate(now),
  }
}

function normalizeDateRange(startDate: string, endDate: string) {
  if (!startDate || !endDate || startDate <= endDate) {
    return { startDate, endDate }
  }
  return {
    startDate: endDate,
    endDate: startDate,
  }
}

function formatInputDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatShortDate(value: string) {
  const [, month, day] = value.split('-')
  return month && day ? `${month}.${day}` : value
}

function formatAmount(value: number) {
  return value.toLocaleString('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatPositiveAmount(value: number) {
  return `+${formatAmount(Math.abs(value))}`
}

function formatNegativeAmount(value: number) {
  return `-${formatAmount(Math.abs(value))}`
}

function formatSignedAmount(value: number) {
  if (value > 0) {
    return formatPositiveAmount(value)
  }
  if (value < 0) {
    return formatNegativeAmount(value)
  }
  return formatAmount(0)
}

function toNumber(value: unknown) {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) ? parsed : 0
}

function accountLabel(account: Account) {
  const viewerName = viewerNameByUserId.value.get(account.userId)
  return isReadOnlyFamilyView.value && selectedFamilyView.value.kind === 'total' && viewerName
    ? `${viewerName} · ${account.name}`
    : account.name
}
</script>

<template>
  <section class="transaction-list-page" aria-label="收支列表">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="收支列表" back-to="/finance" back-label="返回财务首页">
      <template #right>
        <div v-if="canSwitchFamilyView" class="transaction-family-switch">
          <CommonSelect
            v-model="familyView"
            label="切换家庭成员收支视角"
            :options="familyViewSelectOptions"
          />
        </div>
      </template>
    </PageHeader>

    <p v-if="familyViewHint" class="transaction-view-hint">
      {{ familyViewHint }}
    </p>

    <section class="transaction-summary-card" aria-label="收支统计">
      <div class="transaction-summary-copy">
        <span>{{ summaryTitle }}</span>
        <strong :class="summaryBalanceTotal >= 0 ? 'transaction-summary-balance-positive' : 'transaction-summary-balance-negative'">
          {{ formatSignedAmount(summaryBalanceTotal) }}
        </strong>
      </div>
      <div class="transaction-summary-metrics">
        <span class="transaction-summary-metric transaction-summary-metric-income">
          <small>收入</small>
          <strong>{{ formatPositiveAmount(summaryIncomeTotal) }}</strong>
        </span>
        <span class="transaction-summary-metric transaction-summary-metric-expense">
          <small>支出</small>
          <strong>{{ formatNegativeAmount(summaryExpenseTotal) }}</strong>
        </span>
      </div>
    </section>

    <div class="transaction-filter-bar" aria-label="收支筛选">
      <button type="button" @click="showTypeSheet = true">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M4 7H20M4 12H14M4 17H18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          <path d="M17 10L20 7L17 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>{{ selectedTypeLabel }}</span>
      </button>
      <button type="button" @click="showDateRangeSheet = true">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M8 2V5M16 2V5M3.5 9H20.5M6 4H18C19.381 4 20.5 5.119 20.5 6.5V18C20.5 19.381 19.381 20.5 18 20.5H6C4.619 20.5 3.5 19.381 3.5 18V6.5C3.5 5.119 4.619 4 6 4Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M8 13H8.01M12 13H12.01M16 13H16.01M8 17H8.01M12 17H12.01" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>
        </svg>
        <span>{{ selectedDateRangeLabel }}</span>
      </button>
      <button type="button" @click="showAccountSheet = true">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M4.5 7.5H17.5C18.605 7.5 19.5 8.395 19.5 9.5V18C19.5 19.105 18.605 20 17.5 20H4.5C3.395 20 2.5 19.105 2.5 18V9.5C2.5 8.395 3.395 7.5 4.5 7.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
          <path d="M6 7.5V6.5C6 5.395 6.895 4.5 8 4.5H19.5C20.605 4.5 21.5 5.395 21.5 6.5V15.5C21.5 16.605 20.605 17.5 19.5 17.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M15.5 13.75H16.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>
        </svg>
        <span>{{ selectedAccountLabel }}</span>
      </button>
      <button type="button" @click="toggleSortOrder">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M4 7H16M4 12H13M4 17H10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          <path
            v-if="selectedSortOrder === 'desc'"
            d="M18 6V18M18 18L21 15M18 18L15 15"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            v-else
            d="M18 18V6M18 6L21 9M18 6L15 9"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <span>{{ selectedSortLabel }}</span>
      </button>
    </div>

    <section class="transaction-list-groups" aria-label="收支明细">
      <p v-if="transactionListError" class="transaction-list-message transaction-list-message-error">
        {{ transactionListError }}
      </p>
      <CommonLoading v-else-if="isLoadingTransactions" />
      <p v-else-if="dayGroups.length === 0" class="transaction-list-message">
        暂无收支记录
      </p>

      <template v-else>
        <TransactionDayCard
          v-for="group in dayGroups"
          :key="group.date"
          :group="group"
          summary-mode="stacked"
          :show-delete="isSelfView"
          :deleting-id="deletingId"
          @edit="openEditTransaction"
          @delete="openDeleteConfirm"
        />

        <div v-if="hasPagination" class="transaction-pagination" aria-label="收支记录分页">
          <button
            class="transaction-pagination-button"
            type="button"
            :disabled="!canGoPrevPage || isLoadingTransactions"
            @click="goToPrevPage"
          >
            上一页
          </button>
          <span class="transaction-pagination-summary">{{ paginationSummary }}</span>
          <button
            class="transaction-pagination-button"
            type="button"
            :disabled="!canGoNextPage || isLoadingTransactions"
            @click="goToNextPage"
          >
            下一页
          </button>
        </div>
      </template>
    </section>

    <CommonConfirmSheet
      v-model="showDeleteConfirmModal"
      title="确认删除"
      message="删除后会同步恢复账户余额，确认删除这条收支记录吗？"
      confirm-text="确认删除"
      :loading="Boolean(deletingId)"
      :error="deleteError"
      @cancel="closeDeleteConfirm"
      @confirm="confirmDeleteTransaction"
    />

    <CommonOptionSheet
      v-model="showTypeSheet"
      title="收支类型"
      :selected-value="activeFilter"
      :options="transactionTypeOptions"
      @select="selectTransactionType"
    />

    <CommonBottomSheet v-model="showDateRangeSheet" title="选择时间">
      <div class="transaction-sheet-options">
        <button
          v-for="option in dateRangeOptions"
          :key="option.value"
          :class="['transaction-sheet-option', { active: option.value === selectedDateRange }]"
          type="button"
          @click="selectDateRange(option.value)"
        >
          <span>
            <strong>{{ option.label }}</strong>
            <small>{{ option.description }}</small>
          </span>
          <i aria-hidden="true"></i>
        </button>
      </div>

      <div v-if="selectedDateRange === 'custom'" class="transaction-custom-range">
        <button type="button" @click="showStartDateSheet = true">
          <span>开始日期</span>
          <strong>{{ customStartDate }}</strong>
        </button>
        <button type="button" @click="showEndDateSheet = true">
          <span>结束日期</span>
          <strong>{{ customEndDate }}</strong>
        </button>
      </div>
    </CommonBottomSheet>

    <CommonOptionSheet
      v-model="showAccountSheet"
      title="选择账户"
      :selected-value="selectedAccountId"
      :options="accountOptions"
      @select="selectAccount"
    />

    <CommonBottomSheet v-model="showStartDateSheet" title="开始日期">
      <label class="transaction-date-field">
        <span>开始日期</span>
        <input v-model="customStartDate" type="date" />
      </label>
    </CommonBottomSheet>

    <CommonBottomSheet v-model="showEndDateSheet" title="结束日期">
      <label class="transaction-date-field">
        <span>结束日期</span>
        <input v-model="customEndDate" type="date" />
      </label>
    </CommonBottomSheet>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
