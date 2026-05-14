<script setup lang="ts">
// 记一笔-支出页：还原 Pencil「记一笔-支出」页面结构与交互。
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  createTransaction,
  getAccounts,
  getAccountTypes,
  getCategories,
  type Account,
  type Category,
  type TransactionType,
} from '@/api/modules/finance'
import { ApiError } from '@/api/request'
import { getStoredCurrentUser } from '@/utils/current-user'

// 记账类型：顶部支出/收入/转账切换。
const entryTypeOptions = ['支出', '收入', '转账']
const entryType = ref(entryTypeOptions[0])
const router = useRouter()
const currentUserId = computed(() => getStoredCurrentUser()?.id ?? 1)

// 分类数据：支出/收入切换后从后端加载。
const categories = ref<Category[]>([])
const activeCategoryId = ref<number | null>(null)
const categoryOptions = computed(() => [
  ...categories.value.map((category) => ({
    id: category.id,
    icon: displayIcon(category.icon),
    label: category.name,
    manage: false,
  })),
  { id: -1, icon: '⚙', label: '管理分类', manage: true },
])

// 表单数据：账户、时间、备注和金额。
const accountId = ref<number | null>(null)
const accountOptions = ref<Account[]>([])
const transferOutAccount = ref('现金账户')
const transferInAccount = ref('投资账户')
const transferOutOptions = ['现金账户', '招商银行卡', '支付宝', '微信']
const transferInOptions = ['投资账户', '现金账户', '招商银行卡', '支付宝']
const entryTime = ref(formatDateTimeLocal(new Date()))
const note = ref('')
const amountInput = ref('0.00')
const loading = ref(false)
const saving = ref(false)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

// 数字键盘：用于金额输入的按钮布局。
const keypadRows = [
  ['1', '2', '3', '删'],
  ['4', '5', '6', '清空'],
  ['7', '8', '9', '再记'],
  ['.', '0', '+', '保存'],
]

const amountDisplay = computed(() => `${amountInput.value}`)
const transactionType = computed<TransactionType>(() => (entryType.value === '收入' ? 'income' : 'expense'))
const selectedCategory = computed(() => categories.value.find((category) => category.id === activeCategoryId.value))
const selectedAccount = computed(() => accountOptions.value.find((account) => account.id === accountId.value))
const canSave = computed(() => (
  entryType.value !== '转账' &&
  !saving.value &&
  Number(amountInput.value) > 0 &&
  accountId.value !== null &&
  activeCategoryId.value !== null
))

onMounted(() => {
  loadEntryOptions()
})

watch(entryType, () => {
  closeFeedback()
  if (entryType.value !== '转账') {
    loadCategories()
  }
})

function selectCategory(item: { id: number; manage: boolean }) {
  if (item.manage) {
    router.push('/finance/categories')
    return
  }

  activeCategoryId.value = item.id
}

async function onKeypadPress(key: string) {
  if (key === '删') {
    amountInput.value = amountInput.value.length > 1 ? amountInput.value.slice(0, -1) : '0'
    return
  }

  if (key === '清空') {
    amountInput.value = '0.00'
    return
  }

  if (key === '保存' || key === '再记') {
    await saveTransaction()
    return
  }

  if (key === '+') return

  if (amountInput.value === '0.00') {
    amountInput.value = key === '.' ? '0.' : key
    return
  }

  if (key === '.' && amountInput.value.includes('.')) return

  amountInput.value += key
}

async function loadEntryOptions() {
  loading.value = true
  closeFeedback()

  try {
    await Promise.all([loadCashAccounts(), loadCategories()])
  } catch (error) {
    showFeedback(errorMessage(error), 'error')
  } finally {
    loading.value = false
  }
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
  accountId.value = accountOptions.value[0]?.id ?? null
}

async function loadCategories() {
  categories.value = await getCategories({
    userId: currentUserId.value,
    type: transactionType.value,
    status: 'active',
  })
  activeCategoryId.value = categories.value[0]?.id ?? null
}

async function saveTransaction() {
  if (entryType.value === '转账') {
    showFeedback('转账稍后实现，请先记录支出或收入', 'error')
    return
  }
  if (!canSave.value || accountId.value === null || activeCategoryId.value === null) {
    showFeedback('请填写金额、账户和分类', 'error')
    return
  }

  const amount = Number(amountInput.value)
  if (transactionType.value === 'expense' && selectedAccount.value && amount > Number(selectedAccount.value.currentBalance)) {
    showFeedback('账户余额不足', 'error')
    return
  }

  saving.value = true
  closeFeedback()

  try {
    await createTransaction({
      userId: currentUserId.value,
      type: transactionType.value,
      amount,
      currencyCode: 'CNY',
      accountId: accountId.value,
      categoryId: activeCategoryId.value,
      title: note.value.trim() || selectedCategory.value?.name,
      remark: note.value.trim() || null,
      occurredAt: entryTime.value,
    })
    amountInput.value = '0.00'
    note.value = ''
    showFeedback('新增成功', 'success')
    await loadCashAccounts()
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
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + `T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function displayIcon(icon: string) {
  const iconMap: Record<string, string> = {
    food: '🍽',
    daily: '🧴',
    transport: '🚗',
    entertainment: '🎬',
    shopping: '🛍',
    salary: '💼',
    'investment-income': '📈',
    other: '🧩',
  }
  return iconMap[icon] ?? icon
}
</script>

<template>
  <section class="expense-entry-page" aria-label="记一笔-支出">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="记一笔" back-to="/finance" back-label="返回财务首页" />

    <SegmentedControl v-model="entryType" :options="entryTypeOptions" label="记账类型切换" />

    <section class="expense-amount-card" aria-label="金额">
      <p>{{ amountDisplay }}</p>
    </section>

    <section v-if="entryType !== '转账'" class="expense-detail-card" aria-label="分类与详情">
      <p v-if="loading" class="expense-loading">加载中...</p>
      <div class="category-grid">
        <button
          v-for="item in categoryOptions"
          :key="item.id"
          type="button"
          :class="['category-item', { active: activeCategoryId === item.id }]"
          @click="selectCategory(item)"
        >
          <span>{{ item.icon }}</span>
          <strong>{{ item.label }}</strong>
        </button>
      </div>

      <div class="expense-info-row">
        <span>账户</span>
        <label class="expense-inline-control">
          <select v-model.number="accountId" aria-label="选择账户">
            <option v-for="account in accountOptions" :key="account.id" :value="account.id">
              {{ account.name }}
            </option>
          </select>
        </label>
      </div>
      <div class="expense-divider"></div>
      <div class="expense-info-row">
        <span>时间</span>
        <label class="expense-inline-control">
          <input v-model="entryTime" type="datetime-local" aria-label="选择时间" />
        </label>
      </div>
    </section>

    <section v-else class="expense-detail-card transfer-detail-card" aria-label="转账详情">
      <div class="expense-info-row transfer-row">
        <span>转出账户</span>
        <label class="expense-inline-control">
          <select v-model="transferOutAccount" aria-label="选择转出账户">
            <option v-for="account in transferOutOptions" :key="account" :value="account">
              {{ account }}
            </option>
          </select>
        </label>
      </div>
      <div class="expense-divider"></div>
      <div class="expense-info-row transfer-row">
        <span>转入账户</span>
        <label class="expense-inline-control">
          <select v-model="transferInAccount" aria-label="选择转入账户">
            <option v-for="account in transferInOptions" :key="account" :value="account">
              {{ account }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="expense-note-card" aria-label="备注">
      <label class="expense-note-input-wrap">
        <span>备注</span>
        <textarea
          v-model="note"
          class="expense-note-input"
          placeholder="输入备注内容"
          aria-label="输入备注"
        ></textarea>
      </label>
    </section>

    <section class="expense-keypad" aria-label="数字键盘">
      <div v-for="(row, rowIndex) in keypadRows" :key="`row-${rowIndex}`" class="expense-keypad-row">
        <button
          v-for="key in row"
          :key="key"
          type="button"
          :class="[
            'expense-keypad-key',
            { 'key-muted': key === '删' || key === '清空' || key === '再记' },
            { 'key-save': key === '保存' },
          ]"
          :disabled="saving && (key === '保存' || key === '再记')"
          @click="onKeypadPress(key)"
        >
          {{ saving && key === '保存' ? '保存中' : key }}
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
