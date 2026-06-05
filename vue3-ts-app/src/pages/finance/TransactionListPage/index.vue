<script setup lang="ts">
// 收支列表页：还原 Pencil「收支列表页」画板中的筛选与日期分组流水。
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { deleteTransaction, getAccounts, getAccountTypes, getTransactions, type Transaction } from '@/api/modules/finance'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { useFinanceFamilyView } from '@/composables/useFinanceFamilyView'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import type { Transaction as DayTransaction } from '@/types/finance'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const filterOptions = ['全部', '收入', '支出']
const activeFilter = ref(filterOptions[0])
const router = useRouter()
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
const transactionRequestSerial = ref(0)
const {
  familyView,
  familyViewOptions,
  selectedFamilyView,
  canSwitchFamilyView,
  isSelfView,
  isReadOnlyFamilyView,
  selectedViewerUserIds,
  viewerNameByUserId,
  loadFamilyMembers,
} = useFinanceFamilyView()

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
const familyViewHint = computed(() => {
  if (!isReadOnlyFamilyView.value) {
    return ''
  }

  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家收支记录。'
    : `当前查看 ${selectedFamilyView.value.label} 的收支记录。`
})

onMounted(() => {
  void initializePage()
})

watch(familyView, () => {
  if (showDeleteConfirmModal.value && !deletingId.value) {
    closeDeleteConfirm()
  }
  void loadCashTransactions()
})

async function initializePage() {
  await loadFamilyMembers()
  await loadCashTransactions()
}

function openEditTransaction(transaction: DayTransaction) {
  if (!isSelfView.value) {
    return
  }

  if (!transaction.id || transaction.sourceType !== 'transaction' || !transaction.accountId || !transaction.categoryId || !transaction.occurredAt) {
    showFeedback('当前记录暂不支持修改', 'error')
    return
  }

  router.push({
    path: '/finance/entry/expense',
    query: {
      transactionId: String(transaction.id),
      type: transaction.type,
      amount: String(transaction.rawAmount ?? 0),
      accountId: String(transaction.accountId),
      categoryId: String(transaction.categoryId),
      occurredAt: transaction.occurredAt,
      remark: transaction.remark ?? '',
      redirect: '/finance/transactions',
    },
  })
}

async function loadCashTransactions() {
  if (selectedViewerUserIds.value.length === 0) {
    transactionListError.value = '请先登录后查看收支记录'
    return
  }

  const requestSerial = transactionRequestSerial.value + 1
  transactionRequestSerial.value = requestSerial
  isLoadingTransactions.value = true
  transactionListError.value = ''

  try {
    const accountTypes = await getAccountTypes({ status: 'active' })
    const cashType = accountTypes.find((type) => type.code === 'cash')
    if (!cashType) {
      if (requestSerial !== transactionRequestSerial.value) {
        return
      }
      transactions.value = []
      transactionListError.value = '现金账户类型不存在'
      return
    }

    const userResults = await Promise.all(
      selectedViewerUserIds.value.map(async (userId) => {
        const [cashAccounts, transactionList] = await Promise.all([
          getAccounts({
            userId,
            accountTypeId: cashType.id,
            status: 'active',
          }),
          getTransactions({
            userId,
          }),
        ])

        const cashAccountIds = new Set(cashAccounts.map((account) => account.id))
        const viewerName = viewerNameByUserId.value.get(userId)

        return transactionList
          .filter((transaction) => cashAccountIds.has(transaction.accountId))
          .map((transaction) => ({
            ...transaction,
            accountName: isReadOnlyFamilyView.value && selectedFamilyView.value.kind === 'total' && viewerName
              ? `${viewerName} · ${transaction.accountName ?? '现金账户'}`
              : transaction.accountName,
          }))
      }),
    )

    if (requestSerial !== transactionRequestSerial.value) {
      return
    }

    transactions.value = userResults.flat()
  } catch (error) {
    if (requestSerial !== transactionRequestSerial.value) {
      return
    }
    transactions.value = []
    transactionListError.value = error instanceof Error ? error.message : '收支记录加载失败'
  } finally {
    if (requestSerial === transactionRequestSerial.value) {
      isLoadingTransactions.value = false
    }
  }
}

function openDeleteConfirm(transaction: DayTransaction) {
  if (!isSelfView.value) {
    return
  }

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

    <PageHeader title="收支列表" back-to="/finance" back-label="返回财务首页">
      <label v-if="canSwitchFamilyView" class="transaction-family-switch">
        <select v-model="familyView" class="transaction-family-switch-select" aria-label="切换家庭成员收支视角">
          <option
            v-for="option in familyViewOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </label>
    </PageHeader>

    <p v-if="familyViewHint" class="transaction-view-hint">
      {{ familyViewHint }}
    </p>

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
          :show-delete="isSelfView"
          :deleting-id="deletingId"
          @edit="openEditTransaction"
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
