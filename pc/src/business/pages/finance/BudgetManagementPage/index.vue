<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

// 按月管理总预算和分类预算。
import { computed, onMounted, ref, watch } from 'vue'
import CategoryBudgets from './CategoryBudgets.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

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

type DesktopRow = (typeof historyBudgets.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="预算管理">
      <template #right>
        <CommonInput
          v-model="selectedMonth"
          label="查看月份"
          aria-label="查看月份"
          input-type="month"
        />
        <el-button
          type="danger"
          plain
          :disabled="!currentBudget"
          @click="openDeleteConfirmModal"
        >
          删除预算
        </el-button>
        <el-button type="primary" @click="openBudgetModal">
          {{ currentBudget ? '修改预算' : '新增预算' }}
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        {
          label: '月总预算',
          value: currentBudget
            ? formatCurrency(currentBudget.amount)
            : '未设置',
        },
        {
          label: '已使用',
          value: currentBudget ? formatCurrency(currentBudget.usedAmount) : '—',
        },
        {
          label: '剩余预算',
          value: currentBudget
            ? formatCurrency(currentBudget.remainingAmount)
            : '—',
        },
      ]"
    />
    <PagePanel title="预算执行">
      <el-progress
        :format="formatPercentage"
        :percentage="getProgressPercent(currentBudget)"
      />
      <p class="desktop-note">
        退款和报销按到账月份冲减支出；分类预算单独统计。
      </p>
    </PagePanel>
    <CategoryBudgets :month="currentMonthDate" />
    <DesktopTable :rows="historyBudgets" :loading="isLoading" title="历史预算">
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="月份" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatMonth(row.budgetMonth) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="预算" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.amount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="已使用" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.usedAmount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="剩余" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.remainingAmount) }}
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
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
