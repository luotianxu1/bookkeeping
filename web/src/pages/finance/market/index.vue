<script setup lang="ts">
// 行情中心外壳：搜索 + 市场切换 tab + 子路由视图（移植自 investment 项目前端，浅色移动端主题）。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import MarketSearchBox from './components/SearchBox.vue'

const router = useRouter()
const route = useRoute()

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
function currentAttr(path: string): 'page' | undefined {
  return activePath.value === path ? 'page' : undefined
}

function select(path: string): void {
  if (route.path !== path) {
    void router.push(path)
  }
}
</script>

<template>
  <section class="market-page" aria-label="行情中心">
    <header class="market-header">
      <PageHeader
        title="行情中心"
        back-to="/finance/more-features"
        back-label="返回更多功能"
        :prefer-back-to="true"
      />
      <MarketSearchBox />
      <nav class="market-tabs" aria-label="市场切换">
        <button
          v-for="t in TABS"
          :key="t.path"
          class="market-tab"
          :class="{ active: activePath === t.path }"
          :aria-current="currentAttr(t.path)"
          type="button"
          @click="select(t.path)"
        >
          {{ t.label }}
        </button>
      </nav>
    </header>

    <RouterView v-slot="{ Component }">
      <KeepAlive>
        <component :is="Component" />
      </KeepAlive>
    </RouterView>

    <footer class="market-foot">
      数据来源：新浪财经 / 腾讯财经 / 纳斯达克官方 / 沪深港交易所。行情有延迟，不构成投资建议。
    </footer>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
<style lang="scss" src="./tiles.scss"></style>
