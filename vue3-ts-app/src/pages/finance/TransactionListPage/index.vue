<script setup lang="ts">
// 收支列表页：还原 Pencil「收支列表页」画板中的筛选与日期分组流水。
import { computed, onMounted, ref } from 'vue'
import { deleteTransaction, getAccounts, getAccountTypes, getTransactions, type Transaction } from '@/api/modules/finance'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import type { Transaction as DayTransaction } from '@/types/finance'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const filterOptions = ['全部', '收入', '支出']
const activeFilter = ref(filterOptions[0])
const transactions = ref<Transaction[]>([])
const isLoadingTransactions = ref(false)
const transactionListError = ref('')
const deletingId = ref<number | null>(null)
const pendingDeleteTransaction = ref<DayTransaction | null>(null)
const showDeleteConfirmModal = ref(false)
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const filteredTransactions = computed(() => {
  if (activeFilter.value === '收入') {
    return transactions.value.filter((transaction) => transaction.type === 'income')
  }

  if (activeFilter.value === '支出') {
    return transactions.value.filter((transaction) => transaction.type === 'expense')
  }

  return transactions.value
})
const dayGroups = computed(() => buildTransactionDayGroups(filteredTransactions.value))

onMounted(() => {
  loadCashTransactions()
})

async function loadCashTransactions() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    transactionListError.value = '请先登录后查看收支记录'
    return
  }

  isLoadingTransactions.value = true
  transactionListError.value = ''

  try {
    const accountTypes = await getAccountTypes({ status: 'active' })
    const cashType = accountTypes.find((type) => type.code === 'cash')
    if (!cashType) {
      transactions.value = []
      transactionListError.value = '现金账户类型不存在'
      return
    }

    const [cashAccounts, transactionList] = await Promise.all([
      getAccounts({
        userId: currentUser.id,
        accountTypeId: cashType.id,
        status: 'active',
      }),
      getTransactions({
        userId: currentUser.id,
      }),
    ])
    const cashAccountIds = new Set(cashAccounts.map((account) => account.id))
    transactions.value = transactionList.filter((transaction) => cashAccountIds.has(transaction.accountId))
  } catch (error) {
    transactionListError.value = error instanceof Error ? error.message : '收支记录加载失败'
  } finally {
    isLoadingTransactions.value = false
  }
}

function openDeleteConfirm(transaction: DayTransaction) {
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
    await loadCashTransactions()
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
</script>

<template>
  <section class="transaction-list-page" aria-label="收支列表">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="收支列表" back-to="/finance" back-label="返回财务首页" />

    <SegmentedControl
      v-model="activeFilter"
      :options="filterOptions"
      label="收支类型筛选"
    />

    <section class="transaction-list-groups" aria-label="收支明细">
      <p v-if="transactionListError" class="transaction-list-message transaction-list-message-error">
        {{ transactionListError }}
      </p>
      <CommonLoading v-else-if="isLoadingTransactions" />
      <p v-else-if="dayGroups.length === 0" class="transaction-list-message">
        暂无收支记录
      </p>

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

    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :close-on-overlay="!deletingId"
      @close="closeDeleteConfirm"
    >
      <p class="transaction-delete-confirm-text">
        删除后会同步恢复账户余额，确认删除这条收支记录吗？
      </p>
      <p v-if="deleteError" class="transaction-delete-error">{{ deleteError }}</p>

      <template #footer>
        <div class="transaction-delete-actions">
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
