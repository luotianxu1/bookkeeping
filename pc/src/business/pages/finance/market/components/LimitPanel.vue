<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

// 个股双列榜（通用模块）：
// - a：涨跌停个股（/limits，按成交额降序）
// - hk / us：领涨 / 领跌 TOP15（/movers，带流动性门槛，按涨跌幅排序）
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getLimits,
  getMovers,
  type ActivityMarket,
  type LimitStock,
  type MoverStock,
} from '@/business/api/modules/market'
import { ApiError } from '@/business/api/request'
import { amt, pct } from '@/business/utils/marketFormat'
import { useVisibilityPolling } from '@/business/composables/useVisibilityPolling'
import { detailRoute } from '../routes'

const props = defineProps<{ market?: ActivityMarket }>()
const router = useRouter()

type RankStock = LimitStock | MoverStock

const mk = computed<ActivityMarket>(() => props.market ?? 'a')

/* ---------- 状态 ---------- */
const data = ref<{
  up: RankStock[]
  down: RankStock[]
  extraNote: string
} | null>(null)
const loading = ref(true)
const error = ref('')
const REFRESH_MS = 60_000 // 与后端 /activity 缓存 TTL 对齐

/* ---------- 文案 ---------- */
const heading = computed(() => (mk.value === 'a' ? '涨跌停' : '涨跌幅榜'))
const upLabel = computed(() => (mk.value === 'a' ? '涨停' : '领涨'))
const downLabel = computed(() => (mk.value === 'a' ? '跌停' : '领跌'))
const floorNote = computed(() =>
  mk.value === 'hk'
    ? '成交额 ≥ 5000万港元'
    : mk.value === 'us'
      ? '成交额 ≥ 200万美元'
      : '按成交额排序',
)

/* 币种符号（成交额读数），与市场概况脚注口径一致 */
const currency = computed(() =>
  mk.value === 'us' ? '$' : mk.value === 'hk' ? 'HK$' : '¥',
)

/** 详情路由：A股详情接口只认 6 位代码，港/美股为对应类型 */
function open(code: string): void {
  void router.push(
    detailRoute(
      mk.value === 'a' ? 'stock' : mk.value === 'hk' ? 'hk' : 'us',
      code,
    ),
  )
}

/* ---------- 加载（静默刷新：有数据时不闪骨架） ---------- */
async function load(silent = false): Promise<void> {
  if (!silent) {
    loading.value = true
    error.value = ''
  }
  try {
    if (mk.value === 'a') {
      const d = await getLimits()
      data.value = {
        up: d.up,
        down: d.down,
        extraNote: `涨停 ${d.limit_up} 家 / 跌停 ${d.limit_down} 家 · ${floorNote.value}`,
      }
    } else {
      const d = await getMovers(mk.value === 'hk' ? 'hk' : 'us')
      data.value = {
        up: d.up,
        down: d.down,
        extraNote: `${upLabel.value} / ${downLabel.value} TOP15 · ${floorNote.value}`,
      }
    }
    error.value = ''
  } catch (e) {
    if (!data.value) {
      error.value =
        e instanceof ApiError
          ? e.message || '榜单数据加载失败'
          : '榜单数据加载失败'
    }
  } finally {
    if (!silent) loading.value = false
  }
}

// 可见性感知轮询：隐藏时跳过，恢复可见立即静默刷新（与后端 /activity 缓存 TTL 对齐）
// 定时器与可见性监听由 useVisibilityPolling 自动清理
useVisibilityPolling(() => load(true), REFRESH_MS)

onMounted(() => {
  load()
})

// 切市场（hk ↔ us）时重新拉取
watch(mk, () => {
  data.value = null
  load()
})

/* ---------- 阵营统计 ---------- */
/** 全模块最大单股成交额：热度条归一化基准 */

/** 热度条宽度百分比（按全模块最大成交额归一化） */

/** 入场错峰下标：长列表截断在 12 档，避免尾巴等太久 */
</script>
<template>
  <section class="desktop-workspace">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="load()">重试</el-button>
    </el-alert>
    <el-tabs class="desktop-tabs">
      <el-tab-pane :label="upLabel">
        <DesktopTable
          :rows="data?.up || []"
          :loading="loading"
          :title="heading"
          :paginate="false"
        >
          <!-- @vue-generic {RankStock} -->
          <el-table-column label="股票" min-width="160">
            <template #default="{ row }: { row: RankStock }">
              <el-button link type="primary" @click="open(row.code)">
                {{ row.name }}
              </el-button>
            </template>
          </el-table-column>
          <!-- @vue-generic {RankStock} -->
          <el-table-column label="涨跌幅" min-width="150">
            <template #default="{ row }: { row: RankStock }">
              {{ pct(row.pct) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {RankStock} -->
          <el-table-column label="成交额" min-width="150">
            <template #default="{ row }: { row: RankStock }">
              {{ amt(row.amount, currency) }}
            </template>
          </el-table-column>
        </DesktopTable>
      </el-tab-pane>
      <el-tab-pane :label="downLabel">
        <DesktopTable
          :rows="data?.down || []"
          :loading="loading"
          :title="heading"
          :paginate="false"
        >
          <!-- @vue-generic {RankStock} -->
          <el-table-column label="股票" min-width="160">
            <template #default="{ row }: { row: RankStock }">
              <el-button link type="primary" @click="open(row.code)">
                {{ row.name }}
              </el-button>
            </template>
          </el-table-column>
          <!-- @vue-generic {RankStock} -->
          <el-table-column label="涨跌幅" min-width="150">
            <template #default="{ row }: { row: RankStock }">
              {{ pct(row.pct) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {RankStock} -->
          <el-table-column label="成交额" min-width="150">
            <template #default="{ row }: { row: RankStock }">
              {{ amt(row.amount, currency) }}
            </template>
          </el-table-column>
        </DesktopTable>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
