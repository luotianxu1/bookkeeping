<script setup lang="ts">
// 行情工作台：统一搜索、市场页签和持久化子路由。
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import MarketSearchBox from './components/SearchBox.vue'

const router = useRouter()
const route = useRoute()
const isFund = computed(() =>
  route.path.startsWith('/finance/global-market/funds'),
)
const lastStockPath = ref('/finance/global-market/global')
const lastFundPath = ref('/finance/global-market/funds')
watch(
  () => route.fullPath,
  (path) => {
    if (!route.path.startsWith('/finance/global-market/')) return
    if (isFund.value) lastFundPath.value = path
    else lastStockPath.value = path
  },
  { immediate: true },
)
function selectProduct(fund: boolean) {
  if (fund !== isFund.value)
    void router.push(fund ? lastFundPath.value : lastStockPath.value)
}

/** 市场切换 tab：path 对应子路由 */
const TABS = [
  { label: '全球', path: '/finance/global-market/global' },
  { label: 'A股', path: '/finance/global-market/a' },
  { label: '港股', path: '/finance/global-market/hk' },
  { label: '美股', path: '/finance/global-market/us' },
  { label: 'ETF', path: '/finance/global-market/etf' },
  { label: '黄金', path: '/finance/global-market/gold' },
  { label: '板块', path: '/finance/global-market/sectors' },
] as const

const activePath = computed(() => {
  const match = TABS.find((t) => route.path.startsWith(t.path))
  return match?.path ?? ''
})

/** aria-current 属性值（显式类型，避免模板字面量宽化为 string） */

function select(path: string): void {
  if (route.path !== path) {
    void router.push(path)
  }
}
</script>
<template>
  <section class="desktop-workspace market-page">
    <PageHeader title="行情中心">
      <template #right>
        <MarketSearchBox v-if="!isFund" />
        <el-button-group>
          <el-button
            :type="!isFund ? 'primary' : undefined"
            @click="selectProduct(false)"
          >
            股票
          </el-button>
          <el-button
            :type="isFund ? 'primary' : undefined"
            @click="selectProduct(true)"
          >
            基金
          </el-button>
        </el-button-group>
      </template>
    </PageHeader>
    <el-tabs
      v-if="!isFund"
      :model-value="activePath"
      class="desktop-tabs"
      @tab-change="select(String($event))"
    >
      <el-tab-pane
        v-for="tab in TABS"
        :key="tab.path"
        :label="tab.label"
        :name="tab.path"
      />
    </el-tabs>
    <RouterView v-slot="{ Component }">
      <KeepAlive><component :is="Component" /></KeepAlive>
    </RouterView>
    <footer class="market-foot">
      {{
        isFund
          ? '数据来源：天天基金 / 新浪财经。净值按披露日更新，历史收益不代表未来表现。'
          : '数据来源：新浪财经 / 腾讯财经 / 纳斯达克官方 / 沪深港交易所。行情有延迟，不构成投资建议。'
      }}
    </footer>
  </section>
</template>
