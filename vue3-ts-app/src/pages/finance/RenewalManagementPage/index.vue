<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createRenewalSubscription,
  deleteRenewalSubscription,
  getAccounts,
  getRenewalSubscriptions,
  getRenewalSubscriptionSummary,
  pauseRenewalSubscription,
  resumeRenewalSubscription,
  updateRenewalSubscription,
  type Account,
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
  amountText: string
  cycleText: string
  footNote: string
  primaryActionText: string
  primaryActionClass: string
  raw: RenewalSubscription
}

const subscriptions = ref<RenewalSubscription[]>([])
const fundingAccounts = ref<Account[]>([])
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
const selectedFilter = ref<FilterKey>('all')
const formName = ref('')
const formAmount = ref('')
const formFundingAccountId = ref('')
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
  { label: '正常扣费', value: 'active' },
  { label: '暂停扣费', value: 'paused' },
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

const modalTitle = computed(() => editingSubscription.value ? '修改续费计划' : '新增续费计划')
const submitLabel = computed(() => editingSubscription.value ? '保存修改' : '新增计划')
const billingDayLabel = computed(() => formBillingCycle.value === 'monthly' ? '扣费日' : '每期扣费日')

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

const todayDueSubscriptions = computed(() =>
  activeSubscriptions.value.filter((item) => isDueToday(item.nextBillingDate)),
)

const weekDueSubscriptions = computed(() =>
  activeSubscriptions.value.filter((item) => {
    const days = getDaysUntilDue(item.nextBillingDate)
    return days >= 0 && days <= 7
  }),
)

const chargedThisMonthAmount = computed(() =>
  subscriptions.value.reduce((total, item) => {
    if (!didChargeThisMonth(item.lastChargedAt)) {
      return total
    }
    return total + Number(item.amount ?? 0)
  }, 0),
)

const renewalCards = computed<RenewalCardView[]>(() =>
  filteredSubscriptions.value.map((subscription, index) => {
    const dueDays = getDaysUntilDue(subscription.nextBillingDate)
    const isPaused = subscription.status === 'paused'
    const statusText = isPaused
      ? '已暂停'
      : dueDays === 0
        ? '今日扣费'
        : dueDays > 0 && dueDays <= 7
          ? `${dueDays} 天后`
          : '自动扣费'

    const footNote = subscription.lastChargeStatus === 'failed' && subscription.lastChargeMessage
      ? subscription.lastChargeMessage
      : subscription.lastChargedAt
        ? `上次扣费 ${formatDotDate(subscription.lastChargedAt)} 成功`
        : isPaused
          ? '暂停后不再自动扣费'
          : `下次扣费 ${formatDotDate(subscription.nextBillingDate)}`

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
      noteText: `${formatBillingSchedule(subscription.billingDay, subscription.billingCycle)}自动续费 · 扣款账户 ${subscription.fundingAccountName || '--'}`,
      amountText: `${formatCurrencyPlain(Number(subscription.amount ?? 0))}/${getBillingCycleUnit(subscription.billingCycle)}`,
      cycleText: isPaused ? '手动恢复' : getBillingCycleLabel(subscription.billingCycle),
      footNote,
      primaryActionText: isPaused ? '恢复' : '暂停',
      primaryActionClass: isPaused ? 'is-success' : 'is-danger',
      raw: subscription,
    }
  }),
)

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看续费管理'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [subscriptionList, summaryData, accountList] = await Promise.all([
      getRenewalSubscriptions({ userId: currentUser.id }),
      getRenewalSubscriptionSummary(currentUser.id),
      getAccounts({ userId: currentUser.id, status: 'active' }),
    ])
    subscriptions.value = subscriptionList
    summary.value = summaryData
    fundingAccounts.value = accountList.filter((account) => account.accountTypeCode === 'cash')
    if (!formFundingAccountId.value && fundingAccounts.value.length > 0) {
      formFundingAccountId.value = String(fundingAccounts.value[0].id)
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '续费计划加载失败'
  } finally {
    isLoading.value = false
  }
}

function openCreateModal() {
  editingSubscription.value = null
  resetForm()
  formFundingAccountId.value = fundingAccounts.value[0] ? String(fundingAccounts.value[0].id) : ''
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
    formError.value = '请先登录后再保存续费计划'
    return
  }

  const amount = Number(formAmount.value)
  const fundingAccountId = Number(formFundingAccountId.value)
  const billingDay = Number(formBillingDay.value)

  if (!formName.value.trim()) {
    formError.value = '请输入续费名称'
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    formError.value = '请输入大于0的续费金额'
    return
  }
  if (!Number.isFinite(fundingAccountId) || fundingAccountId <= 0) {
    formError.value = '请选择扣费账户'
    return
  }
  if (!Number.isInteger(billingDay) || billingDay < 1 || billingDay > 31) {
    formError.value = '扣费日必须在1到31之间'
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
      billingDay,
      billingCycle: formBillingCycle.value,
      nextBillingDate: formNextBillingDate.value || undefined,
      status: formStatus.value,
      remark: formRemark.value.trim() || null,
    }

    if (editingSubscription.value) {
      await updateRenewalSubscription(editingSubscription.value.id, payload)
      showFeedback('续费计划已更新', 'success')
    } else {
      await createRenewalSubscription(payload)
      showFeedback('续费计划已新增', 'success')
    }

    closeSubscriptionModal(true)
    await loadPage()
  } catch (error) {
    const message = error instanceof Error ? error.message : '续费计划保存失败'
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
      showFeedback('续费计划已恢复', 'success')
    } else {
      await pauseRenewalSubscription(subscription.id, currentUser.id)
      showFeedback('续费计划已暂停', 'success')
    }
    await loadPage()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '续费状态更新失败', 'error')
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
    showFeedback('续费计划已删除', 'success')
    await loadPage()
  } catch (error) {
    const message = error instanceof Error ? error.message : '续费计划删除失败'
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

function isDueToday(nextBillingDate: string) {
  return getDaysUntilDue(nextBillingDate) === 0
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

function didChargeThisMonth(value?: string | null) {
  if (!value) {
    return false
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return false
  }
  const now = new Date()
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
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
  const displayDay = String(day).padStart(2, '0')
  if (cycle === 'quarterly') {
    return `每季 ${displayDay} 日`
  }
  if (cycle === 'yearly') {
    return `每年 ${displayDay} 日`
  }
  return `每月 ${displayDay} 日`
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

function resolveAvatarText(subscription: RenewalSubscription) {
  const source = (subscription.name || '续').trim()
  return source.slice(0, 1)
}

function resolveCardTitle(subscription: RenewalSubscription) {
  return subscription.name.trim()
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="renewal-management-page" aria-label="续费管理">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <p v-if="pageError" class="renewal-message renewal-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <PageHeader title="续费管理" back-to="/finance/more-features" back-label="返回更多功能">
        <button type="button" class="renewal-create-action" @click="openCreateModal">新增</button>
      </PageHeader>

      <section class="renewal-summary-card" aria-label="续费总览">
        <AmountText
          tag="strong"
          class="renewal-summary-amount"
          :value="formatCurrency(summary.monthlyAmount)"
          tone="inherit"
        />

        <div class="renewal-summary-stats">
          <article class="renewal-summary-metric">
            <span>今日待扣</span>
            <strong>{{ todayDueSubscriptions.length }} 项</strong>
          </article>
          <article class="renewal-summary-metric">
            <span>本周待扣</span>
            <strong>{{ weekDueSubscriptions.length }} 项</strong>
          </article>
          <article class="renewal-summary-metric">
            <span>已扣金额</span>
            <strong>{{ formatCurrency(chargedThisMonthAmount) }}</strong>
          </article>
        </div>
      </section>

      <nav class="renewal-filters" aria-label="续费筛选">
        <button
          v-for="item in filterOptions"
          :key="item.key"
          type="button"
          :class="['renewal-filter-chip', { 'renewal-filter-chip-active': selectedFilter === item.key }]"
          @click="selectedFilter = item.key"
        >
          {{ item.label }}
        </button>
      </nav>

      <section class="renewal-list-section" aria-label="续费列表">
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
                    <span :class="['renewal-card-status', card.statusClass]">{{ card.statusText }}</span>
                  </div>
                  <p>{{ card.noteText }}</p>
                </div>
              </div>

              <div class="renewal-card-side">
                <strong class="renewal-card-amount">{{ card.amountText }}</strong>
                <span class="renewal-card-cycle">{{ card.cycleText }}</span>
              </div>
            </div>

            <div class="renewal-card-foot">
              <p :class="['renewal-card-foot-note', { 'renewal-card-hint-error': card.raw.lastChargeStatus === 'failed' }]">
                {{ card.footNote }}
              </p>
              <div class="renewal-card-actions">
                <button type="button" class="renewal-card-action is-edit" @click="openEditModal(card.raw)">
                  编辑
                </button>
                <button type="button" class="renewal-card-action is-danger" @click="openDeleteModal(card.raw)">
                  删除
                </button>
                <button
                  type="button"
                  :class="['renewal-card-action', card.primaryActionClass]"
                  @click="toggleSubscriptionStatus(card.raw)"
                >
                  {{ card.primaryActionText }}
                </button>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="renewal-empty-state">
          <strong>还没有续费计划</strong>
          <p>新增一个每月自动续费项目后，会在到期日自动生成支出并扣减现金账户余额。</p>
          <CommonButton @click="openCreateModal">新增续费计划</CommonButton>
        </div>
      </section>
    </template>

    <CommonModal
      v-model="showSubscriptionModal"
      :title="modalTitle"
      @close="closeSubscriptionModal(true)"
    >
      <div class="renewal-form">
        <CommonInput v-model="formName" label="续费名称" placeholder="例如：腾讯视频会员" />
        <CommonInput
          v-model="formAmount"
          label="每期金额"
          placeholder="请输入续费金额"
          input-type="number"
          input-mode="decimal"
        />
        <CommonSelect v-model="formBillingCycle" label="扣费周期" :options="billingCycleOptions" />
        <CommonSelect v-model="formFundingAccountId" label="扣费账户" :options="fundingAccountOptions" />
        <CommonSelect v-model="formBillingDay" :label="billingDayLabel" :options="billingDayOptions" />
        <CommonInput
          v-model="formNextBillingDate"
          label="首次或下次扣费日"
          input-type="date"
        />
        <CommonSelect v-model="formStatus" label="计划状态" :options="statusOptions" />
        <CommonInput v-model="formRemark" label="备注" placeholder="可记录会员权益、家庭共享等信息" />
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
        确认删除“{{ deletingSubscription?.name ?? '当前续费计划' }}”吗？删除后无法恢复。
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
      aria-label="新增续费计划"
      storage-key="finance-renewals"
      @click="openCreateModal"
    />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
