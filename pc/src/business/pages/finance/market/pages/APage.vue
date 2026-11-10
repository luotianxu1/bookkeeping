<script setup lang="ts">
// A 股 tab：核心指数条 + 市场概况 + 涨跌停 + 概念板块 + 快照表（移植自 investment Home 页）。
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getErrorMessage } from '@/business/api/request'
import {
  getIndexSpot,
  getSectorSpot,
  getStockSpot,
  type IndexSpotRow,
  type SectorRow,
  type SpotRow,
} from '@/business/api/modules/market'
import { sectorsRoute, detailRoute } from '../routes'
import { dirClass, f2, pct } from '@/business/utils/marketFormat'
import MarketOverview from '../components/MarketOverview.vue'
import LimitPanel from '../components/LimitPanel.vue'
import SectorHighlight from '../components/SectorHighlight.vue'
import StockTable from '../components/StockTable.vue'

const router = useRouter()

/* ---------- A 股页状态 ---------- */
const indices = ref<IndexSpotRow[]>([])
const idxLoading = ref(true)
const idxError = ref('')
const stocks = ref<SpotRow[]>([])
const stockLoading = ref(true)
const stockError = ref('')
const sectors = ref<SectorRow[]>([])
const sectorLoading = ref(true)
const sectorError = ref('')

function errText(e: unknown, fallback: string): string {
  return getErrorMessage(e, fallback)
}

/* ---------- 数据加载（一次并发拉全，各自独立容错） ---------- */
async function loadAll(): Promise<void> {
  idxLoading.value = true
  stockLoading.value = true
  sectorLoading.value = true
  idxError.value = ''
  stockError.value = ''
  sectorError.value = ''

  const [idxRes, stockRes, sectorRes] = await Promise.allSettled([
    getIndexSpot(),
    getStockSpot(20),
    getSectorSpot('concept'),
  ])

  if (idxRes.status === 'fulfilled') {
    indices.value = idxRes.value.data
  } else {
    idxError.value = errText(idxRes.reason, '指数数据加载失败')
  }

  if (stockRes.status === 'fulfilled') {
    stocks.value = stockRes.value.data
  } else {
    stockError.value = errText(stockRes.reason, '快照数据加载失败')
  }

  if (sectorRes.status === 'fulfilled') {
    sectors.value = sectorRes.value.data
  } else {
    sectorError.value = errText(sectorRes.reason, '板块数据加载失败')
  }

  idxLoading.value = false
  stockLoading.value = false
  sectorLoading.value = false
}

function goSectors(): void {
  void router.push(sectorsRoute())
}

/** 指数方块点击 → 指数详情 */
function openIndex(code: string): void {
  void router.push(detailRoute('index', code))
}

onMounted(loadAll)
</script>

<template>
  <div class="pc-page a-page">
    <!-- 核心指数：与其他分组页一致的紧凑方块阵（无标题，与 tiles 完全同款） -->
    <section aria-label="核心指数行情" class="idx-section">
      <p v-if="idxError" class="error-box" role="alert">
        指数行情加载失败：{{ idxError }}
        <el-button class="retry" native-type="button" @click="loadAll">
          重试
        </el-button>
      </p>
      <div v-else-if="idxLoading" class="mkt-tiles" aria-busy="true">
        <div v-for="n in 6" :key="n" class="mkt-tile mkt-tile-skel"></div>
      </div>
      <div v-else class="mkt-tiles">
        <el-button
          v-for="(i, idx) in indices"
          :key="i['代码']"
          class="mkt-tile"
          native-type="button"
          :style="{ '--rise-delay': `${Math.min(idx, 12) * 40}ms` }"
          :aria-label="`${i['名称']} 详情`"
          @click="openIndex(i['代码'])"
        >
          <span class="mkt-tile-name">{{ i['名称'] }}</span>
          <span class="num mkt-tile-price" :class="dirClass(i['涨跌幅'])">
            {{ f2(i['最新价']) }}
          </span>
          <span class="num mkt-tile-pct" :class="dirClass(i['涨跌幅'])">
            {{ pct(i['涨跌幅']) }}
          </span>
        </el-button>
      </div>
    </section>
    <MarketOverview market="a" />
    <LimitPanel market="a" />
    <SectorHighlight
      kind="concept"
      :sectors="sectors"
      :loading="sectorLoading"
      :error="sectorError"
      @retry="loadAll"
      @more="goSectors"
    />
    <StockTable
      :stocks="stocks"
      :loading="stockLoading"
      :error="stockError"
      @retry="loadAll"
    />
  </div>
</template>
