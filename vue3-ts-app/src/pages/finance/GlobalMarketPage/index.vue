<script setup lang="ts">
// 全球行情页：通过后端代理展示全球品种实时行情（东方财富统一主源），按「股指」「商品·外汇·利率」分组展示。
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonSkeleton from '@/components/common/CommonSkeleton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getGlobalMarketQuotes, type GlobalMarketQuote } from '@/api/modules/finance'

const REFRESH_INTERVAL_MS = 15_000

// 分组展示：symbols 顺序即卡片顺序
const SYMBOL_GROUPS: { title: string; symbols: string[] }[] = [
  { title: '股指', symbols: ['SH000001', 'NDX', 'KS11', 'N225'] },
  { title: '美股·中证', symbols: ['NDX100', 'SPX', 'CSI500', 'CSI_DIVIDEND'] },
  { title: '商品', symbols: ['hf_XAU', 'hf_XAG', 'hf_OIL'] },
  { title: '外汇·利率', symbols: ['DINIW', 'US10Y'] },
]

const quotesBySymbol = reactive<Record<string, GlobalMarketQuote>>({})
const isRefreshing = ref(false)
const loadError = ref('')
let refreshTimer: number | null = null

const allSymbols = computed(() => SYMBOL_GROUPS.flatMap((group) => group.symbols))
const hasAnyQuote = computed(() => Object.keys(quotesBySymbol).length > 0)

function toneClass(quote: GlobalMarketQuote) {
  if (quote.change == null) {
    return ''
  }
  return quote.change >= 0 ? 'up' : 'down'
}

function formatNumber(value?: number | null) {
  if (value == null || !Number.isFinite(value)) {
    return '--'
  }
  return value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatChange(quote: GlobalMarketQuote) {
  if (quote.change == null || quote.changePercent == null) {
    return '--'
  }
  const sign = quote.change >= 0 ? '+' : ''
  return `${sign}${formatNumber(quote.change)}  ${sign}${quote.changePercent.toFixed(2)}%`
}

async function refresh() {
  if (isRefreshing.value) {
    return
  }
  isRefreshing.value = true
  try {
    const data = await getGlobalMarketQuotes(allSymbols.value)
    loadError.value = ''
    for (const key of Object.keys(quotesBySymbol)) {
      delete quotesBySymbol[key]
    }
    for (const quote of data) {
      quotesBySymbol[quote.symbol] = quote
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '行情加载失败'
  } finally {
    isRefreshing.value = false
  }
}

function startRefreshTimer() {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
  }
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState !== 'visible') {
      return
    }
    void refresh()
  }, REFRESH_INTERVAL_MS)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void refresh()
  }
}

onMounted(() => {
  void refresh()
  startRefreshTimer()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <section class="global-market-page" aria-label="全球行情">
    <header class="global-market-header">
      <PageHeader title="全球行情" back-label="返回更多功能">
        <template #right>
          <CommonHeaderRefreshButton
            label="刷新行情"
            :loading="isRefreshing"
            @click="refresh"
          />
        </template>
      </PageHeader>
    </header>

    <div v-if="loadError && !hasAnyQuote" class="global-market-error" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" @click="refresh">重新加载</button>
    </div>

    <div v-else-if="!loadError && !hasAnyQuote" class="global-market-placeholder" aria-live="polite">
      <CommonSkeleton variant="card" />
    </div>

    <template v-else>
      <section v-for="group in SYMBOL_GROUPS" :key="group.title" class="market-group" :aria-label="group.title">
        <h2 class="market-group-title">{{ group.title }}</h2>
        <div class="global-market-grid">
          <RouterLink
            v-for="symbol in group.symbols"
            v-show="quotesBySymbol[symbol]"
            :key="symbol"
            class="market-card"
            :class="quotesBySymbol[symbol] && toneClass(quotesBySymbol[symbol])"
            :to="`/finance/global-market/${symbol}`"
            :aria-label="`查看${quotesBySymbol[symbol]?.name ?? symbol}K线详情`"
          >
            <template v-if="quotesBySymbol[symbol]">
              <header class="market-card-head">
                <span class="market-name">{{ quotesBySymbol[symbol].name }}</span>
                <span class="market-code">{{ symbol }}</span>
              </header>

              <p class="market-price">{{ formatNumber(quotesBySymbol[symbol].price) }}</p>
              <p class="market-change">{{ formatChange(quotesBySymbol[symbol]) }}</p>
            </template>
          </RouterLink>
        </div>
      </section>
    </template>

    <footer class="global-market-foot">
      数据来源：东方财富行情接口（经服务端代理；美债收益率兜底数据来自美联储FRED），每 15 秒自动刷新。数据可能延迟，不构成投资建议。
    </footer>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
