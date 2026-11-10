<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import { useAsyncSection } from '@/composables/useAsyncSection'
import { getFundRanking, searchMarketFunds, type FundType, type FundSort, type FundOrder, type FundRanking, type FundSearchResult } from '@/api/modules/fund-market'
import { pct, dirClass } from '@/utils/marketFormat'

const route = useRoute(), router = useRouter()
const types = [{ value: 'all', label: '全部' }, { value: 'gp', label: '股票型' }, { value: 'hh', label: '混合型' },
  { value: 'zq', label: '债券型' }, { value: 'zs', label: '指数型' }, { value: 'qdii', label: 'QDII' }, { value: 'fof', label: 'FOF' }]
const periods = [{ value: 'day', label: '日涨幅' }, { value: 'week', label: '近一周' }, { value: 'month', label: '近一月' },
  { value: 'quarter', label: '近三月' }, { value: 'halfYear', label: '近半年' }, { value: 'year', label: '近一年' }, { value: 'ytd', label: '今年来' }]
const type = computed(() => types.some(t => t.value === route.query.type) ? route.query.type as FundType : 'all')
const sort = computed(() => periods.some(p => p.value === route.query.sort) ? route.query.sort as FundSort : 'year')
const order = computed<FundOrder>(() => route.query.order === 'asc' ? 'asc' : 'desc')
const page = computed(() => /^\d+$/.test(String(route.query.page ?? '')) ? Math.max(1, Math.min(2000, Number(route.query.page))) : 1)
const keyword = computed(() => String(route.query.q ?? '').trim().slice(0, 60))
const draft = ref(keyword.value)
const ranking = ref<FundRanking | null>(null), searchResult = ref<FundSearchResult | null>(null)
const list = useAsyncSection(signal => getFundRanking({ type: type.value, sort: sort.value, order: order.value, page: page.value, pageSize: 20 }, signal),
  value => { ranking.value = value }, '基金行情加载失败')
const search = useAsyncSection(signal => searchMarketFunds(keyword.value, signal), value => { searchResult.value = value }, '基金搜索失败')
const periodLabel = computed(() => periods.find(p => p.value === sort.value)?.label)
const lastPage = computed(() => Math.max(1, Math.ceil((ranking.value?.total ?? 0) / 20)))
const fetchedAt = computed(() => ranking.value ? new Date(ranking.value.fetchedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '')
const nav = (value: number | null) => value == null ? '—' : value.toFixed(4)
let active = true, loadedKey = ''
function change(values: Record<string, string | number | undefined>) {
  void router.replace({ path: '/finance/global-market/funds', query: { ...route.query, page: undefined, ...values } })
}
function submitSearch() { change({ q: draft.value.trim() || undefined }) }
function clearSearch() { draft.value = ''; change({ q: undefined }) }
function load() {
  loadedKey = route.fullPath
  if (keyword.value) return search.run()
  return list.run()
}
watch(() => route.fullPath, () => {
  if (!active || route.name !== 'finance-market-funds') return
  draft.value = keyword.value
  list.reset(); search.reset(); ranking.value = null; searchResult.value = null
  void load()
})
onMounted(load)
onActivated(() => {
  active = true
  if (route.name !== 'finance-market-funds') return
  draft.value = keyword.value
  if (loadedKey !== route.fullPath) { list.reset(); search.reset(); ranking.value = null; searchResult.value = null }
  if (loadedKey !== route.fullPath || (!list.ready.value && !search.ready.value && !list.loading.value && !search.loading.value)) void load()
})
onDeactivated(() => { active = false; list.reset(); search.reset() })
</script>

<template>
  <section class="fund-page" aria-label="基金行情">
    <form class="fund-search" role="search" @submit.prevent="submitSearch">
      <input v-model="draft" type="search" maxlength="60" placeholder="搜索基金代码、名称或拼音" aria-label="搜索基金" />
      <CommonButton type="submit" size="sm">搜索</CommonButton>
    </form>
    <template v-if="keyword">
      <div class="fund-heading"><h2>基金搜索</h2><button class="text-button" @click="clearSearch">返回基金排行</button></div>
      <p v-if="search.error.value" class="fund-error" role="alert">{{ search.error.value }} <button @click="search.run">重试搜索</button></p>
      <CommonLoading v-if="search.loading.value" />
      <template v-else-if="search.ready.value">
        <p class="fund-note">“{{ keyword }}”共 {{ searchResult?.total ?? 0 }} 只{{ (searchResult?.total ?? 0) > 30 ? '，显示前30只，请细化关键词' : '' }}</p>
        <p v-if="!searchResult?.items.length" class="fund-empty">未找到匹配的净值型基金，试试基金代码或其他关键词。</p>
        <div v-else class="fund-list" aria-label="基金搜索结果">
          <RouterLink v-for="fund in searchResult?.items" :key="fund.code" class="fund-result" :to="`/finance/global-market/funds/${fund.code}`">
            <div><strong>{{ fund.name }}</strong><small>{{ fund.code }} · {{ fund.type }}</small></div><span aria-hidden="true">›</span>
          </RouterLink>
        </div>
      </template>
    </template>
    <template v-else>
      <nav class="fund-types" aria-label="基金类型">
        <button v-for="t in types" :key="t.value" :aria-pressed="type === t.value" :class="{ active: type === t.value }" @click="change({ type: t.value })">{{ t.label }}</button>
      </nav>
      <div class="fund-toolbar">
        <CommonSelect :model-value="sort" label="涨幅周期" :options="periods" @update:model-value="change({ sort: $event })" />
        <button class="sort-order" :aria-label="`当前${order === 'desc' ? '降序' : '升序'}，切换排序`" @click="change({ order: order === 'desc' ? 'asc' : 'desc' })">{{ order === 'desc' ? '从高到低 ↓' : '从低到高 ↑' }}</button>
        <CommonHeaderRefreshButton label="刷新基金行情" :loading="list.loading.value" @click="list.run" />
      </div>
      <p class="fund-note">净值型公募基金 · 按披露净值更新，货币基金不参与此排行</p>
      <p v-if="list.error.value" class="fund-error" role="alert">{{ list.error.value }} <button @click="list.run">重试行情</button></p>
      <CommonLoading v-if="list.loading.value && !list.ready.value" />
      <template v-else-if="ranking">
        <div class="fund-heading"><h2>基金排行</h2><span>{{ ranking.total.toLocaleString('zh-CN') }} 只</span></div>
        <div class="fund-list" aria-label="基金排行" :aria-busy="list.loading.value">
          <div class="fund-columns"><span>基金 / 净值日期</span><span>单位净值</span><span>{{ periodLabel }}</span></div>
          <RouterLink v-for="fund in ranking.items" :key="fund.code" class="fund-row" :to="`/finance/global-market/funds/${fund.code}`">
            <div class="fund-name"><strong>{{ fund.name }}</strong><small>{{ fund.code }} · {{ fund.navDate }}</small></div>
            <span class="num">{{ nav(fund.nav) }}</span><strong class="num" :class="dirClass(fund[sort])">{{ pct(fund[sort]) }}</strong>
          </RouterLink>
          <p v-if="!ranking.items.length" class="fund-empty">当前条件下暂无基金</p>
        </div>
        <div class="fund-pagination">
          <CommonButton size="sm" variant="secondary" :disabled="page <= 1 || list.loading.value" @click="change({ page: page - 1 })">上一页</CommonButton>
          <span>{{ page }} / {{ lastPage }}</span>
          <CommonButton size="sm" variant="secondary" :disabled="page >= lastPage || list.loading.value" @click="change({ page: page + 1 })">下一页</CommonButton>
        </div>
        <p class="fund-note">获取时间 {{ fetchedAt }} · 各基金净值日期见列表</p>
      </template>
    </template>
  </section>
</template>
<style scoped lang="scss" src="./fund.scss"></style>
