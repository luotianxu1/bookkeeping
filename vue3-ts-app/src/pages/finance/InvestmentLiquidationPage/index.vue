<script setup lang="ts">
// 投资清仓明细页：展示指定投资账户已清仓的股票和基金持仓。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getAccounts,
  getInvestmentPositions,
  getInvestmentTransactions,
  type Account,
  type InvestmentPosition,
  type InvestmentTransaction,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type LiquidationRow = {
  position: InvestmentPosition
  lastSellAt: string | null
  sellAmount: number
}

const route = useRoute()
const router = useRouter()
const isLoading = ref(false)
const pageError = ref('')
const account = ref<Account | null>(null)
const positions = ref<InvestmentPosition[]>([])
const transactions = ref<InvestmentTransaction[]>([])
let requestVersion = 0

const selectedAccountId = computed(() => parseAccountId(route.params.accountId))
const backTo = computed(() => selectedAccountId.value
  ? `/finance/accounts/investment/${selectedAccountId.value}`
  : '/finance/accounts/investment')
const sellTransactionsByPositionId = computed(() => {
  const result = new Map<number, InvestmentTransaction[]>()
  for (const transaction of transactions.value) {
    if (!transaction.positionId || transaction.status !== 'normal') {
      continue
    }
    if (transaction.tradeType !== 'sell' && transaction.tradeType !== 'reduce') {
      continue
    }
    const list = result.get(transaction.positionId) ?? []
    list.push(transaction)
    result.set(transaction.positionId, list)
  }
  return result
})
const rows = computed<LiquidationRow[]>(() => positions.value
  .filter((position) => (position.productType === 'stock' || position.productType === 'fund') && isClosedPosition(position))
  .map((position) => {
    const sellTransactions = sellTransactionsByPositionId.value.get(position.id) ?? []
    const lastSellAt = sellTransactions
      .map((transaction) => transaction.tradeAt)
      .filter(Boolean)
      .sort((left, right) => new Date(right).getTime() - new Date(left).getTime())[0] ?? null
    const sellAmount = sellTransactions.reduce((total, transaction) => total + Number(transaction.amount ?? 0), 0)
    return {
      position,
      lastSellAt,
      sellAmount,
    }
  })
  .sort((left, right) => {
    const rightTime = new Date(right.lastSellAt || right.position.updatedAt).getTime()
    const leftTime = new Date(left.lastSellAt || left.position.updatedAt).getTime()
    return rightTime - leftTime
  }))
const totalProfit = computed(() => rows.value.reduce((total, row) => total + Number(row.position.cumulativeProfit ?? 0), 0))
const totalSellAmount = computed(() => rows.value.reduce((total, row) => total + row.sellAmount, 0))
const totalCostBasis = computed(() => {
  const sellAmountCostBasis = totalSellAmount.value - totalProfit.value
  if (Number.isFinite(sellAmountCostBasis) && sellAmountCostBasis > 0) {
    return sellAmountCostBasis
  }
  return rows.value.reduce((total, row) => total + Number(row.position.costAmount ?? 0), 0)
})
const totalProfitRate = computed(() => {
  if (!Number.isFinite(totalCostBasis.value) || totalCostBasis.value <= 0) {
    return 0
  }
  return (totalProfit.value / totalCostBasis.value) * 100
})
const fundCount = computed(() => rows.value.filter((row) => row.position.productType === 'fund').length)
const stockCount = computed(() => rows.value.filter((row) => row.position.productType === 'stock').length)

onMounted(() => {
  void loadLiquidations()
})

watch(() => route.params.accountId, () => {
  void loadLiquidations()
})

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
    return
  }
  if (!selectedAccountId.value) {
    pageError.value = '投资账户不存在'
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

    const targetAccount = accountList.find((item) => item.id === selectedAccountId.value && item.accountTypeCode === 'investment') ?? null
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
    pageError.value = error instanceof Error ? error.message : '清仓明细加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function openPositionDetail(positionId: number) {
  router.push({ path: '/finance/accounts/investment/detail', query: { positionId } })
}

function isClosedPosition(position: InvestmentPosition) {
  if (position.status === 'closed') {
    return true
  }
  const quantity = Number(position.holdingQuantity ?? 0)
  return position.subscriptionStatus !== 'pending' && Number.isFinite(quantity) && quantity <= 0
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

function formatAmount(value: number | null | undefined, digits = 2) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
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
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}${formatAmount(Math.abs(amount))}%`
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
</script>

<template>
  <section class="investment-liquidation-page" aria-label="投资清仓明细">
    <PageHeader title="清仓明细" :back-to="backTo" back-label="返回投资详情" />

    <p v-if="pageError" class="investment-liquidation-message investment-liquidation-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="investment-liquidation-summary" aria-label="清仓汇总">
        <div class="liquidation-summary-main">
          <span>{{ account?.name || '投资账户' }}</span>
          <AmountText
            tag="strong"
            class="liquidation-summary-profit"
            :value="formatSignedCurrency(totalProfit)"
          />
          <em>{{ fundCount }} 个基金 · {{ stockCount }} 个A股</em>
        </div>
        <div class="liquidation-summary-side">
          <span>收益率</span>
          <AmountText
            tag="strong"
            class="liquidation-summary-rate"
            :value="formatSignedRate(totalProfitRate)"
          />
        </div>
      </section>

      <section class="investment-liquidation-list" aria-label="清仓产品列表">
        <article
          v-for="row in rows"
          :key="row.position.id"
          class="investment-liquidation-card"
          @click="openPositionDetail(row.position.id)"
        >
          <div class="liquidation-card-top">
            <div class="liquidation-product-main">
              <strong>{{ row.position.productName || '未命名产品' }}</strong>
              <span>{{ getProductCode(row.position) }}</span>
            </div>
            <div class="liquidation-profit">
              <span>累计收益</span>
              <AmountText
                tag="strong"
                class="liquidation-profit-value"
                :value="formatSignedCurrency(row.position.cumulativeProfit)"
              />
            </div>
          </div>

          <div class="liquidation-detail-grid">
            <div>
              <span>类型</span>
              <strong>{{ getProductTypeLabel(row.position) }}</strong>
            </div>
            <div>
              <span>卖出金额</span>
              <strong>{{ formatCurrency(row.sellAmount) }}</strong>
            </div>
          </div>

          <div class="liquidation-card-bottom">
            <span>清仓时间</span>
            <strong>{{ formatDateTime(row.lastSellAt || row.position.updatedAt) }}</strong>
          </div>
        </article>

        <p v-if="rows.length === 0" class="investment-liquidation-empty">暂无清仓投资产品</p>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
