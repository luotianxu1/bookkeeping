<script setup lang="ts">
// 预算管理页：每个月只维护一个预算，下方展示历史预算。
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import {
  createMonthlyBudget,
  deleteMonthlyBudget,
  getCurrentMonthlyBudget,
  getMonthlyBudgets,
  updateMonthlyBudget,
  type MonthlyBudget,
  type SaveMonthlyBudgetParams,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const currentBudget = ref<MonthlyBudget | null>(null)
const budgets = ref<MonthlyBudget[]>([])
const isLoading = ref(false)
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
const formMonth = ref(getCurrentMonthInput())
const formAmount = ref('')
const formRemark = ref('')

const currentMonthDate = computed(() => `${formMonth.value}-01`)
const historyBudgets = computed(() =>
  budgets.value.filter((item) => item.budgetMonth !== currentMonthDate.value),
)
const budgetModalTitle = computed(() => (currentBudget.value ? '修改预算' : '新增预算'))

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
  formMonth.value = currentBudget.value?.budgetMonth.slice(0, 7) ?? getCurrentMonthInput()
  formAmount.value = currentBudget.value ? String(currentBudget.value.amount) : ''
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

async function loadBudgets() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看预算'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const monthDate = `${getCurrentMonthInput()}-01`
    const [current, list] = await Promise.all([
      getCurrentMonthlyBudget(currentUser.id, monthDate),
      getMonthlyBudgets({ userId: currentUser.id, limit: 24 }),
    ])
    currentBudget.value = current
    budgets.value = list
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '预算加载失败'
  } finally {
    isLoading.value = false
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
    const message = error instanceof Error ? error.message : '预算保存失败'
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
    const message = error instanceof Error ? error.message : '预算删除失败'
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
  <section class="budget-management-page" aria-label="预算管理">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="预算管理" back-to="/finance/more-features" back-label="返回更多功能" />

    <p v-if="pageError" class="budget-message budget-message-error">{{ pageError }}</p>
    <p v-else-if="isLoading" class="budget-message">加载中...</p>

    <template v-else>
      <section class="budget-summary-card" aria-label="本月预算">
        <div class="budget-summary-top">
          <span>{{ currentBudget ? formatMonth(currentBudget.budgetMonth) : formatMonth(currentMonthDate) }}预算</span>
          <strong>当前月份</strong>
        </div>

        <strong class="budget-amount">
          {{ currentBudget ? formatCurrency(currentBudget.amount) : '未设置' }}
        </strong>

        <p class="budget-meta">
          {{ currentBudget ? getBudgetMeta(currentBudget) : '设置本月预算后，可查看已使用和剩余金额' }}
        </p>

        <div class="budget-progress" aria-hidden="true">
          <span :style="{ width: `${getProgressPercent(currentBudget)}%` }"></span>
        </div>

        <div class="budget-actions">
          <button type="button" class="budget-action-primary" @click="openBudgetModal">
            {{ currentBudget ? '修改预算' : '新增预算' }}
          </button>
          <button
            type="button"
            class="budget-action-danger"
            :disabled="!currentBudget"
            @click="openDeleteConfirmModal"
          >
            删除预算
          </button>
        </div>
      </section>

      <p class="budget-rule-tip">每个月只能设置一个预算，可修改或删除当前月份预算。</p>

      <h2 class="budget-section-title">历史预算</h2>

      <section v-if="historyBudgets.length > 0" class="budget-history-list" aria-label="历史预算">
        <article v-for="item in historyBudgets" :key="item.id" class="budget-history-item">
          <div class="budget-history-top">
            <strong>{{ formatMonth(item.budgetMonth) }}</strong>
            <span>{{ formatCurrency(item.amount) }}</span>
          </div>
          <p :class="{ over: item.remainingAmount < 0 }">{{ getBudgetMeta(item) }}</p>
        </article>
      </section>

      <p v-else class="budget-message">暂无历史预算</p>
    </template>

    <CommonModal v-model="showBudgetModal" :title="budgetModalTitle">
      <form class="budget-form" @submit.prevent="saveBudget">
        <CommonInput
          v-model="formMonth"
          label="预算月份"
          input-type="month"
          placeholder="请选择预算月份"
        />
        <CommonInput
          v-model="formAmount"
          label="预算金额"
          input-type="number"
          input-mode="decimal"
          placeholder="请输入预算金额"
        />
        <CommonInput v-model="formRemark" label="备注" placeholder="可选，添加预算说明" />
        <p v-if="formError" class="budget-form-error">{{ formError }}</p>
      </form>

      <template #footer>
        <div class="budget-modal-actions">
          <CommonButton variant="secondary" :disabled="isSaving" @click="closeBudgetModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSaving" @click="saveBudget">
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
        确认删除{{ currentBudget ? `“${formatMonth(currentBudget.budgetMonth)}预算”` : '当前预算' }}吗？
      </p>
      <p v-if="deleteError" class="budget-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="budget-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteConfirmModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeleting" @click="confirmDeleteBudget">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
