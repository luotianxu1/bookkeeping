<script setup lang="ts">
// 财务首页：组合资产总览、统计入口和收支明细列表。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeOverview } from '@/data/finance'
import { getAccounts, getAccountTypes, getTransactions, type Transaction } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import AssetOverviewCard from '../components/AssetOverviewCard/index.vue'
import StatsEntry from '../components/StatsEntry/index.vue'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'

const router = useRouter()
const transactions = ref<Transaction[]>([])
const isLoadingTransactions = ref(false)
const transactionListError = ref('')
const dayGroups = computed(() => buildTransactionDayGroups(transactions.value))

onMounted(() => {
  loadCashTransactions()
})

function openExpenseEntryPage() {
  router.push('/finance/entry/expense')
}

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
  <AssetOverviewCard :overview="financeOverview" />
  <section class="feature-block" aria-label="更多功能">
    <header class="feature-header">
      <strong>更多功能</strong>
      <RouterLink class="feature-link" to="/finance/more-features" aria-label="进入更多功能">
        >
      </RouterLink>
    </header>
    <StatsEntry />
  </section>

  <section class="records" aria-label="收支明细">
    <p v-if="transactionListError" class="records-message records-message-error">
      {{ transactionListError }}
    </p>
    <p v-else-if="isLoadingTransactions" class="records-message">
      加载中...
    </p>
    <p v-else-if="dayGroups.length === 0" class="records-message">
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

  <FloatingAddButton aria-label="新增记账" @click="openExpenseEntryPage" />
</template>

<style scoped lang="scss" src="./style.scss"></style>
