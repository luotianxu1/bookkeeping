<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

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
  <section class="desktop-workspace">
    <PageHeader title="市场快讯"><template #right></template></PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <DesktopTable
      :rows="newsItems"
      :loading="isLoading"
      title="实时快讯"
      search-placeholder="搜索新闻标题"
      searchable
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
      <!-- @vue-generic {NewsRow} -->
      <el-table-column label="发布时间" min-width="180">
        <template #default="{ row }: { row: NewsRow }">
          {{ formatPublishedAt(row.publishedAt) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {NewsRow} -->
      <el-table-column label="标题" min-width="500">
        <template #default="{ row }: { row: NewsRow }">
          <a
            :href="row.url"
            target="_blank"
            rel="noreferrer"
            class="desktop-news-title"
          >
            {{ row.title }}
          </a>
          <p class="desktop-note">{{ row.summary }}</p>
        </template>
      </el-table-column>
      <el-table-column
        prop="relatedStockCount"
        label="关联标的"
        min-width="110"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="commentCount"
        label="评论"
        min-width="90"
        sortable="custom"
        show-overflow-tooltip
      />
    </DesktopTable>
  </section>
</template>
