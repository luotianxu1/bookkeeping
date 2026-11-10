<script setup lang="ts">
// 港股/美股/ETF/黄金 tab：核心行情方块阵 + 市场概况/榜单/行业板块（移植自 investment MarketPage）。
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
  <div class="pc-page group-page">
    <!-- 核心行情方块阵置顶 -->
    <p v-if="error" class="error-box" role="alert">
      行情加载失败：{{ error }}
      <el-button class="retry" native-type="button" @click="load">
        重试
      </el-button>
    </p>
    <div v-else-if="loading" class="mkt-tiles" aria-busy="true">
      <div v-for="n in 9" :key="n" class="mkt-tile mkt-tile-skel"></div>
    </div>
    <!-- 紧凑方块阵：名称 → 价格 → 涨跌幅；全部分组可点击进详情 -->
    <div v-else-if="quotes.length" class="mkt-tiles">
      <el-button
        v-for="(q, idx) in quotes"
        :key="q['代码']"
        class="mkt-tile"
        :style="{ '--rise-delay': `${Math.min(idx, 12) * 40}ms` }"
        :aria-label="`${q['名称']} 详情`"
        @click="openQuote(q['代码'])"
      >
        <span class="mkt-tile-name">{{ q['名称'] }}</span>
        <span class="num mkt-tile-price" :class="dirClass(q['涨跌幅'])">
          {{ f2(q['最新价']) }}
        </span>
        <span class="num mkt-tile-pct" :class="dirClass(q['涨跌幅'])">
          {{ pct(q['涨跌幅']) }}
        </span>
      </el-button>
    </div>
    <p v-else class="empty">暂无数据</p>
    <!-- 港股 / 美股页的市场概况（全市场口径涨跌分布） -->
    <MarketOverview v-if="group === 'hk' || group === 'us'" :market="group" />
    <!-- 港股/美股涨跌幅榜（对应 A股 tab 的涨跌停模块） -->
    <LimitPanel v-if="group === 'hk' || group === 'us'" :market="group" />
    <!-- 美股行业板块（对应 A股 tab 的概念板块模块；港股无行业分类数据源） -->
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
  </div>
</template>
