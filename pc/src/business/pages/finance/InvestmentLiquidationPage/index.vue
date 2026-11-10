<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { formatAmount } from '@/business/utils/format'
// 投资清仓明细页：展示指定投资账户已清仓的股票和基金持仓。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AmountText from '@/business/components/common/AmountText/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getAccounts,
  getInvestmentPositions,
  getInvestmentTransactions,
  type Account,
  type InvestmentPosition,
  type InvestmentTransaction,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type LiquidationRow = {
  position: InvestmentPosition
  lastSellAt: string | null
  sellAmount: number
}

const route = useRoute()
const router = useRouter()
const isLoading = ref(true)
const pageError = ref('')
const account = ref<Account | null>(null)
const positions = ref<InvestmentPosition[]>([])
const transactions = ref<InvestmentTransaction[]>([])
let requestVersion = 0

const selectedAccountId = computed(() => parseAccountId(route.params.accountId))

const sellTransactionsByPositionId = computed(() => {
  const result = new Map<number, InvestmentTransaction[]>()
  for (const transaction of transactions.value) {
    if (!transaction.positionId || transaction.status !== 'normal') {
      continue
    }
    if (
      transaction.tradeType !== 'sell' &&
      transaction.tradeType !== 'reduce'
    ) {
      continue
    }
    const list = result.get(transaction.positionId) ?? []
    list.push(transaction)
    result.set(transaction.positionId, list)
  }
  return result
})
const rows = computed<LiquidationRow[]>(() =>
  positions.value
    .filter(
      (position) =>
        (position.productType === 'stock' || position.productType === 'fund') &&
        isClosedPosition(position),
    )
    .map((position) => {
      const sellTransactions =
        sellTransactionsByPositionId.value.get(position.id) ?? []
      const lastSellAt =
        sellTransactions
          .map((transaction) => transaction.tradeAt)
          .filter(Boolean)
          .sort(
            (left, right) =>
              new Date(right).getTime() - new Date(left).getTime(),
          )[0] ?? null
      const sellAmount = sellTransactions.reduce(
        (total, transaction) => total + Number(transaction.amount ?? 0),
        0,
      )
      return {
        position,
        lastSellAt,
        sellAmount,
      }
    })
    .sort((left, right) => {
      const rightTime = new Date(
        right.lastSellAt || right.position.updatedAt,
      ).getTime()
      const leftTime = new Date(
        left.lastSellAt || left.position.updatedAt,
      ).getTime()
      return rightTime - leftTime
    }),
)
const totalProfit = computed(() =>
  rows.value.reduce(
    (total, row) => total + Number(row.position.cumulativeProfit ?? 0),
    0,
  ),
)
const totalSellAmount = computed(() =>
  rows.value.reduce((total, row) => total + row.sellAmount, 0),
)
const totalCostBasis = computed(() => {
  const sellAmountCostBasis = totalSellAmount.value - totalProfit.value
  if (Number.isFinite(sellAmountCostBasis) && sellAmountCostBasis > 0) {
    return sellAmountCostBasis
  }
  return rows.value.reduce(
    (total, row) => total + Number(row.position.costAmount ?? 0),
    0,
  )
})
const totalProfitRate = computed(() => {
  if (!Number.isFinite(totalCostBasis.value) || totalCostBasis.value <= 0) {
    return 0
  }
  return (totalProfit.value / totalCostBasis.value) * 100
})
const fundCount = computed(
  () => rows.value.filter((row) => row.position.productType === 'fund').length,
)
const stockCount = computed(
  () => rows.value.filter((row) => row.position.productType === 'stock').length,
)

onMounted(() => {
  void loadLiquidations()
})

watch(
  () => route.params.accountId,
  () => {
    void loadLiquidations()
  },
)

function parseAccountId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

async function loadLiquidations() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()

  if (!currentUser) {
    pageError.value = '请先登录后查看清仓明细'
    isLoading.value = false
    return
  }
  if (!selectedAccountId.value) {
    pageError.value = '投资账户不存在'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, positionList, transactionList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getInvestmentPositions({
        userId: currentUser.id,
        accountId: selectedAccountId.value,
      }),
      getInvestmentTransactions({
        userId: currentUser.id,
        accountId: selectedAccountId.value,
      }),
    ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    const targetAccount =
      accountList.find(
        (item) =>
          item.id === selectedAccountId.value &&
          item.accountTypeCode === 'investment',
      ) ?? null
    if (!targetAccount) {
      account.value = null
      positions.value = []
      transactions.value = []
      pageError.value = '投资账户不存在'
      return
    }

    account.value = targetAccount
    positions.value = positionList
    transactions.value = transactionList
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = getErrorMessage(error, '清仓明细加载失败')
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function openPositionDetail(positionId: number) {
  router.push({
    path: '/finance/accounts/investment/detail',
    query: { positionId },
  })
}

function isClosedPosition(position: InvestmentPosition) {
  if (position.status === 'closed') {
    return true
  }
  const quantity = Number(position.holdingQuantity ?? 0)
  return (
    position.subscriptionStatus !== 'pending' &&
    Number.isFinite(quantity) &&
    quantity <= 0
  )
}

function getProductTypeLabel(position: InvestmentPosition) {
  if (position.productType === 'stock') {
    return 'A股'
  }
  if (position.productType === 'fund') {
    return '基金'
  }
  return '投资'
}

function getProductCode(position: InvestmentPosition) {
  return position.productSymbol || position.market || '--'
}

function formatCurrency(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  const sign = amount < 0 ? '-' : ''
  return `${sign}¥${formatAmount(Math.abs(amount))}`
}

function formatSignedCurrency(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}¥${formatAmount(Math.abs(amount))}`
}

function formatSignedRate(value: number | null | undefined) {
  return formatPercentage(value ?? 0, true)
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

type DesktopRow = (typeof rows.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="清仓明细"><template #right></template></PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        { label: '累计收益', value: formatSignedCurrency(totalProfit) },
        { label: '收益率', value: formatSignedRate(totalProfitRate) },
        { label: '基金数量', value: fundCount },
        { label: '股票数量', value: stockCount },
      ]"
    />
    <DesktopTable
      :rows="rows"
      :loading="isLoading"
      title="清仓记录"
      search-placeholder="搜索产品"
      searchable
    >
      <el-table-column
        prop="position.productName"
        label="产品"
        min-width="220"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="代码" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ getProductCode(row.position) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="类型" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ getProductTypeLabel(row.position) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="卖出金额" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.sellAmount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="累计收益" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          <AmountText
            :value="formatSignedCurrency(row.position.cumulativeProfit)"
          />
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="清仓时间" min-width="180">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatDateTime(row.lastSellAt || row.position.updatedAt) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column fixed="right" label="操作" width="150">
        <template #default="{ row }: { row: DesktopRow }">
          <el-button
            link
            type="primary"
            @click="openPositionDetail(row.position.id)"
          >
            查看详情
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
  </section>
</template>
