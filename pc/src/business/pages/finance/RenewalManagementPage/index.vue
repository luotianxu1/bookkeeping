<script setup lang="ts">
import AmountText from '@/business/components/common/AmountText/index.vue'
import { amountTone } from '@/business/utils/amount-tone'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'
import { useReminderTarget } from '@/business/composables/useReminderTarget'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'

import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

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
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type FilterKey = 'all' | 'due' | 'paused'

const subscriptions = ref<RenewalSubscription[]>([])
const fundingAccounts = ref<Account[]>([])
const expenseCategories = ref<Category[]>([])

const summary = ref<RenewalSubscriptionSummary>({
  activeCount: 0,
  pausedCount: 0,
  dueThisMonthCount: 0,
  monthlyAmount: 0,
  dueThisMonthAmount: 0,
})
const isLoading = ref(true)
useReminderTarget(isLoading, 'renewal')
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
  const leafCategories = expenseCategories.value.filter(
    (category) =>
      !isHiddenFixedExpenseCategory(category) && isLeafCategory(category),
  )

  if (!leafCategories.length) {
    return [{ label: '暂无可用支出分类', value: '', disabled: true }]
  }

  return leafCategories.map((category) => ({
    label: buildCategoryOptionLabel(category),
    value: String(category.id),
  }))
})

const modalTitle = computed(() =>
  editingSubscription.value ? '修改固定支出' : '新增固定支出',
)
const submitLabel = computed(() =>
  editingSubscription.value ? '保存修改' : '新增固定支出',
)
const billingDayLabel = computed(() =>
  formBillingCycle.value === 'monthly' ? '支出日' : '每期支出日',
)

const activeSubscriptions = computed(() =>
  subscriptions.value.filter((item) => item.status === 'active'),
)

const filteredSubscriptions = computed(() => {
  if (selectedFilter.value === 'paused') {
    return subscriptions.value.filter((item) => item.status === 'paused')
  }
  if (selectedFilter.value === 'due') {
    return subscriptions.value.filter(
      (item) =>
        item.status === 'active' && isDueThisMonth(item.nextBillingDate),
    )
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

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看固定支出'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [subscriptionList, summaryData, accountList, categoryList] =
      await Promise.all([
        getRenewalSubscriptions({ userId: currentUser.id }),
        getRenewalSubscriptionSummary(currentUser.id),
        getAccounts({ userId: currentUser.id, status: 'active' }),
        getCategories({
          userId: currentUser.id,
          type: 'expense',
          status: 'active',
        }),
      ])
    subscriptions.value = subscriptionList
    summary.value = summaryData
    fundingAccounts.value = accountList.filter(
      (account) => account.accountTypeCode === 'cash',
    )
    expenseCategories.value = categoryList.filter(
      (category) => !isHiddenFixedExpenseCategory(category),
    )
    if (!formFundingAccountId.value && fundingAccounts.value.length > 0) {
      formFundingAccountId.value = String(fundingAccounts.value[0].id)
    }
  } catch (error) {
    pageError.value = getErrorMessage(error, '固定支出加载失败')
  } finally {
    isLoading.value = false
  }
}

function openCreateModal() {
  editingSubscription.value = null
  resetForm()
  formFundingAccountId.value = fundingAccounts.value[0]
    ? String(fundingAccounts.value[0].id)
    : ''
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
  formCategoryId.value = resolveEditableCategoryId(subscription.categoryId)
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
    const message = getErrorMessage(error, '固定支出保存失败')
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
    showFeedback(getErrorMessage(error, '固定支出状态更新失败'), 'error')
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
    await deleteRenewalSubscription(
      deletingSubscription.value.id,
      currentUser.id,
    )
    showDeleteModal.value = false
    deletingSubscription.value = null
    showFeedback('固定支出已删除', 'success')
    await loadPage()
  } catch (error) {
    const message = getErrorMessage(error, '固定支出删除失败')
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
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth()
  )
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(value)
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

function getAnnualCycleMultiplier(cycle?: RenewalBillingCycle | string | null) {
  if (cycle === 'quarterly') {
    return 4
  }
  if (cycle === 'yearly') {
    return 1
  }
  return 12
}

function buildCategoryOptionLabel(category: Category) {
  if (!category.parentId) {
    return category.name
  }
  const parent = expenseCategories.value.find(
    (item) => item.id === category.parentId,
  )
  return parent ? `${parent.name} / ${category.name}` : category.name
}

function isLeafCategory(category: Category) {
  return !expenseCategories.value.some((item) => item.parentId === category.id)
}

function resolveEditableCategoryId(categoryId?: number | null) {
  if (!categoryId) {
    return ''
  }
  const category = expenseCategories.value.find(
    (item) => item.id === categoryId,
  )
  return category &&
    !isHiddenFixedExpenseCategory(category) &&
    isLeafCategory(category)
    ? String(category.id)
    : ''
}

function isHiddenFixedExpenseCategory(category: Category) {
  return (
    category.system &&
    category.type === 'expense' &&
    category.name === '固定支出'
  )
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

type DesktopRow = (typeof filteredSubscriptions.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="固定支出">
      <template #right>
        <el-button type="primary" @click="openCreateModal">
          新增固定支出
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <div class="pc-statistics">
      <el-card shadow="never">
        <small>年度固定支出</small>
        <AmountText
          tag="strong"
          :value="formatCurrency(annualFixedExpenseAmount)"
          :tone="amountTone(-annualFixedExpenseAmount)"
        />
      </el-card>
      <el-card shadow="never">
        <small>月均支出</small>
        <AmountText
          tag="strong"
          :value="formatCurrency(monthlyAverageAmount)"
          :tone="amountTone(-monthlyAverageAmount)"
        />
      </el-card>
    </div>
    <DesktopTable
      title="固定支出明细"
      searchable
      search-placeholder="名称 / 备注"
      :rows="filteredSubscriptions"
      :loading="isLoading"
    >
      <template #toolbar>
        <el-select v-model="selectedFilter">
          <el-option
            v-for="item in filterOptions"
            :key="item.key"
            :value="item.key"
            :label="item.label"
          />
        </el-select>
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="名称"
        prop="name"
        sortable="custom"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="金额" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          <AmountText
            :value="formatCurrency(row.amount)"
            :tone="amountTone(-row.amount)"
          />
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="周期" min-width="110">
        <template #default="{ row }: { row: DesktopRow }">
          {{ getBillingCycleLabel(row.billingCycle) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="下次支出日"
        prop="nextBillingDate"
        sortable="custom"
        min-width="160"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }: { row: DesktopRow }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '暂停' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="remark"
        sortable="custom"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="isUpdatingStatus"
              @click="toggleSubscriptionStatus(row)"
            >
              {{ row.status === 'active' ? '暂停' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="openDeleteModal(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showSubscriptionModal"
      :title="modalTitle"
      @close="closeSubscriptionModal(true)"
    >
      <div class="pc-form-grid renewal-form">
        <CommonInput
          v-model="formName"
          label="支出名称"
          placeholder="例如：房租、会员、保险"
        />
        <CommonInput
          v-model="formAmount"
          label="每期金额"
          placeholder="请输入固定支出金额"
          input-type="number"
          input-mode="decimal"
        />
        <CommonSelect
          v-model="formBillingCycle"
          label="支出周期"
          :options="billingCycleOptions"
        />
        <CommonSelect
          v-model="formFundingAccountId"
          label="扣款账户"
          :options="fundingAccountOptions"
        />
        <CommonSelect
          v-model="formCategoryId"
          label="扣款分类（必填）"
          :options="categoryOptions"
        />
        <CommonSelect
          v-model="formBillingDay"
          :label="billingDayLabel"
          :options="billingDayOptions"
        />
        <CommonInput
          v-model="formNextBillingDate"
          label="首次或下次支出日"
          input-type="date"
        />
        <CommonSelect
          v-model="formStatus"
          label="支出状态"
          :options="statusOptions"
        />
        <CommonInput
          v-model="formRemark"
          label="备注"
          placeholder="可记录房租、会员、保费等固定支出信息"
        />
        <p v-if="formError" class="renewal-form-error">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar renewal-modal-actions">
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
        确认删除“{{
          deletingSubscription?.name ?? '当前固定支出'
        }}”吗？删除后无法恢复。
      </p>
      <p v-if="deleteError" class="renewal-form-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar renewal-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteModal"
          >
            取消
          </CommonButton>
          <CommonButton
            :disabled="isDeleting"
            @click="confirmDeleteSubscription"
          >
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
