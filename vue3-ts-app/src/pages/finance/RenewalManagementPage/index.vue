<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  createRenewalSubscription,
  deleteRenewalSubscription,
  getAccounts,
  getCategories,
  getRenewalSubscriptions,
  getRenewalSubscriptionSummary,
  pauseRenewalSubscription,
  resumeRenewalSubscription,
  updateRenewalSubscription,
  type Account,
  type Category,
  type RenewalBillingCycle,
  type RenewalSubscription,
  type RenewalSubscriptionSummary,
  type SaveRenewalSubscriptionParams,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type FilterKey = 'all' | 'due' | 'paused'

type RenewalCardView = {
  id: number
  avatarText: string
  avatarClass: string
  title: string
  statusText: string
  statusClass: string
  noteText: string
  accountText: string
  categoryText: string
  amountText: string
  cycleText: string
  footNote: string
  primaryActionText: string
  primaryActionClass: string
  raw: RenewalSubscription
}

const subscriptions = ref<RenewalSubscription[]>([])
const fundingAccounts = ref<Account[]>([])
const expenseCategories = ref<Category[]>([])
const isManageMode = ref(false)
const summary = ref<RenewalSubscriptionSummary>({
  activeCount: 0,
  pausedCount: 0,
  dueThisMonthCount: 0,
  monthlyAmount: 0,
  dueThisMonthAmount: 0,
})
const isLoading = ref(false)
const isSaving = ref(false)
const isUpdatingStatus = ref(false)
const isDeleting = ref(false)
const pageError = ref('')
const formError = ref('')
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showSubscriptionModal = ref(false)
const showDeleteModal = ref(false)
const editingSubscription = ref<RenewalSubscription | null>(null)
const deletingSubscription = ref<RenewalSubscription | null>(null)
const selectedFilter = ref('all')
const formName = ref('')
const formAmount = ref('')
const formFundingAccountId = ref('')
const formCategoryId = ref('')
const formBillingDay = ref('1')
const formBillingCycle = ref<RenewalBillingCycle>('monthly')
const formNextBillingDate = ref('')
const formStatus = ref<'active' | 'paused'>('active')
const formRemark = ref('')

const filterOptions: Array<{ key: FilterKey; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'due', label: '本月待扣' },
  { key: 'paused', label: '已暂停' },
]

const statusOptions: CommonSelectOption[] = [
  { label: '正常支出', value: 'active' },
  { label: '暂停支出', value: 'paused' },
]

const billingCycleOptions: CommonSelectOption[] = [
  { label: '按月', value: 'monthly' },
  { label: '按季', value: 'quarterly' },
  { label: '按年', value: 'yearly' },
]

const billingDayOptions = computed<CommonSelectOption[]>(() =>
  Array.from({ length: 31 }, (_, index) => ({
    label: `${index + 1} 日`,
    value: String(index + 1),
  })),
)

const fundingAccountOptions = computed<CommonSelectOption[]>(() => {
  if (!fundingAccounts.value.length) {
    return [{ label: '暂无可用现金账户', value: '', disabled: true }]
  }

  return fundingAccounts.value.map((account) => ({
    label: `${account.name} · ${formatCurrency(Number(account.currentBalance ?? 0))}`,
    value: String(account.id),
  }))
})

const categoryOptions = computed<CommonSelectOption[]>(() => {
  const leafCategories = expenseCategories.value.filter(isLeafCategory)

  if (!leafCategories.length) {
    return [{ label: '暂无可用支出分类', value: '', disabled: true }]
  }

  return leafCategories.map((category) => ({
    label: buildCategoryOptionLabel(category),
    value: String(category.id),
  }))
})

const modalTitle = computed(() => editingSubscription.value ? '修改固定支出' : '新增固定支出')
const submitLabel = computed(() => editingSubscription.value ? '保存修改' : '新增固定支出')
const billingDayLabel = computed(() => formBillingCycle.value === 'monthly' ? '支出日' : '每期支出日')

const activeSubscriptions = computed(() => subscriptions.value.filter((item) => item.status === 'active'))

const filteredSubscriptions = computed(() => {
  if (selectedFilter.value === 'paused') {
    return subscriptions.value.filter((item) => item.status === 'paused')
  }
  if (selectedFilter.value === 'due') {
    return subscriptions.value.filter((item) => item.status === 'active' && isDueThisMonth(item.nextBillingDate))
  }
  return subscriptions.value
})

const annualFixedExpenseAmount = computed(() =>
  activeSubscriptions.value.reduce((total, item) => {
    const amount = Number(item.amount ?? 0)
    if (!Number.isFinite(amount)) {
      return total
    }
    return total + amount * getAnnualCycleMultiplier(item.billingCycle)
  }, 0),
)

const monthlyAverageAmount = computed(() => annualFixedExpenseAmount.value / 12)

const renewalCards = computed<RenewalCardView[]>(() =>
  filteredSubscriptions.value.map((subscription, index) => {
    const dueDays = getDaysUntilDue(subscription.nextBillingDate)
    const isPaused = subscription.status === 'paused'
    const statusText = isPaused
      ? '已暂停'
      : dueDays === 0
        ? '今日支出'
        : dueDays > 0 && dueDays <= 7
          ? `${dueDays} 天后`
          : ''

    const footNote = subscription.lastChargeStatus === 'failed' && subscription.lastChargeMessage
      ? subscription.lastChargeMessage
      : subscription.lastChargedAt
        ? `上次扣款 ${formatDotDate(subscription.lastChargedAt)} 成功`
        : isPaused
          ? '暂停后不再自动扣款'
          : `下次支出 ${formatDotDate(subscription.nextBillingDate)}`

    return {
      id: subscription.id,
      avatarText: resolveAvatarText(subscription),
      avatarClass: `renewal-avatar-${index % 3}`,
      title: resolveCardTitle(subscription),
      statusText,
      statusClass: isPaused
        ? 'renewal-card-status-paused'
        : dueDays === 0
          ? 'renewal-card-status-today'
          : dueDays > 0 && dueDays <= 7
            ? 'renewal-card-status-soon'
            : 'renewal-card-status-active',
      noteText: formatBillingSchedule(subscription.billingDay, subscription.billingCycle),
      accountText: `扣款账户 ${subscription.fundingAccountName || '--'}`,
      categoryText: subscription.categoryName || '未设置分类',
      amountText: `${formatCurrencyPlain(Number(subscription.amount ?? 0))}/${getBillingCycleUnit(subscription.billingCycle)}`,
      cycleText: isPaused ? '手动恢复' : getBillingCycleLabel(subscription.billingCycle),
      footNote,
      primaryActionText: isPaused ? '恢复' : '暂停',
      primaryActionClass: isPaused ? 'is-success' : 'is-warning',
      raw: subscription,
    }
  }),
)

onMounted(() => {
  void loadPage()
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看固定支出'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [subscriptionList, summaryData, accountList, categoryList] = await Promise.all([
      getRenewalSubscriptions({ userId: currentUser.id }),
      getRenewalSubscriptionSummary(currentUser.id),
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getCategories({ userId: currentUser.id, type: 'expense', status: 'active' }),
    ])
    subscriptions.value = subscriptionList
    summary.value = summaryData
    fundingAccounts.value = accountList.filter((account) => account.accountTypeCode === 'cash')
    expenseCategories.value = categoryList
    if (!formFundingAccountId.value && fundingAccounts.value.length > 0) {
      formFundingAccountId.value = String(fundingAccounts.value[0].id)
    }
    if (!formCategoryId.value) {
      formCategoryId.value = resolveDefaultCategoryId()
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '固定支出加载失败'
  } finally {
    isLoading.value = false
  }
}

function openCreateModal() {
  editingSubscription.value = null
  resetForm()
  formFundingAccountId.value = fundingAccounts.value[0] ? String(fundingAccounts.value[0].id) : ''
  formCategoryId.value = resolveDefaultCategoryId()
  formBillingDay.value = String(new Date().getDate())
  formBillingCycle.value = 'monthly'
  formNextBillingDate.value = ''
  showSubscriptionModal.value = true
}

function openEditModal(subscription: RenewalSubscription) {
  editingSubscription.value = subscription
  formName.value = subscription.name
  formAmount.value = String(Number(subscription.amount ?? 0))
  formFundingAccountId.value = String(subscription.fundingAccountId)
  formCategoryId.value = subscription.categoryId ? String(subscription.categoryId) : resolveDefaultCategoryId()
  formBillingDay.value = String(subscription.billingDay)
  formBillingCycle.value = subscription.billingCycle || 'monthly'
  formNextBillingDate.value = subscription.nextBillingDate
  formStatus.value = subscription.status === 'paused' ? 'paused' : 'active'
  formRemark.value = subscription.remark ?? ''
  formError.value = ''
  showSubscriptionModal.value = true
}

function closeSubscriptionModal(force = false) {
  if (isSaving.value && !force) {
    return
  }
  showSubscriptionModal.value = false
  editingSubscription.value = null
  resetForm()
}

function resetForm() {
  formName.value = ''
  formAmount.value = ''
  formFundingAccountId.value = ''
  formCategoryId.value = ''
  formBillingDay.value = '1'
  formBillingCycle.value = 'monthly'
  formNextBillingDate.value = ''
  formStatus.value = 'active'
  formRemark.value = ''
  formError.value = ''
}

function openDeleteModal(subscription: RenewalSubscription) {
  if (isDeleting.value) {
    return
  }

  deletingSubscription.value = subscription
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal() {
  if (isDeleting.value) {
    return
  }

  showDeleteModal.value = false
  deletingSubscription.value = null
  deleteError.value = ''
}

async function saveSubscription() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后再保存固定支出'
    return
  }

  const amount = Number(formAmount.value)
  const fundingAccountId = Number(formFundingAccountId.value)
  const categoryId = Number(formCategoryId.value)
  const billingDay = Number(formBillingDay.value)

  if (!formName.value.trim()) {
    formError.value = '请输入固定支出名称'
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    formError.value = '请输入大于0的固定支出金额'
    return
  }
  if (!Number.isFinite(fundingAccountId) || fundingAccountId <= 0) {
    formError.value = '请选择扣款账户'
    return
  }
  if (!Number.isFinite(categoryId) || categoryId <= 0) {
    formError.value = '请选择扣款分类'
    return
  }
  if (!Number.isInteger(billingDay) || billingDay < 1 || billingDay > 31) {
    formError.value = '支出日必须在1到31之间'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const payload: SaveRenewalSubscriptionParams = {
      userId: currentUser.id,
      name: formName.value.trim(),
      providerName: null,
      amount,
      currencyCode: 'CNY',
      fundingAccountId,
      categoryId,
      billingDay,
      billingCycle: formBillingCycle.value,
      nextBillingDate: formNextBillingDate.value || undefined,
      status: formStatus.value,
      remark: formRemark.value.trim() || null,
    }

    if (editingSubscription.value) {
      await updateRenewalSubscription(editingSubscription.value.id, payload)
      showFeedback('固定支出已更新', 'success')
    } else {
      await createRenewalSubscription(payload)
      showFeedback('固定支出已新增', 'success')
    }

    closeSubscriptionModal(true)
    await loadPage()
  } catch (error) {
    const message = error instanceof Error ? error.message : '固定支出保存失败'
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function toggleSubscriptionStatus(subscription: RenewalSubscription) {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isUpdatingStatus.value) {
    return
  }

  isUpdatingStatus.value = true
  try {
    if (subscription.status === 'paused') {
      await resumeRenewalSubscription(subscription.id, currentUser.id)
      showFeedback('固定支出已恢复', 'success')
    } else {
      await pauseRenewalSubscription(subscription.id, currentUser.id)
      showFeedback('固定支出已暂停', 'success')
    }
    await loadPage()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '固定支出状态更新失败', 'error')
  } finally {
    isUpdatingStatus.value = false
  }
}

async function confirmDeleteSubscription() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !deletingSubscription.value || isDeleting.value) {
    return
  }

  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteRenewalSubscription(deletingSubscription.value.id, currentUser.id)
    showDeleteModal.value = false
    deletingSubscription.value = null
    showFeedback('固定支出已删除', 'success')
    await loadPage()
  } catch (error) {
    const message = error instanceof Error ? error.message : '固定支出删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function isDueThisMonth(nextBillingDate: string) {
  const date = new Date(nextBillingDate)
  if (Number.isNaN(date.getTime())) {
    return false
  }
  const now = new Date()
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
}

function getDaysUntilDue(nextBillingDate: string) {
  const target = new Date(nextBillingDate)
  if (Number.isNaN(target.getTime())) {
    return Number.POSITIVE_INFINITY
  }
  const today = new Date()
  const current = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const due = new Date(target.getFullYear(), target.getMonth(), target.getDate())
  return Math.round((due.getTime() - current.getTime()) / 86400000)
}

function formatDotDate(value?: string | null) {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--'
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}.${month}.${day}`
}

function formatBillingSchedule(day: number, cycle?: RenewalBillingCycle | string | null) {
  const displayDay = String(Number(day))
  if (cycle === 'quarterly') {
    return `每季度 ${displayDay} 号扣款`
  }
  if (cycle === 'yearly') {
    return `每年 ${displayDay} 号扣款`
  }
  return `每月 ${displayDay} 号扣款`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value))
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(value)
}

function formatCurrencyPlain(value: number) {
  return `¥${formatNumber(value)}`
}

function getBillingCycleLabel(cycle?: RenewalBillingCycle | string | null) {
  if (cycle === 'quarterly') {
    return '连续包季'
  }
  if (cycle === 'yearly') {
    return '连续包年'
  }
  return '连续包月'
}

function getBillingCycleUnit(cycle?: RenewalBillingCycle | string | null) {
  if (cycle === 'quarterly') {
    return '季'
  }
  if (cycle === 'yearly') {
    return '年'
  }
  return '月'
}

function getAnnualCycleMultiplier(cycle?: RenewalBillingCycle | string | null) {
  if (cycle === 'quarterly') {
    return 4
  }
  if (cycle === 'yearly') {
    return 1
  }
  return 12
}

function resolveAvatarText(subscription: RenewalSubscription) {
  const source = (subscription.name || '续').trim()
  return source.slice(0, 1)
}

function resolveCardTitle(subscription: RenewalSubscription) {
  return subscription.name.trim()
}

function buildCategoryOptionLabel(category: Category) {
  if (!category.parentId) {
    return category.name
  }
  const parent = expenseCategories.value.find((item) => item.id === category.parentId)
  return parent ? `${parent.name} / ${category.name}` : category.name
}

function isLeafCategory(category: Category) {
  return !expenseCategories.value.some((item) => item.parentId === category.id)
}

function resolveDefaultCategoryId() {
  const renewalCategory = expenseCategories.value.find((category) => (
    (category.name === '固定支出' || category.name === '会员续费') && isLeafCategory(category)
  ))
  if (renewalCategory) {
    return String(renewalCategory.id)
  }
  const firstCategory = expenseCategories.value.find(isLeafCategory)
  return firstCategory ? String(firstCategory.id) : ''
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="renewal-management-page" aria-label="固定支出">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <p v-if="pageError" class="renewal-message renewal-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <PageHeader title="固定支出" back-to="/finance/more-features" back-label="返回更多功能">
        <template #right>
          <div class="renewal-header-actions">
            <CommonHeaderActionButton
              :label="isManageMode ? '完成管理' : '管理固定支出'"
              @click="toggleManageMode"
            >
              <svg v-if="isManageMode" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M20 6L9 17L4 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z" stroke="currentColor" stroke-width="1.8" />
                <path d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </CommonHeaderActionButton>
          </div>
        </template>
      </PageHeader>

      <section class="renewal-summary-card" aria-label="固定支出总览">
        <div class="renewal-summary-stats">
          <article class="renewal-summary-metric">
            <span>本月总额</span>
            <strong>{{ formatCurrency(summary.dueThisMonthAmount) }}</strong>
          </article>
          <article class="renewal-summary-metric">
            <span>月平均</span>
            <strong>{{ formatCurrency(monthlyAverageAmount) }}</strong>
          </article>
          <article class="renewal-summary-metric">
            <span>年度总额</span>
            <strong>{{ formatCurrency(annualFixedExpenseAmount) }}</strong>
          </article>
        </div>
      </section>

      <SegmentedControl
        v-model="selectedFilter"
        class="renewal-filter-tabs"
        :options="filterOptions.map((item) => ({ label: item.label, value: item.key }))"
        label="固定支出筛选"
      />

      <section class="renewal-list-section" aria-label="固定支出列表">
        <div v-if="renewalCards.length" class="renewal-list">
          <article
            v-for="card in renewalCards"
            :key="card.id"
            class="renewal-card"
          >
            <div class="renewal-card-top">
              <div class="renewal-card-main">
                <span :class="['renewal-card-avatar', card.avatarClass]">{{ card.avatarText }}</span>
                <div class="renewal-card-text-wrap">
                  <div class="renewal-card-title-row">
                    <h3>{{ card.title }}</h3>
                    <span v-if="card.statusText" :class="['renewal-card-status', card.statusClass]">
                      {{ card.statusText }}
                    </span>
                  </div>
                  <p>{{ card.noteText }}</p>
                  <p>{{ card.accountText }}</p>
                </div>
              </div>

              <div class="renewal-card-side">
                <strong class="renewal-card-amount">{{ card.amountText }}</strong>
                <span class="renewal-card-cycle">{{ card.cycleText }}</span>
                <span class="renewal-card-category">{{ card.categoryText }}</span>
              </div>
            </div>

            <div class="renewal-card-foot">
              <p :class="['renewal-card-foot-note', { 'renewal-card-hint-error': card.raw.lastChargeStatus === 'failed' }]">
                {{ card.footNote }}
              </p>
            </div>

            <div v-if="isManageMode" class="renewal-card-actions">
              <CommonButton
                class="renewal-card-action"
                variant="secondary"
                size="sm"
                :aria-label="`修改${card.title}`"
                @click="openEditModal(card.raw)"
              >
                修改
              </CommonButton>
              <CommonButton
                class="renewal-card-action is-danger"
                variant="secondary"
                size="sm"
                :aria-label="`删除${card.title}`"
                @click="openDeleteModal(card.raw)"
              >
                删除
              </CommonButton>
              <CommonButton
                :class="['renewal-card-action', card.primaryActionClass]"
                variant="secondary"
                size="sm"
                :aria-label="`${card.primaryActionText}${card.title}`"
                :disabled="isUpdatingStatus"
                @click="toggleSubscriptionStatus(card.raw)"
              >
                {{ card.primaryActionText }}
              </CommonButton>
            </div>
          </article>
        </div>

        <div v-else class="renewal-empty-state">
          <strong>还没有固定支出</strong>
          <p>新增房租、会员、保险等周期性支出后，会在到期日自动生成支出并扣减现金账户余额。</p>
          <CommonButton @click="openCreateModal">新增固定支出</CommonButton>
        </div>
      </section>
    </template>

    <CommonModal
      v-model="showSubscriptionModal"
      :title="modalTitle"
      @close="closeSubscriptionModal(true)"
    >
      <div class="renewal-form">
        <CommonInput v-model="formName" label="支出名称" placeholder="例如：房租、会员、保险" />
        <CommonInput
          v-model="formAmount"
          label="每期金额"
          placeholder="请输入固定支出金额"
          input-type="number"
          input-mode="decimal"
        />
        <CommonSelect v-model="formBillingCycle" label="支出周期" :options="billingCycleOptions" />
        <CommonSelect v-model="formFundingAccountId" label="扣款账户" :options="fundingAccountOptions" />
        <CommonSelect v-model="formCategoryId" label="扣款分类" :options="categoryOptions" />
        <CommonSelect v-model="formBillingDay" :label="billingDayLabel" :options="billingDayOptions" />
        <CommonInput
          v-model="formNextBillingDate"
          label="首次或下次支出日"
          input-type="date"
        />
        <CommonSelect v-model="formStatus" label="支出状态" :options="statusOptions" />
        <CommonInput v-model="formRemark" label="备注" placeholder="可记录房租、会员、保费等固定支出信息" />
        <p v-if="formError" class="renewal-form-error">{{ formError }}</p>
      </div>

      <template #footer>
        <div class="renewal-modal-actions">
          <CommonButton variant="secondary" @click="closeSubscriptionModal">
            取消
          </CommonButton>
          <CommonButton :disabled="isSaving" @click="saveSubscription">
            {{ submitLabel }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteModal"
      title="确认删除"
      size="compact"
      :show-close="false"
    >
      <p class="renewal-delete-text">
        确认删除“{{ deletingSubscription?.name ?? '当前固定支出' }}”吗？删除后无法恢复。
      </p>
      <p v-if="deleteError" class="renewal-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="renewal-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteModal">
            取消
          </CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeleteSubscription">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <FloatingAddButton
      aria-label="新增固定支出"
      storage-key="finance-renewals"
      @click="openCreateModal"
    />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
