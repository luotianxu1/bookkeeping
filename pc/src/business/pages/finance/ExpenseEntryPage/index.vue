<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'

import { useIdempotentSubmit } from '@/business/composables/useIdempotentSubmit'
// 记一笔-支出页：还原 Pencil「记一笔-支出」页面结构与交互。
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonDateTimePicker from '@/business/components/common/CommonDateTimePicker/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import SavingsAllocationFields, {
  type SavingsAllocationDraft,
} from './components/SavingsAllocationFields.vue'
import ExpenseInlineSelect, {
  type ExpenseInlineSelectOption,
  type ExpenseInlineSelectValue,
} from './components/ExpenseInlineSelect/index.vue'
import {
  createTransaction,
  getAccounts,
  getAccountTypes,
  getCategories,
  updateTransaction,
  getSavingsGoals,
  getTransactionSavingsAllocations,
  type SavingsGoal,
  type Account,
  type Category,
  type TransactionType,
} from '@/business/api/modules/finance'
import { ApiError } from '@/business/api/request'
import { getStoredCurrentUser } from '@/business/utils/current-user'

// 记账类型：顶部支出/收入/转账切换。
const submission = useIdempotentSubmit()

const entryTypeOptions = ['支出', '收入', '转账']
const entryType = ref(entryTypeOptions[0])
const route = useRoute()
const router = useRouter()
const currentUserId = computed(() => getStoredCurrentUser()?.id ?? 1)
const editingTransactionId = ref<number | null>(
  parseRouteNumber(route.query.transactionId),
)

// 分类数据：支出/收入切换后从后端加载。
const categories = ref<Category[]>([])
const activeParentCategoryId = ref<number | null>(null)
const activeCategoryId = ref<number | null>(null)
const rootCategories = computed(() =>
  categories.value.filter((category) => !category.parentId),
)

const secondaryCategoryOptions = computed(() =>
  categories.value
    .filter((category) => category.parentId === activeParentCategoryId.value)
    .map((category) => ({
      id: category.id,
      icon: category.icon,
      color: category.color,
      fallbackIcon:
        categories.value.find((parent) => parent.id === category.parentId)
          ?.icon ?? null,
      fallbackColor:
        categories.value.find((parent) => parent.id === category.parentId)
          ?.color ?? null,
      label: category.name,
    })),
)
// 表单数据：账户、时间、备注和金额。
const accountId = ref<number | null>(null)
const accountOptions = ref<Account[]>([])
const transferOutAccount = ref<number | null>(null)
const transferInAccount = ref<number | null>(null)
const accountSelectOptions = computed<ExpenseInlineSelectOption[]>(() =>
  accountOptions.value.map((account) => ({
    label: account.name,
    value: account.id,
    description: formatAccountBalance(account.currentBalance),
  })),
)
const entryTime = ref(formatDateTimeLocal(new Date()))
const note = ref('')
const amountInput = ref('0.00')
const loading = ref(true)
const formReady = ref(false)
const saving = ref(false)
const savingsGoals = ref<SavingsGoal[]>([])
const outgoingAllocations = ref<SavingsAllocationDraft[]>([])
const incomingAllocations = ref<SavingsAllocationDraft[]>([])
const eligibleOutgoingGoals = computed(() =>
  savingsGoals.value.filter(
    (goal) =>
      goal.accountId ===
      (entryType.value === '转账' ? transferOutAccount.value : accountId.value),
  ),
)
const eligibleIncomingGoals = computed(() =>
  savingsGoals.value.filter(
    (goal) =>
      goal.accountId ===
      (entryType.value === '转账' ? transferInAccount.value : accountId.value),
  ),
)
const currentReserved = computed(() =>
  savingsGoals.value
    .filter((goal) => goal.accountId === accountId.value)
    .reduce((sum, goal) => sum + Number(goal.currentAmount), 0),
)
const currentAvailable = computed(() =>
  Math.max(
    0,
    Number(selectedAccount.value?.currentBalance ?? 0) - currentReserved.value,
  ),
)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

// 数字键盘：用于金额输入的按钮布局。

const transactionType = computed<TransactionType>(() => {
  if (entryType.value === '收入') return 'income'
  if (entryType.value === '转账') return 'transfer'
  return 'expense'
})
const selectedCategory = computed(() =>
  categories.value.find((category) => category.id === activeCategoryId.value),
)
const selectedAccount = computed(() =>
  accountOptions.value.find((account) => account.id === accountId.value),
)
const transferOutSelectedAccount = computed(() =>
  accountOptions.value.find(
    (account) => account.id === transferOutAccount.value,
  ),
)
const isEditing = computed(() => editingTransactionId.value !== null)
const backTo = computed(
  () => parseRouteText(route.query.redirect) || '/finance',
)
const accountSelectValue = computed<ExpenseInlineSelectValue>({
  get: () => accountId.value,
  set: (value) => {
    if (typeof value === 'number') {
      accountId.value = value
      return
    }
    if (typeof value === 'string' && value) {
      const parsed = Number(value)
      accountId.value = Number.isFinite(parsed) ? parsed : null
      return
    }
    accountId.value = null
  },
})
const transferOutSelectValue = computed<ExpenseInlineSelectValue>({
  get: () => transferOutAccount.value,
  set: (value) => {
    transferOutAccount.value =
      typeof value === 'number'
        ? value
        : typeof value === 'string' && value
          ? Number(value)
          : null
  },
})
const transferInSelectValue = computed<ExpenseInlineSelectValue>({
  get: () => transferInAccount.value,
  set: (value) => {
    transferInAccount.value =
      typeof value === 'number'
        ? value
        : typeof value === 'string' && value
          ? Number(value)
          : null
  },
})
const canSave = computed(() => {
  if (
    loading.value ||
    !formReady.value ||
    saving.value ||
    Number(amountInput.value) <= 0
  ) {
    return false
  }
  if (entryType.value === '转账') {
    return (
      transferOutAccount.value !== null &&
      transferInAccount.value !== null &&
      transferOutAccount.value !== transferInAccount.value
    )
  }
  return accountId.value !== null && activeCategoryId.value !== null
})

onMounted(() => {
  applyRouteEntryType()
  loadEntryOptions()
})

watch(entryType, () => {
  outgoingAllocations.value = []
  incomingAllocations.value = []
  closeFeedback()
  if (entryType.value !== '转账') {
    loadCategories()
  }
})
watch(accountId, () => {
  outgoingAllocations.value = []
  incomingAllocations.value = []
})
watch(transferOutAccount, () => {
  outgoingAllocations.value = []
})
watch(transferInAccount, () => {
  incomingAllocations.value = []
})

function selectCategory(item: { id: number; manage: boolean }) {
  if (item.manage) {
    router.push('/finance/categories')
    return
  }

  activeParentCategoryId.value = item.id
  syncActiveLeafCategory(item.id, activeCategoryId.value)
}

async function loadEntryOptions() {
  loading.value = true
  formReady.value = false
  closeFeedback()

  try {
    await Promise.all([
      loadCashAccounts(),
      loadCategories(),
      loadSavingsGoals(),
    ])
    applyEditingFormValues()
    await nextTick()
    if (editingTransactionId.value) {
      const allocations = await getTransactionSavingsAllocations(
        editingTransactionId.value,
      )
      const rows = allocations.map((item) => ({
        goalId: item.goalId,
        amount: String(item.amount),
      }))
      incomingAllocations.value = rows.filter(
        (row) =>
          entryType.value !== '支出' &&
          eligibleIncomingGoals.value.some((goal) => goal.id === row.goalId),
      )
      outgoingAllocations.value = rows.filter(
        (row) =>
          entryType.value !== '收入' &&
          eligibleOutgoingGoals.value.some((goal) => goal.id === row.goalId),
      )
    }
    formReady.value = true
  } catch (error) {
    showFeedback(errorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function loadSavingsGoals() {
  savingsGoals.value = await getSavingsGoals()
}

function allocationsForSave(amount: number) {
  const outgoing = entryType.value === '收入' ? [] : outgoingAllocations.value
  const incoming = entryType.value === '支出' ? [] : incomingAllocations.value
  for (const rows of [outgoing, incoming]) {
    let total = 0
    for (const row of rows) {
      if (
        !row.goalId ||
        !/^\d{1,16}(\.\d{1,2})?$/.test(row.amount.trim()) ||
        Number(row.amount) <= 0
      )
        throw new Error('请填写有效的目标分配金额，最多两位小数')
      total += Math.round(Number(row.amount) * 100)
    }
    if (total > Math.round(amount * 100))
      throw new Error('目标计入或扣减总额不能超过本笔流水金额')
  }
  return [...outgoing, ...incoming].map((row) => ({
    goalId: row.goalId!,
    amount: Number(row.amount),
  }))
}

async function loadCashAccounts() {
  const accountTypes = await getAccountTypes({ status: 'active' })
  const cashType = accountTypes.find((type) => type.code === 'cash')
  if (!cashType) {
    accountOptions.value = []
    accountId.value = null
    throw new Error('现金账户类型不存在')
  }

  accountOptions.value = await getAccounts({
    userId: currentUserId.value,
    accountTypeId: cashType.id,
    status: 'active',
  })
  const nextAccountId = accountOptions.value[0]?.id ?? null
  const nextTransferOutAccountId = accountOptions.value[0]?.id ?? null
  const nextTransferInAccountId =
    accountOptions.value[1]?.id ?? accountOptions.value[0]?.id ?? null

  accountId.value = nextAccountId
  transferOutAccount.value = nextTransferOutAccountId
  transferInAccount.value = nextTransferInAccountId
}

async function loadCategories() {
  const previousParentId = activeParentCategoryId.value
  const previousCategoryId = activeCategoryId.value
  // 转账不需要分类，分类类型只可能是支出或收入
  const categoryType: 'expense' | 'income' =
    transactionType.value === 'income' ? 'income' : 'expense'
  categories.value = await getCategories({
    userId: currentUserId.value,
    type: categoryType,
    status: 'active',
  })
  syncSelectedCategories(previousParentId, previousCategoryId)
}

async function saveTransaction() {
  if (saving.value) return
  if (!formReady.value || loading.value) {
    showFeedback('请等待账户和储蓄目标加载完成后再保存', 'error')
    return
  }
  const amount = Number(amountInput.value)
  if (amount <= 0) {
    showFeedback('请输入大于0的金额', 'error')
    return
  }

  if (entryType.value === '转账') {
    await saveTransferTransaction(amount)
    return
  }

  if (
    !canSave.value ||
    accountId.value === null ||
    activeCategoryId.value === null
  ) {
    showFeedback('请填写金额、账户和分类', 'error')
    return
  }

  if (
    !isEditing.value &&
    transactionType.value === 'expense' &&
    selectedAccount.value &&
    amount > Number(selectedAccount.value.currentBalance)
  ) {
    showFeedback('账户余额不足', 'error')
    return
  }

  saving.value = true
  closeFeedback()

  try {
    const payload = {
      userId: currentUserId.value,
      type: transactionType.value,
      amount,
      currencyCode: selectedAccount.value?.currencyCode ?? 'CNY',
      accountId: accountId.value,
      categoryId: activeCategoryId.value,
      title: note.value.trim() || selectedCategory.value?.name,
      remark: note.value.trim() || null,
      occurredAt: entryTime.value,
      savingsAllocations: allocationsForSave(amount),
    }

    if (isEditing.value && editingTransactionId.value) {
      await updateTransaction(editingTransactionId.value, payload)
      showFeedback('修改成功', 'success')
    } else {
      await createTransaction(submission.withRequestId(payload))
      submission.reset()
      amountInput.value = '0.00'
      note.value = ''
      outgoingAllocations.value = []
      incomingAllocations.value = []
      showFeedback('新增成功', 'success')
      await Promise.all([loadCashAccounts(), loadSavingsGoals()])
    }
  } catch (error) {
    showFeedback(errorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

async function saveTransferTransaction(amount: number) {
  if (transferOutAccount.value === null || transferInAccount.value === null) {
    showFeedback('请选择转出账户和转入账户', 'error')
    return
  }
  if (transferOutAccount.value === transferInAccount.value) {
    showFeedback('转出账户和转入账户不能相同', 'error')
    return
  }
  if (
    !isEditing.value &&
    transferOutSelectedAccount.value &&
    amount > Number(transferOutSelectedAccount.value.currentBalance)
  ) {
    showFeedback('转出账户余额不足', 'error')
    return
  }

  saving.value = true
  closeFeedback()

  try {
    const payload = {
      userId: currentUserId.value,
      type: 'transfer' as TransactionType,
      amount,
      currencyCode: transferOutSelectedAccount.value?.currencyCode ?? 'CNY',
      fromAccountId: transferOutAccount.value,
      toAccountId: transferInAccount.value,
      remark: note.value.trim() || null,
      occurredAt: entryTime.value,
      savingsAllocations: allocationsForSave(amount),
    }

    if (isEditing.value && editingTransactionId.value) {
      await updateTransaction(editingTransactionId.value, payload)
      showFeedback('修改成功', 'success')
    } else {
      await createTransaction(submission.withRequestId(payload))
      submission.reset()
      amountInput.value = '0.00'
      note.value = ''
      outgoingAllocations.value = []
      incomingAllocations.value = []
      showFeedback('转账成功', 'success')
      await Promise.all([loadCashAccounts(), loadSavingsGoals()])
    }
  } catch (error) {
    showFeedback(errorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function closeFeedback() {
  showFeedbackModal.value = false
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return '操作失败，请稍后再试'
}

function formatDateTimeLocal(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return (
    [date.getFullYear(), pad(date.getMonth() + 1), pad(date.getDate())].join(
      '-',
    ) + `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

function formatAccountBalance(value: number) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) {
    return '0.00'
  }
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

function applyRouteEntryType() {
  const type = parseRouteText(route.query.type)
  if (type === 'income') {
    entryType.value = '收入'
    return
  }
  if (type === 'expense') {
    entryType.value = '支出'
  }
  if (type === 'transfer') entryType.value = '转账'
}

function applyEditingFormValues() {
  if (!isEditing.value) {
    return
  }

  const amount = parseRouteNumber(route.query.amount)
  if (amount !== null) {
    amountInput.value = amount.toFixed(2)
  }

  const routeAccountId = parseRouteNumber(route.query.accountId)
  if (
    routeAccountId !== null &&
    accountOptions.value.some((account) => account.id === routeAccountId)
  ) {
    accountId.value = routeAccountId
  }
  const fromId = parseRouteNumber(route.query.fromAccountId),
    toId = parseRouteNumber(route.query.toAccountId)
  if (fromId !== null) transferOutAccount.value = fromId
  if (toId !== null) transferInAccount.value = toId

  const routeCategoryId = parseRouteNumber(route.query.categoryId)
  if (routeCategoryId !== null) {
    const matchedCategory = categories.value.find(
      (category) => category.id === routeCategoryId,
    )
    if (matchedCategory) {
      activeParentCategoryId.value =
        matchedCategory.parentId ?? matchedCategory.id
      activeCategoryId.value = matchedCategory.id
    }
  }

  const occurredAt = parseRouteText(route.query.occurredAt)
  if (occurredAt) {
    entryTime.value = occurredAt.slice(0, 16)
  }

  note.value = parseRouteText(route.query.remark) ?? ''
}

function parseRouteText(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }
  return typeof value === 'string' ? value : ''
}

function parseRouteNumber(value: unknown) {
  const raw = parseRouteText(value)
  if (!raw) {
    return null
  }
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

function syncSelectedCategories(
  preferredParentId: number | null,
  preferredCategoryId: number | null,
) {
  if (rootCategories.value.length === 0) {
    activeParentCategoryId.value = null
    activeCategoryId.value = null
    return
  }

  const nextParent =
    rootCategories.value.find(
      (category) => category.id === preferredParentId,
    ) ?? rootCategories.value[0]
  activeParentCategoryId.value = nextParent.id
  syncActiveLeafCategory(nextParent.id, preferredCategoryId)
}

function syncActiveLeafCategory(
  parentId: number,
  preferredCategoryId: number | null,
) {
  const childCategories = categories.value.filter(
    (category) => category.parentId === parentId,
  )
  if (childCategories.length === 0) {
    activeCategoryId.value = parentId
    return
  }

  activeCategoryId.value =
    childCategories.find((category) => category.id === preferredCategoryId)
      ?.id ?? childCategories[0].id
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="isEditing ? '修改记录' : '记一笔'">
      <template #right></template>
    </PageHeader>
    <div class="desktop-main-aside">
      <PagePanel title="收支信息">
        <el-form label-position="top" @submit.prevent="saveTransaction">
          <div class="pc-toolbar">
            <SegmentedControl
              v-model="entryType"
              :options="entryTypeOptions"
              label="记账类型"
            />
          </div>
          <el-skeleton v-if="loading" :rows="5" animated />
          <div v-else class="desktop-form-grid">
            <el-form-item label="金额" required>
              <el-input
                v-model="amountInput"
                inputmode="decimal"
                placeholder="0.00"
                aria-label="金额"
              />
            </el-form-item>
            <CommonDateTimePicker v-model="entryTime" label="时间" />
            <template v-if="entryType !== '转账'">
              <el-form-item label="一级分类">
                <el-select
                  :model-value="activeParentCategoryId"
                  @update:model-value="
                    selectCategory({ id: $event, manage: false })
                  "
                  filterable
                >
                  <el-option
                    v-for="item in rootCategories"
                    :key="item.id"
                    :value="item.id"
                    :label="item.name"
                  />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="secondaryCategoryOptions.length"
                label="二级分类"
              >
                <el-select v-model="activeCategoryId" filterable>
                  <el-option
                    v-for="item in secondaryCategoryOptions"
                    :key="item.id"
                    :value="item.id"
                    :label="item.label"
                  />
                </el-select>
              </el-form-item>
              <ExpenseInlineSelect
                v-model="accountSelectValue"
                label="账户"
                :options="accountSelectOptions"
              />
              <p v-if="currentReserved > 0">
                可用余额 {{ formatAccountBalance(currentAvailable) }} · 目标预留
                {{ formatAccountBalance(currentReserved) }}
              </p>
            </template>
            <template v-else>
              <ExpenseInlineSelect
                v-model="transferOutSelectValue"
                label="转出账户"
                :options="accountSelectOptions"
              />
              <ExpenseInlineSelect
                v-model="transferInSelectValue"
                label="转入账户"
                :options="accountSelectOptions"
              />
            </template>
            <el-form-item label="备注">
              <el-input v-model="note" type="textarea" :rows="2" />
            </el-form-item>
            <SavingsAllocationFields
              v-if="entryType !== '收入'"
              v-model="outgoingAllocations"
              :goals="eligibleOutgoingGoals"
              label="扣减储蓄目标"
              :disabled="saving"
            />
            <SavingsAllocationFields
              v-if="entryType !== '支出'"
              v-model="incomingAllocations"
              :goals="eligibleIncomingGoals"
              label="计入储蓄目标"
              :disabled="saving"
            />
          </div>
          <div class="pc-form-footer">
            <el-button
              type="primary"
              native-type="submit"
              :loading="saving"
              :disabled="!canSave"
            >
              保存
            </el-button>
            <el-button :disabled="saving" @click="router.push(backTo)">
              返回
            </el-button>
            <RouterLink to="/finance/categories">管理分类</RouterLink>
          </div>
        </el-form>
      </PagePanel>
      <PagePanel title="账户与分配">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="记账类型">
            {{ entryType }}
          </el-descriptions-item>
          <el-descriptions-item label="可用余额">
            {{ formatAccountBalance(currentAvailable) }}
          </el-descriptions-item>
          <el-descriptions-item label="目标预留">
            {{ formatAccountBalance(currentReserved) }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="desktop-link-list">
          <RouterLink to="/finance/accounts">管理账户</RouterLink>
          <RouterLink to="/finance/categories">管理分类</RouterLink>
          <RouterLink to="/finance/savings-goals">储蓄目标</RouterLink>
        </div>
      </PagePanel>
    </div>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
