<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import { getUsMarketOverview, type UsMarketIndexQuote, type UsMarketOverview } from '@/api/modules/finance'

const marketOverview = ref<UsMarketOverview | null>(null)
const isLoading = ref(false)
const isRefreshing = ref(false)
const errorMessage = ref('')
let refreshTimer: ReturnType<typeof window.setTimeout> | null = null

const indices = computed(() => marketOverview.value?.indices ?? [])
const pageFootText = computed(() => {
  if (!marketOverview.value?.updatedAt) {
    return '等待行情更新'
  }

  return `更新时间 ${formatTime(marketOverview.value.updatedAt)}`
})

function getStatusText(item: UsMarketIndexQuote) {
  if (item.stale) {
    return '使用最近一次成功缓存'
  }
  return '实时更新'
}

function getRefreshInterval() {
  return Math.max(marketOverview.value?.autoRefreshIntervalSeconds ?? 60, 30) * 1000
}

function scheduleRefresh() {
  clearRefreshTimer()
  refreshTimer = window.setTimeout(() => {
    void loadOverview(true)
  }, getRefreshInterval())
}

function clearRefreshTimer() {
  if (refreshTimer !== null) {
    window.clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

async function loadOverview(silent = false) {
  if (silent) {
    isRefreshing.value = true
  } else {
    isLoading.value = true
  }
  errorMessage.value = ''

  try {
    marketOverview.value = await getUsMarketOverview()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '美股行情加载失败'
  } finally {
    isLoading.value = false
    isRefreshing.value = false
    scheduleRefresh()
  }
}

function formatNumber(value?: number | null, fractionDigits = 2) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

function formatSigned(value?: number | null, fractionDigits = 2) {
  const normalized = Number(value ?? 0)
  const sign = normalized >= 0 ? '+' : ''
  return `${sign}${formatNumber(normalized, fractionDigits)}`
}

function formatChangePercent(value?: number | null) {
  return `${formatSigned(value)}%`
}

function formatTime(value?: string) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--'
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')

  return `${year}年${month}月${day}日 ${hour}:${minute}:${second}`
}

onMounted(() => {
  void loadOverview()
})

onBeforeUnmount(() => {
  clearRefreshTimer()
})
</script>

<template>
  <section class="us-market-page" aria-label="美股行情">
    <header class="us-market-header">
      <PageHeader title="美股" back-to="/finance/more-features" back-label="返回更多功能" />
      <button
        class="us-market-refresh"
        type="button"
        :disabled="isRefreshing"
        aria-label="刷新美股行情"
        @click="loadOverview(true)"
      >
        <span :class="['us-market-refresh-icon', { spinning: isRefreshing }]">↻</span>
      </button>
    </header>

    <section class="overview-card" aria-label="美股指数概览">
      <header class="card-head">
        <p>主要指数</p>
        <span>{{ isRefreshing ? '更新中...' : '自动刷新' }}</span>
      </header>
      <p v-if="isLoading && indices.length === 0" class="market-message">正在获取美股实时行情</p>
      <p v-if="errorMessage" class="market-message market-message-error">{{ errorMessage }}</p>
      <div class="market-list" role="list">
        <article v-for="item in indices" :key="item.code" class="market-list-item" role="listitem">
          <div class="market-list-left">
            <strong>{{ item.name }}</strong>
            <p>{{ item.alias }}</p>
          </div>
          <div class="market-list-right">
            <AmountText tag="strong" class="market-list-change" :value="formatChangePercent(item.changePercent)" />
            <span :class="['market-pill', { stale: item.stale }]">{{ getStatusText(item) }}</span>
          </div>
        </article>
      </div>
      <p class="market-foot">{{ pageFootText }}</p>
    </section>

    <section class="chart-section" aria-label="指数趋势图">
      <article v-for="item in indices" :key="`${item.code}-trend`" class="chart-card">
        <header class="card-head">
          <p>{{ item.name }}趋势</p>
          <span>{{ item.code }}</span>
        </header>
        <img class="chart-image" :src="item.trendImageUrl" :alt="`${item.name}趋势图`">
        <div class="chart-actions">
          <a :href="item.trendImageUrl" target="_blank" rel="noreferrer">查看趋势图</a>
          <a :href="item.klineImageUrl" target="_blank" rel="noreferrer">查看K线图</a>
        </div>
      </article>
    </section>

    <section class="tips-card" aria-label="行情说明">
      <header class="card-head">
        <p>数据说明</p>
        <span>行情聚合</span>
      </header>
      <p>标普500采用国际指数实时报价，纳指100采用 Nasdaq 图表镜像，趋势图使用东方财富公开图像。</p>
      <p>当外部源暂时异常时，会自动保留最近一次成功抓取的数据，避免页面空白。</p>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
