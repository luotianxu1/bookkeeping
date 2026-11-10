<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

// A 股工作台：指数、涨跌分布、涨跌停、板块与个股快照。
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

/** 打开指数详情 */
function openIndex(code: string): void {
  void router.push(detailRoute('index', code))
}

onMounted(loadAll)

type QuoteRow = (typeof indices.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <el-alert
      v-if="idxError"
      :title="idxError"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="loadAll">重试</el-button>
    </el-alert>
    <DesktopTable :rows="indices" :loading="idxLoading" title="核心指数">
      <el-table-column
        prop="代码"
        label="代码"
        min-width="140"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="名称"
        label="名称"
        min-width="200"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {QuoteRow} -->
      <el-table-column label="最新价" min-width="150">
        <template #default="{ row }: { row: QuoteRow }">
          <span :class="dirClass(row['涨跌幅'])">{{ f2(row['最新价']) }}</span>
        </template>
      </el-table-column>
      <!-- @vue-generic {QuoteRow} -->
      <el-table-column label="涨跌幅" min-width="150">
        <template #default="{ row }: { row: QuoteRow }">
          <span :class="dirClass(row['涨跌幅'])">{{ pct(row['涨跌幅']) }}</span>
        </template>
      </el-table-column>
      <!-- @vue-generic {QuoteRow} -->
      <el-table-column fixed="right" label="操作" width="150">
        <template #default="{ row }: { row: QuoteRow }">
          <el-button link type="primary" @click="openIndex(row['代码'])">
            查看行情
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
    <div class="desktop-columns">
      <MarketOverview market="a" />
      <LimitPanel market="a" />
    </div>
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
  </section>
</template>
