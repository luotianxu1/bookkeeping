<script setup lang="ts">
import TransactionTable from '@/business/components/TransactionTable.vue'
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'

import { formatCnyAmount as formatAmount } from '@/business/utils/format'
// 收支工作台：组合筛选、汇总和服务端分页流水。
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  deleteTransaction,
  getAccounts,
  getAccountTypes,
  getTransactionPage,
  getCategories,
  type Category,
  type Account,
  type Transaction,
  type TransactionType,
} from '@/business/api/modules/finance'

import CommonConfirmSheet from '@/business/components/common/CommonConfirmSheet/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { useFinanceFamilyView } from '@/business/composables/useFinanceFamilyView'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { buildTransactionDayGroups } from '@/business/utils/transaction-day-groups'
import type { Transaction as DayTransaction } from '@/business/types/finance'

import { getErrorMessage } from '@/business/api/request'

const filterOptions = ['全部', '收入', '支出']
const TRANSACTION_PAGE_SIZE = 50
type DateRangeKey = 'month' | 'lastMonth' | 'threeMonths' | 'custom' | 'all'
type SortOrder = 'desc' | 'asc'
type SheetOption = {
  label: string
  value: string
  description?: string
}

const dateRangeOptions: SheetOption[] = [
  { label: '本月', value: 'month', description: '查看当前自然月收支' },
  { label: '上月', value: 'lastMonth', description: '查看上一个自然月收支' },
  {
    label: '近 3 个月',
    value: 'threeMonths',
    description: '从近三个月首日到今天',
  },
  {
    label: '自定义时间',
    value: 'custom',
    description: '手动选择开始和结束日期',
  },
  { label: '全部时间', value: 'all', description: '搜索全部历史收支' },
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
const isLoadingTransactions = ref(true)
const transactionListError = ref('')
const deletingId = ref<number | null>(null)
const pendingDeleteTransaction = ref<DayTransaction | null>(null)
const showDeleteConfirmModal = ref(false)

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
const searchInput = ref(''),
  keyword = ref('')
const selectedCategoryId = ref('all'),
  minAmount = ref(''),
  maxAmount = ref('')
const draftCategoryId = ref('all'),
  draftMinAmount = ref(''),
  draftMaxAmount = ref('')
const categoriesLoading = ref(false)
const categories = ref<Category[]>([]),
  categoriesError = ref(''),
  filterError = ref('')
let categorySerial = 0

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
const dayGroups = computed(() =>
  buildTransactionDayGroups(transactions.value, selectedSortOrder.value),
)

const familyViewSelectOptions = computed<CommonSelectOption[]>(() =>
  familyViewOptions.value.map((option) => ({
    label: option.label,
    value: option.value,
  })),
)

const categoryOptions = computed(() => [
  { value: 'all', label: '全部分类' },
  ...categories.value.map((category) => {
    const parent = categories.value.find(
      (item) => item.id === category.parentId,
    )
    const hasChildren = categories.value.some(
      (item) => item.parentId === category.id,
    )
    const owner =
      selectedFamilyView.value.kind === 'total' && category.userId
        ? viewerNameByUserId.value.get(category.userId)
        : ''
    return {
      value: String(category.id),
      label:
        [
          owner,
          category.type === 'income' ? '收入' : '支出',
          parent?.name,
          category.name,
        ]
          .filter(Boolean)
          .join(' / ') + (hasChildren ? '（全部子分类）' : ''),
    }
  }),
])
const paginationSummary = computed(
  () =>
    `第 ${currentPage.value} / ${totalPages.value} 页，共 ${totalItems.value} 条`,
)

const accountOptions = computed<SheetOption[]>(() => [
  { label: '全部账户', value: 'all', description: '包含所有现金账户' },
  ...cashAccounts.value.map((account) => ({
    label: accountLabel(account),
    value: String(account.id),
    description: account.accountTypeName ?? '现金账户',
  })),
])

const selectedSortLabel = computed(() =>
  selectedSortOrder.value === 'desc' ? '倒序' : '正序',
)
const resolvedDateRange = computed(() => {
  if (selectedDateRange.value === 'all') return { startDate: '', endDate: '' }
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

onMounted(() => {
  void initializePage()
})

watch(familyView, async () => {
  ++categorySerial
  selectedCategoryId.value = 'all'
  categories.value = []
  currentPage.value = 1
  selectedAccountId.value = 'all'
  if (showDeleteConfirmModal.value && !deletingId.value) {
    closeDeleteConfirm()
  }
  await loadCashAccountOptions()
  await loadCategoryOptions()
  await loadCashTransactions()
})

watch(
  [
    activeFilter,
    selectedDateRange,
    selectedAccountId,
    selectedSortOrder,
    customStartDate,
    customEndDate,
    keyword,
    selectedCategoryId,
    minAmount,
    maxAmount,
  ],
  () => {
    currentPage.value = 1
    void loadCashTransactions()
  },
)

async function initializePage() {
  await loadFamilyMembers()
  await loadCashAccountOptions()
  await loadCategoryOptions()
  await loadCashTransactions()
}

function clearSearchFilters() {
  searchInput.value = ''
  keyword.value = ''
  selectedCategoryId.value = 'all'
  minAmount.value = ''
  maxAmount.value = ''
}

async function loadCategoryOptions() {
  const ticket = ++categorySerial
  categoriesLoading.value = true
  categoriesError.value = ''
  try {
    const groups = await Promise.all(
      selectedViewerUserIds.value.map((userId) => getCategories({ userId })),
    )
    if (ticket !== categorySerial) return
    categories.value = [
      ...new Map(
        groups.flat().map((category) => [category.id, category]),
      ).values(),
    ]
  } catch (cause) {
    if (ticket === categorySerial)
      categoriesError.value = getErrorMessage(cause, '分类加载失败')
  } finally {
    if (ticket === categorySerial) categoriesLoading.value = false
  }
}
function applyAdvancedFilters() {
  const min = draftMinAmount.value.trim(),
    max = draftMaxAmount.value.trim()
  if (
    [min, max].some(
      (value) =>
        value &&
        (!/^\d{1,16}(\.\d{1,2})?$/.test(value) ||
          !Number.isFinite(Number(value))),
    )
  ) {
    filterError.value = '金额范围须为非负数，最多两位小数'
    return
  }
  if (min && max && Number(min) > Number(max)) {
    filterError.value = '最低金额不能大于最高金额'
    return
  }
  selectedCategoryId.value = draftCategoryId.value
  minAmount.value = min
  maxAmount.value = max
  keyword.value = searchInput.value.trim()
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
    accountGroups
      .flat()
      .forEach((account) => accountMap.set(account.id, account))
    cashAccounts.value = Array.from(accountMap.values())
  } catch {
    cashAccounts.value = []
  }
}

function openEditTransaction(transaction: DayTransaction) {
  if (!isSelfView.value) {
    return
  }

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
    isLoadingTransactions.value = false
    return
  }

  const requestSerial = transactionRequestSerial.value + 1
  transactionRequestSerial.value = requestSerial
  isLoadingTransactions.value = true
  transactionListError.value = ''

  try {
    const pageData = await getTransactionPage({
      userIds: selectedViewerUserIds.value.join(','),
      keyword: keyword.value || undefined,
      categoryId:
        selectedCategoryId.value === 'all'
          ? undefined
          : Number(selectedCategoryId.value),
      minAmount: minAmount.value ? Number(minAmount.value) : undefined,
      maxAmount: maxAmount.value ? Number(maxAmount.value) : undefined,
      type: currentTransactionType.value,
      accountId:
        selectedAccountId.value === 'all'
          ? undefined
          : Number(selectedAccountId.value),
      cashOnly: true,
      startDate: resolvedDateRange.value.startDate || undefined,
      endDate: resolvedDateRange.value.endDate || undefined,
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
        accountName:
          isReadOnlyFamilyView.value &&
          selectedFamilyView.value.kind === 'total' &&
          viewerName
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
    transactionListError.value = getErrorMessage(error, '收支记录加载失败')
  } finally {
    if (requestSerial === transactionRequestSerial.value) {
      isLoadingTransactions.value = false
    }
  }
}

function openDeleteConfirm(transaction: DayTransaction) {
  if (!isSelfView.value) {
    return
  }

  pendingDeleteTransaction.value = transaction
  deleteError.value = ''
  showDeleteConfirmModal.value = true
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
    startDate: formatInputDate(
      new Date(now.getFullYear(), now.getMonth() - 2, 1),
    ),
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

function formatPositiveAmount(value: number) {
  return `+${formatAmount(Math.abs(value))}`
}

function formatSignedAmount(value: number) {
  if (value > 0) {
    return formatPositiveAmount(value)
  }
  if (value < 0) {
    return `-${formatAmount(Math.abs(value))}`
  }
  return formatAmount(0)
}

function toNumber(value: unknown) {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) ? parsed : 0
}

function accountLabel(account: Account) {
  const viewerName = viewerNameByUserId.value.get(account.userId)
  return isReadOnlyFamilyView.value &&
    selectedFamilyView.value.kind === 'total' &&
    viewerName
    ? `${viewerName} · ${account.name}`
    : account.name
}

function resetDesktopFilters() {
  activeFilter.value = filterOptions[0]
  selectedDateRange.value = 'month'
  selectedAccountId.value = 'all'
  selectedSortOrder.value = 'desc'
  draftCategoryId.value = 'all'
  draftMinAmount.value = ''
  draftMaxAmount.value = ''
  filterError.value = ''
  clearSearchFilters()
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="收支流水">
      <template #right>
        <el-button
          :loading="isLoadingTransactions"
          @click="loadCashTransactions"
        >
          刷新
        </el-button>
        <el-button
          v-if="isSelfView"
          type="primary"
          @click="router.push('/finance/entry/expense')"
        >
          记一笔
        </el-button>
      </template>
    </PageHeader>
    <PagePanel title="查询条件">
      <el-form
        class="desktop-filter-form"
        label-position="top"
        @submit.prevent="applyAdvancedFilters"
      >
        <CommonSelect
          v-if="canSwitchFamilyView"
          v-model="familyView"
          label="账本范围"
          :options="familyViewSelectOptions"
        />
        <CommonSelect
          v-model="activeFilter"
          label="收支类型"
          :options="transactionTypeOptions"
        />
        <CommonSelect
          v-model="selectedDateRange"
          label="时间范围"
          :options="dateRangeOptions"
        />
        <CommonSelect
          v-model="selectedAccountId"
          label="账户"
          :options="accountOptions"
        />
        <CommonInput
          v-if="selectedDateRange === 'custom'"
          v-model="customStartDate"
          label="开始日期"
          input-type="date"
        />
        <CommonInput
          v-if="selectedDateRange === 'custom'"
          v-model="customEndDate"
          label="结束日期"
          input-type="date"
        />
        <CommonSelect
          v-model="draftCategoryId"
          label="分类"
          :options="categoryOptions"
          :disabled="categoriesLoading"
        />
        <CommonInput
          v-model="searchInput"
          label="关键词"
          placeholder="名称 / 备注"
        />
        <CommonInput
          v-model="draftMinAmount"
          label="最低金额"
          input-type="number"
        />
        <CommonInput
          v-model="draftMaxAmount"
          label="最高金额"
          input-type="number"
        />
        <div class="desktop-filter-actions">
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="resetDesktopFilters">重置</el-button>
          <el-button @click="toggleSortOrder">
            {{ selectedSortLabel }}
          </el-button>
        </div>
      </el-form>
      <el-alert
        v-if="filterError"
        :title="filterError"
        type="error"
        :closable="false"
        show-icon
      ></el-alert>
      <el-alert
        v-if="categoriesError"
        :title="categoriesError"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="loadCategoryOptions">
          重试
        </el-button>
      </el-alert>
    </PagePanel>
    <MetricStrip
      :items="[
        {
          label: '收入',
          value: formatPositiveAmount(summaryIncomeTotal),
          tone: 'success',
        },
        { label: '净支出', value: formatAmount(summaryExpenseTotal) },
        { label: '结余', value: formatSignedAmount(summaryBalanceTotal) },
        { label: '流水笔数', value: totalItems },
      ]"
    />
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
    <TransactionTable
      :rows="dayGroups.flatMap((group) => group.transactions)"
      :loading="isLoadingTransactions"
      :paginate="false"
      title="流水明细"
      :show-delete="isSelfView"
      :deleting-id="deletingId"
      @edit="openEditTransaction"
      @delete="openDeleteConfirm"
    />
    <div class="desktop-pagination">
      <span>{{ paginationSummary }}</span>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="TRANSACTION_PAGE_SIZE"
        :total="totalItems"
        layout="prev, pager, next, jumper"
        @current-change="loadCashTransactions"
      />
    </div>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
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
  </section>
</template>
