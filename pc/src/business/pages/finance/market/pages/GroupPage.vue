<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

// 港股、美股、ETF 和黄金工作台：报价表、市场概况与板块排名。
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getErrorMessage } from '@/business/api/request'
import {
  getQuotes,
  getUsSectors,
  type MarketGroup,
  type QuoteRow,
  type SectorRow,
} from '@/business/api/modules/market'
import { dirClass, f2, pct } from '@/business/utils/marketFormat'
import { detailRoute } from '../routes'
import MarketOverview from '../components/MarketOverview.vue'
import LimitPanel from '../components/LimitPanel.vue'
import SectorHighlight from '../components/SectorHighlight.vue'

const props = defineProps<{ group: MarketGroup }>()
const router = useRouter()

/* ---------- 状态 ---------- */
const quotes = ref<QuoteRow[]>([])
const loading = ref(true)
const error = ref('')

/* 美股行业板块（仅 us tab 加载） */
const usSectors = ref<SectorRow[]>([])
const usSectorLoading = ref(false)
const usSectorError = ref('')

function errText(e: unknown, fallback: string): string {
  return getErrorMessage(e, fallback)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const data = await getQuotes(props.group)
    quotes.value = data.data
  } catch (e) {
    error.value = errText(e, '行情加载失败')
  } finally {
    loading.value = false
  }
  if (props.group === 'us') loadUsSectors()
}

async function loadUsSectors(): Promise<void> {
  usSectorLoading.value = true
  usSectorError.value = ''
  try {
    usSectors.value = (await getUsSectors()).data
  } catch (e) {
    usSectorError.value = errText(e, '美股行业数据加载失败')
  } finally {
    usSectorLoading.value = false
  }
}

/* 切换 tab 时重新加载对应分组 */
watch(() => props.group, load)
onMounted(load)

/** 详情路由：ETF 是 A 场内基金走 stock，其余分组同名 kind */
function openQuote(code: string): void {
  const kind =
    props.group === 'etf'
      ? 'stock'
      : props.group === 'global'
        ? 'global'
        : props.group
  void router.push(detailRoute(kind, code))
}
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
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <DesktopTable
      :rows="quotes"
      :loading="loading"
      title="市场报价"
      search-placeholder="搜索代码或名称"
      searchable
    >
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
          <el-button link type="primary" @click="openQuote(row['代码'])">
            查看行情
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
    <div class="desktop-columns">
      <MarketOverview v-if="group === 'hk' || group === 'us'" :market="group" />
      <LimitPanel v-if="group === 'hk' || group === 'us'" :market="group" />
    </div>
    <SectorHighlight
      v-if="group === 'us'"
      kind="industry"
      heading="行业板块"
      :show-more="false"
      currency="$"
      :sectors="usSectors"
      :loading="usSectorLoading"
      :error="usSectorError"
      @retry="loadUsSectors"
    />
  </section>
</template>
