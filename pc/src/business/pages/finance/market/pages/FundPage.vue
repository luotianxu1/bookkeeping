<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import {
  computed,
  onActivated,
  onDeactivated,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonHeaderRefreshButton from '@/business/components/common/CommonHeaderRefreshButton/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'

import { useAsyncSection } from '@/business/composables/useAsyncSection'
import {
  getFundRanking,
  searchMarketFunds,
  type FundType,
  type FundSort,
  type FundOrder,
  type FundRanking,
  type FundSearchResult,
} from '@/business/api/modules/fund-market'
import { pct, dirClass } from '@/business/utils/marketFormat'

const route = useRoute(),
  router = useRouter()
const types = [
  { value: 'all', label: '全部' },
  { value: 'gp', label: '股票型' },
  { value: 'hh', label: '混合型' },
  { value: 'zq', label: '债券型' },
  { value: 'zs', label: '指数型' },
  { value: 'qdii', label: 'QDII' },
  { value: 'fof', label: 'FOF' },
]
const periods = [
  { value: 'day', label: '日涨幅' },
  { value: 'week', label: '近一周' },
  { value: 'month', label: '近一月' },
  { value: 'quarter', label: '近三月' },
  { value: 'halfYear', label: '近半年' },
  { value: 'year', label: '近一年' },
  { value: 'ytd', label: '今年来' },
]
const type = computed(() =>
  types.some((t) => t.value === route.query.type)
    ? (route.query.type as FundType)
    : 'all',
)
const sort = computed(() =>
  periods.some((p) => p.value === route.query.sort)
    ? (route.query.sort as FundSort)
    : 'year',
)
const order = computed<FundOrder>(() =>
  route.query.order === 'asc' ? 'asc' : 'desc',
)
const page = computed(() =>
  /^\d+$/.test(String(route.query.page ?? ''))
    ? Math.max(1, Math.min(2000, Number(route.query.page)))
    : 1,
)
const keyword = computed(() =>
  String(route.query.q ?? '')
    .trim()
    .slice(0, 60),
)
const draft = ref(keyword.value)
const ranking = ref<FundRanking | null>(null),
  searchResult = ref<FundSearchResult | null>(null)
const list = useAsyncSection(
  (signal) =>
    getFundRanking(
      {
        type: type.value,
        sort: sort.value,
        order: order.value,
        page: page.value,
        pageSize: 20,
      },
      signal,
    ),
  (value) => {
    ranking.value = value
  },
  '基金行情加载失败',
)
const search = useAsyncSection(
  (signal) => searchMarketFunds(keyword.value, signal),
  (value) => {
    searchResult.value = value
  },
  '基金搜索失败',
)

const lastPage = computed(() =>
  Math.max(1, Math.ceil((ranking.value?.total ?? 0) / 20)),
)
const fetchedAt = computed(() =>
  ranking.value
    ? new Date(ranking.value.fetchedAt).toLocaleString('zh-CN', {
        timeZone: 'Asia/Shanghai',
        hour12: false,
      })
    : '',
)
const nav = (value: number | null) => (value == null ? '—' : value.toFixed(4))
let active = true,
  loadedKey = ''
function change(values: Record<string, string | number | undefined>) {
  const target = {
    path: '/finance/global-market/funds',
    query: { ...route.query, page: undefined, ...values },
  }
  if (router.resolve(target).fullPath === route.fullPath) void load()
  else void router.replace(target)
}
function submitSearch() {
  change({ q: draft.value.trim() || undefined })
}
function clearSearch() {
  draft.value = ''
  change({ q: undefined })
}
function load() {
  loadedKey = route.fullPath
  if (keyword.value) return search.run()
  return list.run()
}
watch(
  () => route.fullPath,
  () => {
    if (!active || route.name !== 'finance-market-funds') return
    draft.value = keyword.value
    list.reset()
    search.reset()
    ranking.value = null
    searchResult.value = null
    void load()
  },
)
onMounted(load)
onActivated(() => {
  active = true
  if (route.name !== 'finance-market-funds') return
  draft.value = keyword.value
  if (loadedKey !== route.fullPath) {
    list.reset()
    search.reset()
    ranking.value = null
    searchResult.value = null
  }
  if (
    loadedKey !== route.fullPath ||
    (!list.ready.value &&
      !search.ready.value &&
      !list.loading.value &&
      !search.loading.value)
  )
    void load()
})
onDeactivated(() => {
  active = false
  list.reset()
  search.reset()
})

type FundRow = NonNullable<typeof ranking.value>['items'][number]
type SearchRow = NonNullable<typeof searchResult.value>['items'][number]
</script>
<template>
  <section class="desktop-workspace">
    <PagePanel title="基金查询">
      <el-form
        class="fund-search"
        role="search"
        @submit.prevent="submitSearch"
        label-position="top"
      >
        <DesktopInput
          v-model="draft"
          type="search"
          maxlength="60"
          placeholder="搜索基金代码、名称或拼音"
          aria-label="搜索基金"
        />
        <CommonButton type="submit" size="sm">搜索</CommonButton>
      </el-form>
    </PagePanel>
    <template v-if="keyword">
      <el-alert
        v-if="search.error.value"
        :title="search.error.value"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="search.run">重试</el-button>
      </el-alert>
      <DesktopTable
        :rows="searchResult?.items || []"
        :loading="search.loading.value"
        title="搜索结果"
      >
        <template #toolbar>
          <el-button @click="clearSearch">返回排行</el-button>
        </template>
        <!-- @vue-generic {{ name: string; code: string }} -->
        <el-table-column label="基金" min-width="260">
          <template #default="{ row }: { row: { name: string; code: string } }">
            <RouterLink :to="'/finance/global-market/funds/' + row.code">
              {{ row.name }}
            </RouterLink>
          </template>
        </el-table-column>
        <el-table-column
          prop="code"
          label="代码"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="type"
          label="类型"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {SearchRow} -->
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }: { row: SearchRow }">
            <RouterLink :to="'/finance/global-market/funds/' + row.code">
              <el-button link type="primary">查看基金</el-button>
            </RouterLink>
          </template>
        </el-table-column>
      </DesktopTable>
    </template>
    <template v-else>
      <div class="desktop-filter-bar">
        <CommonSelect
          :model-value="type"
          label="基金类型"
          :options="types"
          @update:model-value="change({ type: $event })"
        />
        <div class="pc-toolbar fund-toolbar">
          <CommonSelect
            :model-value="sort"
            label="涨幅周期"
            :options="periods"
            @update:model-value="change({ sort: $event })"
          />
          <el-button
            class="sort-order"
            :aria-label="`当前${order === 'desc' ? '降序' : '升序'}，切换排序`"
            @click="change({ order: order === 'desc' ? 'asc' : 'desc' })"
          >
            {{ order === 'desc' ? '从高到低 ↓' : '从低到高 ↑' }}
          </el-button>
          <CommonHeaderRefreshButton
            label="刷新基金行情"
            :loading="list.loading.value"
            @click="list.run"
          />
        </div>
      </div>
      <el-alert
        v-if="list.error.value"
        :title="list.error.value"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="list.run">重试</el-button>
      </el-alert>
      <DesktopTable
        :paginate="false"
        :rows="ranking?.items || []"
        :loading="list.loading.value"
        title="基金排行"
      >
        <!-- @vue-generic {{ name: string; code: string }} -->
        <el-table-column label="基金" min-width="260">
          <template #default="{ row }: { row: { name: string; code: string } }">
            <RouterLink :to="'/finance/global-market/funds/' + row.code">
              {{ row.name }}
            </RouterLink>
          </template>
        </el-table-column>
        <el-table-column
          prop="code"
          label="代码"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="navDate"
          label="净值日期"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {FundRow} -->
        <el-table-column label="单位净值" min-width="150">
          <template #default="{ row }: { row: FundRow }">
            {{ nav(row.nav) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {FundRow} -->
        <el-table-column label="区间涨幅" min-width="150">
          <template #default="{ row }: { row: FundRow }">
            <span :class="dirClass(row[sort])">{{ pct(row[sort]) }}</span>
          </template>
        </el-table-column>
        <!-- @vue-generic {FundRow} -->
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }: { row: FundRow }">
            <RouterLink :to="'/finance/global-market/funds/' + row.code">
              <el-button link type="primary">查看基金</el-button>
            </RouterLink>
          </template>
        </el-table-column>
      </DesktopTable>
      <div class="desktop-pagination">
        <span>共 {{ ranking?.total || 0 }} 只</span>
        <el-pagination
          :current-page="page"
          :page-count="lastPage"
          layout="prev, pager, next"
          :disabled="list.loading.value"
          @current-change="change({ page: $event })"
        />
      </div>
    </template>
  </section>
</template>
