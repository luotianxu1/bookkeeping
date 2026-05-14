<script setup lang="ts">
// 现金资产详情页：展示单个现金账户的余额与收支记录。
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import { getAccount, getAccountTransactions, type Account, type Transaction as ApiTransaction } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import type { DayGroup } from '@/types/finance'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const route = useRoute()

const account = ref<Account | null>(null)
const transactions = ref<ApiTransaction[]>([])
const isLoading = ref(false)
const pageError = ref('')
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

function formatAmount(value: number) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

</script>

<template>
  <section class="cash-asset-detail-page" aria-label="现金资产详情">
    <header class="cash-asset-detail-header">
      <PageHeader title="资产详情" back-to="/finance/accounts/cash" back-label="返回现金账户" />
    </header>

    <p v-if="pageError" class="cash-asset-message cash-asset-message-error">
      {{ pageError }}
    </p>
    <p v-else-if="isLoading" class="cash-asset-message">
      加载中...
    </p>

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
            />
          </template>
        </section>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
