<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import { useAsyncSection } from '@/composables/useAsyncSection'
import { getInvestmentFundBase, getInvestmentFundTrend, getInvestmentFundPeriodReturns,
  type InvestmentFundBase, type FundNavItem, type FundPeriodReturnRow } from '@/api/modules/market'
import { dirClass, pct } from '@/utils/marketFormat'
import FundNavChart from '../components/FundNavChart.vue'

const route = useRoute(), router = useRouter()
const code = computed(() => String(route.params.code ?? ''))
const valid = computed(() => /^\d{6}$/.test(code.value))
const base = ref<InvestmentFundBase | null>(null), navItems = ref<FundNavItem[]>([]), returns = ref<FundPeriodReturnRow[]>([])
const range = ref('3'), metric = ref<'nav' | 'accNav'>('nav')
const ranges = [{ value: '1', label: '近一月' }, { value: '3', label: '近三月' }, { value: '6', label: '近半年' }, { value: '12', label: '近一年' }, { value: '0', label: '全部' }]
const periodLabels: Record<string, string> = { Y: '近一月', '3Y': '近三月', '6Y': '近半年', '1N': '近一年', '2N': '近两年', '3N': '近三年', '5N': '近五年' }
const number = (value: unknown): number | null => value == null || String(value).trim() === '' || !Number.isFinite(Number(value)) ? null : Number(value)
const price = (value: unknown) => number(value)?.toFixed(4) ?? '—'
const info = useAsyncSection(signal => getInvestmentFundBase(code.value, { signal, timeout: 55_000 }), value => { base.value = value }, '基金资料加载失败')
const trend = useAsyncSection(signal => getInvestmentFundTrend(code.value, { signal, timeout: 45_000 }), value => { navItems.value = value.items }, '净值走势加载失败')
const periods = useAsyncSection(signal => getInvestmentFundPeriodReturns(code.value, { signal, timeout: 80_000 }), value => { returns.value = value.rows }, '阶段收益加载失败')
const ordered = computed(() => navItems.value.filter(p => /^\d{4}-\d{2}-\d{2}$/.test(p.date) && number(p.nav) != null)
  .slice().sort((a, b) => a.date.localeCompare(b.date)))
const points = computed(() => {
  const latest = ordered.value[ordered.value.length - 1]
  if (!latest || range.value === '0') return ordered.value
  const end = new Date(`${latest.date}T00:00:00Z`)
  end.setUTCMonth(end.getUTCMonth() - Number(range.value))
  const start = end.toISOString().slice(0, 10)
  return ordered.value.filter(p => p.date >= start)
})
const recent = computed(() => ordered.value.slice(-5).reverse())
const loading = computed(() => info.loading.value || trend.loading.value || periods.loading.value)
let active = true, loadedCode = ''
function reset() { info.reset(); trend.reset(); periods.reset(); base.value = null; navItems.value = []; returns.value = [] }
async function load() {
  loadedCode = code.value
  if (!valid.value) { reset(); return }
  await Promise.all([info.run(), trend.run(), periods.run()])
}
watch(code, () => {
  if (!active || route.name !== 'finance-market-fund-detail') return
  reset(); range.value = '3'; metric.value = 'nav'; void load()
})
onMounted(load)
onActivated(() => { active = true; if (loadedCode !== code.value) { reset(); void load() } })
onDeactivated(() => { active = false; loadedCode = ''; info.reset(); trend.reset(); periods.reset() })
function back() {
  if (String(router.options.history.state.back ?? '').startsWith('/finance/global-market/funds')) router.back()
  else void router.push('/finance/global-market/funds')
}
</script>

<template>
  <section class="fund-page fund-detail" aria-label="基金详情">
    <div class="fund-heading"><button class="text-button" @click="back">‹ 返回基金列表</button><CommonHeaderRefreshButton label="刷新基金详情" :loading="loading" :disabled="!valid" @click="load" /></div>
    <p v-if="!valid" class="fund-error" role="alert">基金代码无效，请从基金列表重新选择。</p>
    <template v-else>
      <p v-if="info.error.value" class="fund-error" role="alert">{{ info.error.value }} <button @click="info.run">重试资料</button></p>
      <CommonLoading v-if="info.loading.value && !info.ready.value" />
      <section v-if="base?.matched" class="fund-panel" aria-label="基金概况">
        <div><h2>{{ base.Datas.SHORTNAME || code }}</h2><p class="fund-note">{{ code }}<span v-if="base.Datas.FTYPE"> · {{ base.Datas.FTYPE }}</span></p></div>
        <div class="nav-summary">
          <div><span>单位净值</span><strong>{{ price(base.Datas.DWJZ) }}</strong><small>{{ base.Datas.FSRQ || '暂无净值日期' }}</small></div>
          <div><span>累计净值</span><strong>{{ price(base.Datas.LJJZ) }}</strong><small>含历史分红</small></div>
        </div>
        <dl class="fund-facts">
          <div><dt>基金公司</dt><dd>{{ base.Datas.JJGS || '—' }}</dd></div>
          <div><dt>申购状态</dt><dd>{{ base.Datas.SGZT || '—' }}</dd></div>
          <div><dt>赎回状态</dt><dd>{{ base.Datas.SHZT || '—' }}</dd></div>
        </dl>
      </section>
      <p v-else-if="info.ready.value" class="fund-empty">暂未获取到 {{ code }} 的基金资料，可刷新重试。</p>
      <section class="fund-panel" aria-label="基金净值走势">
        <div class="fund-heading"><h2>净值走势</h2><div class="metric-switch" role="group" aria-label="净值口径">
          <button :aria-pressed="metric === 'nav'" @click="metric = 'nav'">单位</button><button :aria-pressed="metric === 'accNav'" @click="metric = 'accNav'">累计</button>
        </div></div>
        <nav class="fund-types" aria-label="净值时间范围"><button v-for="item in ranges" :key="item.value" :aria-pressed="range === item.value" :class="{ active: range === item.value }" @click="range = item.value">{{ item.label }}</button></nav>
        <p v-if="trend.error.value" class="fund-error" role="alert">{{ trend.error.value }} <button @click="trend.run">重试走势</button></p>
        <CommonLoading v-if="trend.loading.value && !trend.ready.value" />
        <FundNavChart v-else-if="points.length" :key="code" :points="points" :metric="metric" />
        <p v-else-if="trend.ready.value" class="fund-empty">暂无可展示的净值历史</p>
        <p class="fund-note">{{ metric === 'nav' ? '单位净值会受分红、拆分影响，走势不等于投资收益率。' : '累计净值包含历史分红，不代表分红再投资收益率。' }}</p>
      </section>
      <section class="fund-panel" aria-label="基金阶段收益">
        <h2>阶段收益</h2>
        <p v-if="periods.error.value" class="fund-error" role="alert">{{ periods.error.value }} <button @click="periods.run">重试收益</button></p>
        <CommonLoading v-if="periods.loading.value && !periods.ready.value" />
        <div v-else-if="returns.length" class="period-grid"><div v-for="p in returns" :key="p.title"><span>{{ periodLabels[p.title] || p.title }}</span><strong :class="dirClass(number(p.syl))">{{ pct(number(p.syl)) }}</strong></div></div>
        <p v-else-if="periods.ready.value" class="fund-empty">暂无阶段收益数据</p>
        <p class="fund-note">按净值历史及分红再投资口径测算；历史不足的区间显示“—”。</p>
      </section>
      <section v-if="recent.length" class="fund-panel" aria-label="近期净值">
        <h2>近期净值</h2>
        <div class="fund-columns"><span>日期</span><span>单位净值</span><span>累计净值</span></div>
        <div v-for="p in recent" :key="p.date" class="fund-columns nav-history"><span>{{ p.date }}</span><span>{{ price(p.nav) }}</span><span>{{ price(p.accNav) }}</span></div>
      </section>
    </template>
  </section>
</template>
<style scoped lang="scss" src="./fund.scss"></style>
