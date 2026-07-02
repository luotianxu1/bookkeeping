<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getMarketNews, type MarketNews, type MarketNewsCategory } from '@/api/modules/finance'

type CategoryOption = {
  label: string
  value: MarketNewsCategory
}

const categoryOptions: CategoryOption[] = [
  { label: '7x24', value: 'all' },
  { label: '焦点', value: 'focus' },
  { label: '中国', value: 'china' },
  { label: '股市', value: 'stock' },
  { label: '商品', value: 'commodity' },
  { label: '基金', value: 'fund' },
  { label: '宏观', value: 'macro' },
]

const activeCategory = ref<MarketNewsCategory>('all')
const marketNews = ref<MarketNews | null>(null)
const isLoading = ref(false)
const isRefreshing = ref(false)
const pageError = ref('')
let requestId = 0

const currentCategoryLabel = computed(() => (
  categoryOptions.find((option) => option.value === activeCategory.value)?.label ?? '7x24'
))
const newsItems = computed(() => marketNews.value?.items ?? [])
const summaryText = computed(() => (
  newsItems.value.length > 0
    ? `最近 ${newsItems.value.length} 条 ${marketNews.value?.categoryLabel || currentCategoryLabel.value}快讯`
    : `${marketNews.value?.categoryLabel || currentCategoryLabel.value}快讯加载中`
))
const updatedAtText = computed(() => {
  const value = marketNews.value?.updatedAt
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return `更新于 ${formatDateTime(date)}`
})

onMounted(() => {
  void loadMarketNews()
})

watch(activeCategory, () => {
  void loadMarketNews()
})

async function loadMarketNews(forceRefresh = false) {
  const currentRequestId = ++requestId
  if (forceRefresh) {
    isRefreshing.value = true
  } else {
    isLoading.value = !marketNews.value
  }
  pageError.value = ''

  try {
    const data = await getMarketNews({
      category: activeCategory.value,
      limit: 20,
    })
    if (currentRequestId !== requestId) {
      return
    }
    marketNews.value = data
  } catch (error) {
    if (currentRequestId !== requestId) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '市场快讯加载失败'
  } finally {
    if (currentRequestId === requestId) {
      isLoading.value = false
      isRefreshing.value = false
    }
  }
}

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatPublishedAt(value: string | null) {
  if (!value) {
    return '--:--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }

  return formatDateTime(date)
}
</script>

<template>
  <section class="market-news-page" aria-label="市场快讯">
    <header class="market-news-header">
      <PageHeader title="市场快讯" back-to="/finance/more-features" back-label="返回更多功能">
        <template #right>
          <button
            class="market-news-refresh"
            type="button"
            :disabled="isRefreshing"
            aria-label="刷新市场快讯"
            @click="loadMarketNews(true)"
          >
            <span :class="['market-news-refresh-icon', { spinning: isRefreshing }]">↻</span>
          </button>
        </template>
      </PageHeader>
    </header>

    <section class="market-news-summary-card" aria-label="市场快讯概览">
      <div class="market-news-summary-main">
        <p>{{ marketNews?.categoryLabel || currentCategoryLabel }}市场快讯</p>
        <strong>{{ summaryText }}</strong>
        <span>{{ updatedAtText || '等待最新快讯更新' }}</span>
      </div>
      <div class="market-news-summary-side">
        <span>来源</span>
        <strong>{{ marketNews?.source || '东方财富' }}</strong>
      </div>
    </section>

    <section class="market-news-category-bar" aria-label="快讯分类">
      <button
        v-for="option in categoryOptions"
        :key="option.value"
        :class="['market-news-category-chip', { active: option.value === activeCategory }]"
        type="button"
        @click="activeCategory = option.value"
      >
        {{ option.label }}
      </button>
    </section>

    <p v-if="pageError" class="market-news-message market-news-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" text="市场快讯加载中..." />

    <section v-else class="market-news-list" aria-label="快讯列表">
      <a
        v-for="item in newsItems"
        :key="item.code"
        class="market-news-card"
        :href="item.url"
        target="_blank"
        rel="noreferrer"
      >
        <div class="market-news-card-top">
          <span class="market-news-time">{{ formatPublishedAt(item.publishedAt) }}</span>
          <span v-if="item.relatedStockCount > 0" class="market-news-related">
            关联 {{ item.relatedStockCount }} 个标的
          </span>
        </div>
        <strong :class="{ highlight: item.highlight }">{{ item.title }}</strong>
        <p>{{ item.summary }}</p>
        <div class="market-news-card-bottom">
          <span>{{ item.commentCount }} 评论</span>
          <span>查看原文</span>
        </div>
      </a>

      <p v-if="newsItems.length === 0" class="market-news-message">
        暂无快讯
      </p>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
