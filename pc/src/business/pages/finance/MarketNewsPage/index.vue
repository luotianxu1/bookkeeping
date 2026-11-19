<script setup lang="ts">
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

import { computed, onMounted, ref, watch } from 'vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getMarketNews,
  type MarketNews,
  type MarketNewsCategory,
} from '@/business/api/modules/market'
import { getErrorMessage } from '@/business/api/request'

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
const isLoading = ref(true)
const isRefreshing = ref(false)
const pageError = ref('')
let requestId = 0

const newsItems = computed(() => marketNews.value?.items ?? [])

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
    pageError.value = getErrorMessage(error, '市场快讯加载失败')
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

type NewsRow = (typeof newsItems.value)[number]
</script>
<template>
  <section class="desktop-workspace stagger-in">
    <PageHeader title="市场快讯"><template #right></template></PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <DesktopRecordList
      class="news-list"
      :rows="newsItems"
      :loading="isLoading"
      title="实时快讯"
      search-placeholder="搜索新闻标题 / 摘要"
      searchable
      :search-keys="['title', 'summary']"
      row-key="code"
      empty-title="暂无快讯"
      empty-hint="切换分类或稍后刷新试试"
    >
      <template #toolbar>
        <el-select v-model="activeCategory" aria-label="快讯分类">
          <el-option
            v-for="option in categoryOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </template>
      <template #default="{ row }: { row: NewsRow }">
        <article
          class="news-entry"
          :class="{ 'news-entry--highlight': row.highlight }"
        >
          <div class="news-entry-head">
            <a
              :href="row.url"
              target="_blank"
              rel="noreferrer"
              class="news-entry-title"
            >
              {{ row.title }}
            </a>
            <span v-if="row.highlight" class="news-entry-flag">热点</span>
          </div>
          <p v-if="row.summary" class="news-entry-summary">{{ row.summary }}</p>
          <div class="news-entry-meta">
            <span class="news-entry-time">
              {{ formatPublishedAt(row.publishedAt) }}
            </span>
            <span v-if="row.relatedStockCount > 0">
              关联标的 {{ row.relatedStockCount }}
            </span>
            <span v-if="row.commentCount > 0">
              评论 {{ row.commentCount }}
            </span>
          </div>
        </article>
      </template>
    </DesktopRecordList>
  </section>
</template>

<style scoped>
/* 新闻列表式条目：单列、热点左侧红条强调，标题外链 + 摘要两行截断。 */
.news-list :deep(.record-list-grid) {
  grid-template-columns: 1fr;
  gap: 10px;
}
.news-entry {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-left-width: 3px;
  border-radius: 10px;
  min-width: 0;
}
.news-entry--highlight {
  border-left-color: var(--color-danger);
  background: linear-gradient(
    150deg,
    var(--el-color-danger-light-9),
    var(--el-bg-color) 40%
  );
}
.news-entry-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.news-entry-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  text-decoration: none;
}
.news-entry-title:hover {
  color: var(--el-color-primary);
}
.news-entry-flag {
  flex-shrink: 0;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  color: var(--color-danger);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 999px;
}
.news-entry-summary {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.news-entry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.news-entry-time {
  font-weight: 500;
  color: var(--el-text-color-regular);
}
</style>
