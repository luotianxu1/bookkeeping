<script setup lang="ts">
// 按月管理总预算和分类预算。
import { computed, onMounted, ref, watch } from 'vue'
import CategoryBudgets from './CategoryBudgets.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import {
  createMonthlyBudget,
  deleteMonthlyBudget,
  getCurrentMonthlyBudget,
  getMonthlyBudgets,
  updateMonthlyBudget,
  type MonthlyBudget,
  type SaveMonthlyBudgetParams,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const currentBudget = ref<MonthlyBudget | null>(null)
const budgets = ref<MonthlyBudget[]>([])
const isLoading = ref(true)
const isSaving = ref(false)
const isDeleting = ref(false)
const pageError = ref('')
const formError = ref('')
const deleteError = ref('')
const showBudgetModal = ref(false)
const showDeleteConfirmModal = ref(false)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const selectedMonth = ref(getCurrentMonthInput())
const formMonth = ref(getCurrentMonthInput())
watch(selectedMonth, () => {
  void loadBudgets()
})
const formAmount = ref('')
const formRemark = ref('')

const currentMonthDate = computed(() => `${selectedMonth.value}-01`)
const historyBudgets = computed(() =>
  budgets.value.filter((item) => item.budgetMonth !== currentMonthDate.value),
)
const budgetModalTitle = computed(() =>
  currentBudget.value ? '修改预算' : '新增预算',
)

onMounted(() => {
  loadBudgets()
})

function getCurrentMonthInput() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

function formatMonth(value: string) {
  const [year, month] = value.slice(0, 7).split('-')
  return `${year}年${Number(month)}月`
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(value)
}

function getProgressPercent(budget: MonthlyBudget | null) {
  if (!budget) {
    return 0
  }

  return Math.min(Math.max(Number(budget.usagePercent || 0), 0), 100)
}

function getBudgetMeta(budget: MonthlyBudget) {
  const usedText = `已使用 ${formatCurrency(budget.usedAmount)}`
  if (budget.remainingAmount >= 0) {
    return `${usedText}，剩余 ${formatCurrency(budget.remainingAmount)}`
  }

  return `${usedText}，超出 ${formatCurrency(Math.abs(budget.remainingAmount))}`
}

function openBudgetModal() {
  formError.value = ''
  formMonth.value =
    currentBudget.value?.budgetMonth.slice(0, 7) ?? selectedMonth.value
  formAmount.value = currentBudget.value
    ? String(currentBudget.value.amount)
    : ''
  formRemark.value = currentBudget.value?.remark || ''
  showBudgetModal.value = true
}

function closeBudgetModal() {
  showBudgetModal.value = false
  formError.value = ''
}

function openDeleteConfirmModal() {
  if (!currentBudget.value) {
    return
  }

  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirmModal() {
  showDeleteConfirmModal.value = false
  deleteError.value = ''
}

let loadSerial = 0
async function loadBudgets() {
  const ticket = ++loadSerial
  if (!/^\d{4}-\d{2}$/.test(selectedMonth.value)) {
    pageError.value = '请选择有效月份'
    isLoading.value = false
    return
  }
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看预算'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const monthDate = `${selectedMonth.value}-01`
    const [current, list] = await Promise.all([
      getCurrentMonthlyBudget(currentUser.id, monthDate),
      getMonthlyBudgets({ userId: currentUser.id, limit: 24 }),
    ])
    if (ticket !== loadSerial) return
    currentBudget.value = current
    budgets.value = list
  } catch (error) {
    if (ticket === loadSerial)
      pageError.value = getErrorMessage(error, '预算加载失败')
  } finally {
    if (ticket === loadSerial) isLoading.value = false
  }
}

async function saveBudget() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后再保存预算'
    return
  }

  const amount = Number(formAmount.value)
  if (!Number.isFinite(amount) || amount <= 0) {
    formError.value = '请输入大于0的预算金额'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const payload: SaveMonthlyBudgetParams = {
      userId: currentUser.id,
      budgetMonth: `${formMonth.value}-01`,
      amount,
      currencyCode: 'CNY',
      remark: formRemark.value.trim() || null,
    }

    if (currentBudget.value?.id) {
      await updateMonthlyBudget(currentBudget.value.id, payload)
      showFeedback('修改成功', 'success')
    } else {
      await createMonthlyBudget(payload)
      showFeedback('新增成功', 'success')
    }

    closeBudgetModal()
    await loadBudgets()
  } catch (error) {
    const message = getErrorMessage(error, '预算保存失败')
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function confirmDeleteBudget() {
  const currentUser = getStoredCurrentUser()
  if (!currentBudget.value || !currentUser || isDeleting.value) {
    return
  }

  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteMonthlyBudget(currentBudget.value.id, currentUser.id)
    closeDeleteConfirmModal()
    showFeedback('删除成功', 'success')
    await loadBudgets()
  } catch (error) {
    const message = getErrorMessage(error, '预算删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="pc-page budget-management-page" aria-label="预算管理">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <PageHeader
      title="预算管理"
      back-to="/finance/more-features"
      back-label="返回更多功能"
    />
    <CommonInput v-model="selectedMonth" label="查看月份" input-type="month" />
    <p v-if="pageError" class="budget-message budget-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />
    <template v-else>
      <el-card
        class="pc-record-card budget-summary-card"
        aria-label="本月预算"
        shadow="never"
      >
        <div class="budget-summary-top">
          <span>
            {{
              currentBudget
                ? formatMonth(currentBudget.budgetMonth)
                : formatMonth(currentMonthDate)
            }}预算
          </span>
          <strong>月总预算</strong>
        </div>
        <strong class="budget-amount">
          {{ currentBudget ? formatCurrency(currentBudget.amount) : '未设置' }}
        </strong>
        <p class="budget-meta">
          {{
            currentBudget
              ? getBudgetMeta(currentBudget)
              : '设置本月预算后，可查看已使用和剩余金额'
          }}
        </p>
        <div class="budget-progress" aria-hidden="true">
          <span
            :style="{ width: `${getProgressPercent(currentBudget)}%` }"
          ></span>
        </div>
        <div class="pc-toolbar budget-actions">
          <el-button
            native-type="button"
            class="budget-action-primary"
            @click="openBudgetModal"
            type="primary"
          >
            {{ currentBudget ? '修改预算' : '新增预算' }}
          </el-button>
          <el-button
            native-type="button"
            class="budget-action-danger"
            :disabled="!currentBudget"
            @click="openDeleteConfirmModal"
            type="danger"
            plain
          >
            删除预算
          </el-button>
        </div>
      </el-card>
      <p class="budget-rule-tip">
        总预算包含现金账户支出；退款和报销到账在到账月份冲减支出。分类预算单独统计，不会重复叠加到总预算。
      </p>
      <CategoryBudgets :month="currentMonthDate" />
      <h2 class="budget-section-title">历史预算</h2>
      <section
        v-if="historyBudgets.length > 0"
        class="pc-grid budget-history-list"
        aria-label="历史预算"
      >
        <el-card
          v-for="item in historyBudgets"
          :key="item.id"
          class="pc-record-card budget-history-item"
          shadow="never"
        >
          <div class="budget-history-top">
            <strong>{{ formatMonth(item.budgetMonth) }}</strong>
            <span>{{ formatCurrency(item.amount) }}</span>
          </div>
          <p :class="{ over: item.remainingAmount < 0 }">
            {{ getBudgetMeta(item) }}
          </p>
        </el-card>
      </section>
      <p v-else class="budget-message">暂无历史预算</p>
    </template>
    <CommonModal v-model="showBudgetModal" :title="budgetModalTitle">
      <el-form
        class="budget-form"
        @submit.prevent="saveBudget"
        label-position="top"
      >
        <CommonInput
          v-model="formMonth"
          label="预算月份"
          input-type="month"
          placeholder="请选择预算月份"
          disabled
        />
        <CommonInput
          v-model="formAmount"
          label="预算金额"
          input-type="number"
          input-mode="decimal"
          placeholder="请输入预算金额"
        />
        <CommonInput
          v-model="formRemark"
          label="备注"
          placeholder="可选，添加预算说明"
        />
        <p v-if="formError" class="budget-form-error">{{ formError }}</p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar budget-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSaving"
            @click="closeBudgetModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSaving"
            @click="saveBudget"
          >
            {{ isSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :show-close="false"
    >
      <p class="budget-delete-message">
        确认删除{{
          currentBudget
            ? `“${formatMonth(currentBudget.budgetMonth)}预算”`
            : '当前预算'
        }}吗？
      </p>
      <p v-if="deleteError" class="budget-form-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar budget-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeleting"
            @click="confirmDeleteBudget"
          >
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
