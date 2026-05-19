<script setup lang="ts">
// 现金资产详情页：展示单个现金账户的余额与收支记录。
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import { deleteTransaction, getAccount, getAccountTransactions, type Account, type Transaction as ApiTransaction } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import type { DayGroup, Transaction } from '@/types/finance'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const route = useRoute()

const account = ref<Account | null>(null)
const transactions = ref<ApiTransaction[]>([])
const isLoading = ref(false)
const pageError = ref('')
const deletingId = ref<number | null>(null)
const pendingDeleteTransaction = ref<Transaction | null>(null)
const showDeleteConfirmModal = ref(false)
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
let requestVersion = 0

const accountId = computed(() => Number(route.params.accountId))
const historyCountText = computed(() => `共 ${transactions.value.length} 条`)
const accountName = computed(() => account.value?.name ?? '现金账户')
const accountTypeName = computed(() => account.value?.accountTypeName ? `${account.value.accountTypeName}账户` : '现金账户')
const accountAmount = computed(() => formatAmount(Number(account.value?.currentBalance ?? 0)))
const dayGroups = computed<DayGroup[]>(() => buildTransactionDayGroups(transactions.value))

watch(accountId, () => {
  loadDetail()
}, { immediate: true })

async function loadDetail() {
  const currentRequestVersion = ++requestVersion
  account.value = null
  transactions.value = []

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看资产详情'
    return
  }
  if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
    pageError.value = '账户不存在'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountDetail, transactionList] = await Promise.all([
      getAccount(accountId.value),
      getAccountTransactions(accountId.value, {
        userId: currentUser.id,
      }),
    ])
    if (currentRequestVersion !== requestVersion) {
      return
    }
    account.value = accountDetail
    transactions.value = transactionList.filter((transaction) => transaction.accountId === accountId.value)
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '资产详情加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function openDeleteConfirm(transaction: Transaction) {
  pendingDeleteTransaction.value = transaction
  deleteError.value = ''
  showDeleteConfirmModal.value = true
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
    await loadDetail()
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

function formatAmount(value: number) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

</script>

<template>
  <section class="cash-asset-detail-page" aria-label="现金资产详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="cash-asset-detail-header">
      <PageHeader title="资产详情" back-to="/finance/accounts/cash" back-label="返回现金账户" />
    </header>

    <p v-if="pageError" class="cash-asset-message cash-asset-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="cash-asset-overview-card" aria-label="现金资产总览">
        <div class="cash-asset-overview-top">
          <div class="cash-asset-overview-title">
            <strong>{{ accountName }}</strong>
            <span>{{ accountTypeName }}</span>
          </div>
        </div>

        <AmountText tag="p" class="cash-asset-overview-amount" :value="accountAmount" />
      </section>

      <section class="cash-asset-history-wrap" aria-label="收支记录">
        <header class="cash-asset-history-head">
          <strong>收支记录</strong>
          <span>{{ historyCountText }}</span>
        </header>

        <section class="cash-asset-history-groups">
          <p v-if="transactions.length === 0" class="cash-asset-empty">暂无收支记录</p>

          <template v-else>
            <TransactionDayCard
              v-for="group in dayGroups"
              :key="group.date"
              :group="group"
              summary-mode="stacked"
              show-delete
              :deleting-id="deletingId"
              @delete="openDeleteConfirm"
            />
          </template>
        </section>
      </section>
    </template>

    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :close-on-overlay="!deletingId"
      @close="closeDeleteConfirm"
    >
      <p class="cash-delete-confirm-text">
        删除后会同步恢复账户余额，确认删除这条收支记录吗？
      </p>
      <p v-if="deleteError" class="cash-delete-error">{{ deleteError }}</p>

      <template #footer>
        <div class="cash-delete-actions">
          <CommonButton variant="secondary" :disabled="Boolean(deletingId)" @click="closeDeleteConfirm">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="Boolean(deletingId)" @click="confirmDeleteTransaction">
            {{ deletingId ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
