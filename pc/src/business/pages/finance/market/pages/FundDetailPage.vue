<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

import {
  computed,
  onActivated,
  onDeactivated,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'

import { useAsyncSection } from '@/business/composables/useAsyncSection'
import {
  getInvestmentFundBase,
  getInvestmentFundTrend,
  getInvestmentFundPeriodReturns,
  type InvestmentFundBase,
  type FundNavItem,
  type FundPeriodReturnRow,
} from '@/business/api/modules/market'
import { dirClass, pct } from '@/business/utils/marketFormat'
import FundNavChart from '../components/FundNavChart.vue'

const route = useRoute(),
  router = useRouter()
const code = computed(() => String(route.params.code ?? ''))
const valid = computed(() => /^\d{6}$/.test(code.value))
const base = ref<InvestmentFundBase | null>(null),
  navItems = ref<FundNavItem[]>([]),
  returns = ref<FundPeriodReturnRow[]>([])
const range = ref('3'),
  metric = ref<'nav' | 'accNav'>('nav')
const ranges = [
  { value: '1', label: '近一月' },
  { value: '3', label: '近三月' },
  { value: '6', label: '近半年' },
  { value: '12', label: '近一年' },
  { value: '0', label: '全部' },
]
const periodLabels: Record<string, string> = {
  Y: '近一月',
  '3Y': '近三月',
  '6Y': '近半年',
  '1N': '近一年',
  '2N': '近两年',
  '3N': '近三年',
  '5N': '近五年',
}
const number = (value: unknown): number | null =>
  value == null ||
  String(value).trim() === '' ||
  !Number.isFinite(Number(value))
    ? null
    : Number(value)
const price = (value: unknown) => number(value)?.toFixed(4) ?? '—'
const info = useAsyncSection(
  (signal) => getInvestmentFundBase(code.value, { signal, timeout: 55_000 }),
  (value) => {
    base.value = value
  },
  '基金资料加载失败',
)
const trend = useAsyncSection(
  (signal) => getInvestmentFundTrend(code.value, { signal, timeout: 45_000 }),
  (value) => {
    navItems.value = value.items
  },
  '净值走势加载失败',
)
const periods = useAsyncSection(
  (signal) =>
    getInvestmentFundPeriodReturns(code.value, { signal, timeout: 80_000 }),
  (value) => {
    returns.value = value.rows
  },
  '阶段收益加载失败',
)
const ordered = computed(() =>
  navItems.value
    .filter((p) => /^\d{4}-\d{2}-\d{2}$/.test(p.date) && number(p.nav) != null)
    .slice()
    .sort((a, b) => a.date.localeCompare(b.date)),
)
const points = computed(() => {
  const latest = ordered.value[ordered.value.length - 1]
  if (!latest || range.value === '0') return ordered.value
  const end = new Date(`${latest.date}T00:00:00Z`)
  end.setUTCMonth(end.getUTCMonth() - Number(range.value))
  const start = end.toISOString().slice(0, 10)
  return ordered.value.filter((p) => p.date >= start)
})
const recent = computed(() => ordered.value.slice(-5).reverse())
const loading = computed(
  () => info.loading.value || trend.loading.value || periods.loading.value,
)
let active = true,
  loadedCode = ''
function reset() {
  info.reset()
  trend.reset()
  periods.reset()
  base.value = null
  navItems.value = []
  returns.value = []
}
async function load() {
  loadedCode = code.value
  if (!valid.value) {
    reset()
    return
  }
  await Promise.all([info.run(), trend.run(), periods.run()])
}
watch(code, () => {
  if (!active || route.name !== 'finance-market-fund-detail') return
  reset()
  range.value = '3'
  metric.value = 'nav'
  void load()
})
onMounted(load)
onActivated(() => {
  active = true
  if (loadedCode !== code.value) {
    reset()
    void load()
  }
})
onDeactivated(() => {
  active = false
  loadedCode = ''
  info.reset()
  trend.reset()
  periods.reset()
})
function back() {
  if (
    String(router.options.history.state.back ?? '').startsWith(
      '/finance/global-market/funds',
    )
  )
    router.back()
  else void router.push('/finance/global-market/funds')
}

type ReturnRow = (typeof returns.value)[number]
type NavRow = (typeof recent.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="base?.Datas?.SHORTNAME || code">
      <template #right>
        <el-button @click="back">返回列表</el-button>
        <el-button :loading="loading" :disabled="!valid" @click="load">
          刷新
        </el-button>
      </template>
    </PageHeader>
    <el-alert v-if="!valid" title="基金代码无效" type="error" />
    <template v-else>
      <el-alert
        v-if="info.error.value"
        :title="info.error.value"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="info.run">重试</el-button>
      </el-alert>
      <template v-if="base?.matched">
        <MetricStrip
          :items="[
            { label: '单位净值', value: price(base.Datas.DWJZ) },
            { label: '累计净值', value: price(base.Datas.LJJZ) },
            { label: '净值日期', value: base.Datas.FSRQ || '—' },
          ]"
        />
      </template>
      <div class="desktop-main-aside">
        <PagePanel title="净值走势">
          <div class="fund-heading">
            <h2>净值走势</h2>
            <div class="metric-switch" role="group" aria-label="净值口径">
              <el-button
                :aria-pressed="metric === 'nav'"
                @click="metric = 'nav'"
              >
                单位
              </el-button>
              <el-button
                :aria-pressed="metric === 'accNav'"
                @click="metric = 'accNav'"
              >
                累计
              </el-button>
            </div>
          </div>
          <nav class="fund-types" aria-label="净值时间范围">
            <el-button
              v-for="item in ranges"
              :key="item.value"
              :aria-pressed="range === item.value"
              :class="{ active: range === item.value }"
              @click="range = item.value"
              class="pc-content-button"
            >
              {{ item.label }}
            </el-button>
          </nav>
          <p v-if="trend.error.value" class="fund-error" role="alert">
            {{ trend.error.value }}
            <el-button @click="trend.run">重试走势</el-button>
          </p>
          <CommonLoading v-if="trend.loading.value && !trend.ready.value" />
          <FundNavChart
            v-else-if="points.length"
            :key="code"
            :points="points"
            :metric="metric"
          />
          <p v-else-if="trend.ready.value" class="fund-empty">
            暂无可展示的净值历史
          </p>
          <p class="fund-note">
            {{
              metric === 'nav'
                ? '单位净值会受分红、拆分影响，走势不等于投资收益率。'
                : '累计净值包含历史分红，不代表分红再投资收益率。'
            }}
          </p>
        </PagePanel>
        <div class="desktop-results">
          <PagePanel title="基金资料" v-if="base?.matched">
            <dl class="desktop-record-details">
              <div>
                <dt>基金公司</dt>
                <dd>{{ base.Datas.JJGS || '—' }}</dd>
              </div>
              <div>
                <dt>申购状态</dt>
                <dd>{{ base.Datas.SGZT || '—' }}</dd>
              </div>
              <div>
                <dt>赎回状态</dt>
                <dd>{{ base.Datas.SHZT || '—' }}</dd>
              </div>
            </dl>
          </PagePanel>
          <el-alert
            v-if="periods.error.value"
            :title="periods.error.value"
            type="error"
            :closable="false"
            show-icon
          >
            <el-button link type="primary" @click="periods.run">重试</el-button>
          </el-alert>
          <DesktopTable
            :rows="returns"
            :loading="periods.loading.value"
            title="阶段收益"
          >
            <!-- @vue-generic {ReturnRow} -->
            <el-table-column label="周期" min-width="150">
              <template #default="{ row }: { row: ReturnRow }">
                {{ periodLabels[row.title] || row.title }}
              </template>
            </el-table-column>
            <!-- @vue-generic {ReturnRow} -->
            <el-table-column label="收益" min-width="150">
              <template #default="{ row }: { row: ReturnRow }">
                <span :class="dirClass(number(row.syl))">
                  {{ pct(number(row.syl)) }}
                </span>
              </template>
            </el-table-column>
          </DesktopTable>
        </div>
      </div>
      <DesktopTable :rows="recent" :loading="false" title="历史净值">
        <el-table-column
          prop="date"
          label="日期"
          min-width="180"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {NavRow} -->
        <el-table-column label="单位净值" min-width="150">
          <template #default="{ row }: { row: NavRow }">
            {{ price(row.nav) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {NavRow} -->
        <el-table-column label="累计净值" min-width="150">
          <template #default="{ row }: { row: NavRow }">
            {{ price(row.accNav) }}
          </template>
        </el-table-column>
      </DesktopTable>
    </template>
  </section>
</template>
