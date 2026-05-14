<script setup lang="ts">
// 收支列表页：还原 Pencil「收支列表页」画板中的筛选与日期分组流水。
import { computed, onMounted, ref } from 'vue'
import { getAccounts, getAccountTypes, getTransactions, type Transaction } from '@/api/modules/finance'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const filterOptions = ['全部', '收入', '支出']
const activeFilter = ref(filterOptions[0])
const transactions = ref<Transaction[]>([])
const isLoadingTransactions = ref(false)
const transactionListError = ref('')

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
</script>

<template>
  <section class="transaction-list-page" aria-label="收支列表">
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
      <p v-else-if="isLoadingTransactions" class="transaction-list-message">
        加载中...
      </p>
      <p v-else-if="dayGroups.length === 0" class="transaction-list-message">
        暂无收支记录
      </p>

      <template v-else>
        <TransactionDayCard
          v-for="group in dayGroups"
          :key="group.date"
          :group="group"
          summary-mode="stacked"
        />
      </template>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
