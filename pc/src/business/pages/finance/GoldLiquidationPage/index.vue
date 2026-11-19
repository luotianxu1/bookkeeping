<script setup lang="ts">
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'
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

function goldProfitToneClass(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  if (!Number.isFinite(amount) || amount === 0) {
    return ''
  }
  return amount > 0 ? 'tone-up' : 'tone-down'
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
  <section class="desktop-workspace desktop-list-workspace stagger-in">
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
      <DesktopRecordList
        title="黄金清仓记录"
        searchable
        search-placeholder="搜索清仓记录"
        :rows="records"
      >
        <template #default="{ row }: { row: GoldLiquidationRecord }">
          <article class="record-card gold-liquidation-card">
            <div class="record-card-head">
              <div class="record-card-title">
                <span class="gold-liquidation-time">
                  {{ formatDateTime(row.tradeAt) }}
                </span>
              </div>
              <div
                class="record-card-aside gold-liquidation-profit"
                :class="goldProfitToneClass(row.profit)"
              >
                {{ formatSignedAmount(row.profit) }}
              </div>
            </div>
            <div class="gold-liquidation-flow">
              <div class="gold-liquidation-price">
                <span>买入价</span>
                <strong>{{ formatAmount(row.buyPrice) }}</strong>
              </div>
              <span class="gold-liquidation-arrow" aria-hidden="true">
                →
              </span>
              <div class="gold-liquidation-price">
                <span>卖出价</span>
                <strong>{{ formatAmount(row.sellPrice) }}</strong>
              </div>
              <div class="gold-liquidation-price gold-liquidation-weight">
                <span>克重</span>
                <strong>{{ formatWeight(row.weight) }}</strong>
              </div>
            </div>
          </article>
        </template>
      </DesktopRecordList>
    </template>
  </section>
</template>

<style scoped>
/* 黄金清仓卡：时间+收益徽标首行，买入价 → 卖出价交易流 */
.gold-liquidation-time {
  font-variant-numeric: tabular-nums;
}

.gold-liquidation-profit {
  padding: 2px 10px;
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  background: var(--el-fill-color-light);
  border-radius: 999px;
}

/* 中国市场惯例：盈利红、亏损绿 */
.gold-liquidation-profit.tone-up {
  color: var(--color-danger);
  background: rgba(220, 38, 38, 0.08);
}

.gold-liquidation-profit.tone-down {
  color: var(--color-success);
  background: rgba(22, 163, 74, 0.08);
}

.gold-liquidation-flow {
  display: flex;
  align-items: center;
  gap: 12px;
}

.gold-liquidation-price {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.gold-liquidation-price strong {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.gold-liquidation-arrow {
  color: var(--el-text-color-placeholder);
}

.gold-liquidation-weight {
  margin-left: auto;
  text-align: right;
}
</style>
