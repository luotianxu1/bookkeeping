<script setup lang="ts">
// 市场概况：11 档涨跌分布直方图 + 涨跌占比条 + 今日实时成交额脚注。
import { computed, onMounted, ref, watch } from 'vue'
import {
  getActivity,
  getTurnover,
  type ActivityMarket,
  type MarketActivity,
  type MarketTurnover,
} from '@/business/api/modules/market'
import { getErrorMessage } from '@/business/api/request'

const props = defineProps<{ market?: ActivityMarket }>()

/* ---------- 状态 ---------- */
const data = ref<MarketActivity | null>(null)
const turnover = ref<MarketTurnover | null>(null)
const loading = ref(true)
const error = ref('')
const collapsed = ref(false)

/* 副标题：三个市场均为全市场口径（A股 hs_a / 港股 HKEX+新浪 / 美股纳斯达克官方） */
const note = computed(() => {
  if (!data.value) return ''
  const n =
    data.value.sample ?? data.value.up + data.value.down + data.value.flat
  return `全市场 ${n} 只个股涨跌分布`
})

function errText(e: unknown, fallback: string): string {
  return getErrorMessage(e, fallback)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const mk = props.market ?? 'a'
  try {
    data.value = await getActivity(mk)
  } catch (e) {
    error.value = errText(e, '市场概况加载失败')
  } finally {
    loading.value = false
  }
  // 成交额独立加载：失败不打断概况展示
  try {
    turnover.value = await getTurnover(mk)
  } catch {
    turnover.value = null
  }
}

onMounted(load)
watch(() => props.market, load)

/* ---------- 成交额脚注 ---------- */
const CURRENCY_SIGN: Record<string, string> = { CNY: '¥', HKD: 'HK$', USD: '$' }

/** 金额：≥1万亿 显示 X.XX万亿，否则 X亿 */
function fmtAmount(v: number): string {
  const sign = CURRENCY_SIGN[turnover.value?.currency ?? 'CNY']
  if (Math.abs(v) >= 1e12) return `${sign}${(v / 1e12).toFixed(2)}万亿`
  return `${sign}${(v / 1e8).toFixed(0)}亿`
}

const turnoverPct = computed<number | null>(() => {
  const t = turnover.value
  if (!t || t.prev === null || t.prev <= 0) return null
  return (t.today / t.prev - 1) * 100
})

/* ---------- 柱高归一化 ---------- */
const maxCount = computed(() => {
  if (!data.value) return 0
  return Math.max(...data.value.dist.map((d) => d.count), 1)
})

/** 柱高百分比（最低 2% 保证可见） */
function barH(count: number): string {
  return `${Math.max((count / maxCount.value) * 100, 2).toFixed(1)}%`
}
</script>

<template>
  <section class="ov" aria-label="市场概况">
    <div class="spot-head">
      <h2>市场概况</h2>
      <span v-if="note" class="spot-note">{{ note }}</span>
      <el-button
        class="fold-btn"
        native-type="button"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      >
        {{ collapsed ? '展开' : '收起' }}
      </el-button>
    </div>
    <p v-if="error" class="error-box" role="alert">
      {{ error }}
      <el-button class="retry" native-type="button" @click="load">
        重试
      </el-button>
    </p>
    <div v-else-if="loading" class="ov-skel" aria-busy="true"></div>
    <template v-else-if="data">
      <!-- 11 档涨跌分布直方图 -->
      <div
        v-show="!collapsed"
        class="bars"
        role="img"
        aria-label="全市场个股涨跌幅分布直方图"
      >
        <div v-for="(d, i) in data.dist" :key="d.side + d.label" class="col">
          <span class="num cnt" :class="d.side">{{ d.count }}</span>
          <div class="bar-track">
            <div
              class="bar"
              :class="d.side"
              :style="{ height: barH(d.count), '--bar-delay': `${i * 45}ms` }"
              :title="`${d.side === 'down' ? '跌' : d.side === 'up' ? '涨' : ''}${d.label}：${d.count} 家`"
            ></div>
          </div>
          <span class="num lbl">{{ d.label }}</span>
        </div>
      </div>
      <!-- 涨跌占比条 -->
      <div class="sum">
        <span class="num sum-num down">跌 {{ data.down }}</span>
        <div
          class="ratio"
          role="img"
          :aria-label="`上涨 ${data.up} 家，下跌 ${data.down} 家，平盘 ${data.flat} 家`"
        >
          <i class="seg down" :style="{ flexGrow: data.down }"></i>
          <i class="seg flat" :style="{ flexGrow: data.flat }"></i>
          <i class="seg up" :style="{ flexGrow: data.up }"></i>
        </div>
        <span class="num sum-num up">涨 {{ data.up }}</span>
      </div>
      <!-- 今日实时成交额 + 较上一日此时 -->
      <p v-if="turnover" class="turnover num">
        今日实时成交额
        <strong>{{ fmtAmount(turnover.today) }}</strong>
        <span class="vs">
          · 较上一日此时
          <template v-if="turnoverPct !== null">
            {{ turnoverPct > 0 ? '+' : '' }}{{ turnoverPct.toFixed(1) }}%
          </template>
          <template v-else>—</template>
        </span>
      </p>
    </template>
  </section>
</template>
