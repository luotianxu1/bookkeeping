<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'
import { formatAmount } from '@/business/utils/format'
// 黄金清仓记录页：通过后端接口展示累计收益和清仓明细。
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getGoldLiquidations,
  type GoldLiquidation,
  type GoldLiquidationRecord,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const isLoading = ref(true)
const pageError = ref('')
const liquidation = ref<GoldLiquidation>({
  cumulativeWeight: 0,
  cumulativeProfit: 0,
  records: [],
})
let requestVersion = 0

const selectedAccountId = computed(() => parseAccountId(route.query.accountId))
const records = computed<GoldLiquidationRecord[]>(() => {
  const allRecords = liquidation.value.records ?? []
  if (!selectedAccountId.value) {
    return allRecords
  }

  return allRecords.filter(
    (record) => record.accountId === selectedAccountId.value,
  )
})
const visibleCumulativeWeight = computed(() =>
  Number(
    records.value
      .reduce((total, record) => total + Number(record.weight ?? 0), 0)
      .toFixed(6),
  ),
)
const visibleCumulativeProfit = computed(() =>
  Number(
    records.value
      .reduce((total, record) => total + Number(record.profit ?? 0), 0)
      .toFixed(2),
  ),
)
const backTo = computed(() =>
  selectedAccountId.value
    ? `/finance/accounts/gold/position?accountId=${selectedAccountId.value}`
    : '/finance/accounts/gold/position',
)

onMounted(() => {
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
    pageError.value = '请先登录后查看清仓记录'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const response = await getGoldLiquidations(currentUser.id)

    if (currentRequestVersion !== requestVersion) {
      return
    }

    liquidation.value = response
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = getErrorMessage(error, '清仓记录加载失败')
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function formatWeight(value: number | null | undefined) {
  return `${Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })}g`
}

function formatSignedAmount(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}${formatAmount(Math.abs(amount))}`
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
  <section class="pc-page gold-liquidation-page" aria-label="清仓记录">
    <PageHeader
      title="清仓记录"
      back-label="返回黄金账户持仓"
      :back-to="backTo"
    />
    <p
      v-if="pageError"
      class="gold-liquidation-message gold-liquidation-message-error"
    >
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />
    <template v-else>
      <section class="liquidation-summary">
        <div class="summary-row">
          <span>累计克重</span>
          <strong class="weight">
            {{ formatWeight(visibleCumulativeWeight) }}
          </strong>
        </div>
        <div class="summary-row">
          <span>累计收益</span>
          <strong
            class="profit"
            :class="{ negative: visibleCumulativeProfit < 0 }"
          >
            {{ formatSignedAmount(visibleCumulativeProfit) }}
          </strong>
        </div>
      </section>
      <DesktopTable :rows="records">
        <!-- @vue-generic {GoldLiquidationRecord} -->
        <el-table-column label="卖出时间" min-width="160">
          <template #default="{ row }: { row: GoldLiquidationRecord }">
            {{ formatDateTime(row.tradeAt) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {GoldLiquidationRecord} -->
        <el-table-column label="克重" min-width="160">
          <template #default="{ row }: { row: GoldLiquidationRecord }">
            {{ formatWeight(row.weight) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {GoldLiquidationRecord} -->
        <el-table-column label="买入价" min-width="160">
          <template #default="{ row }: { row: GoldLiquidationRecord }">
            {{ formatAmount(row.buyPrice) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {GoldLiquidationRecord} -->
        <el-table-column label="卖出价" min-width="160">
          <template #default="{ row }: { row: GoldLiquidationRecord }">
            {{ formatAmount(row.sellPrice) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {GoldLiquidationRecord} -->
        <el-table-column label="收益" min-width="160">
          <template #default="{ row }: { row: GoldLiquidationRecord }">
            {{ formatSignedAmount(row.profit) }}
          </template>
        </el-table-column>
      </DesktopTable>
    </template>
  </section>
</template>
