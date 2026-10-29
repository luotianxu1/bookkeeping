<script setup lang="ts">
// 港股/美股/ETF/黄金 tab：核心行情方块阵 + 市场概况/榜单/行业板块（移植自 investment MarketPage）。
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getQuotes,
  getUsSectors,
  type MarketGroup,
  type QuoteRow,
  type SectorRow,
} from '@/api/modules/market'
import { dirClass, f2, pct } from '@/utils/marketFormat'
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
  return e instanceof Error ? e.message || fallback : fallback
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
  const kind = props.group === 'etf' ? 'stock' : props.group === 'global' ? 'global' : props.group
  void router.push(detailRoute(kind, code))
}
</script>

<template>
  <div class="group-page">
    <!-- 核心行情方块阵置顶 -->
    <p v-if="error" class="error-box" role="alert">
      行情加载失败：{{ error }}
      <button class="retry" type="button" @click="load">重试</button>
    </p>

    <div v-else-if="loading" class="mkt-tiles" aria-busy="true">
      <div v-for="n in 9" :key="n" class="mkt-tile mkt-tile-skel"></div>
    </div>

    <!-- 紧凑方块阵：名称 → 价格 → 涨跌幅；全部分组可点击进详情 -->
    <div v-else-if="quotes.length" class="mkt-tiles">
      <button
        v-for="(q, idx) in quotes"
        :key="q['代码']"
        class="mkt-tile"
        :style="{ '--rise-delay': `${Math.min(idx, 12) * 40}ms` }"
        :aria-label="`${q['名称']} 详情`"
        @click="openQuote(q['代码'])"
      >
        <span class="mkt-tile-name">{{ q['名称'] }}</span>
        <span class="num mkt-tile-price" :class="dirClass(q['涨跌幅'])">{{ f2(q['最新价']) }}</span>
        <span class="num mkt-tile-pct" :class="dirClass(q['涨跌幅'])">{{ pct(q['涨跌幅']) }}</span>
      </button>
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

<style scoped lang="scss">
.group-page {
  display: grid;
  gap: var(--space-16);

  > * {
    min-width: 0;
  }
}

.num {
  font-variant-numeric: tabular-nums;
}

/* 方块阵样式已抽到 ../tiles.scss（.mkt-tiles/.mkt-tile*），与 A 股核心指数共用 */

.error-box {
  margin: 0;
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

.retry {
  min-height: 36px;
  margin-left: var(--space-10);
  padding: 0 var(--space-16);
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-brand);
  font: inherit;
  font-size: var(--font-size-12);
  font-weight: 700;
  cursor: pointer;
}

.empty {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
  text-align: center;
  padding: var(--space-20) 0;
}
</style>
