<script setup lang="ts">
import { formatPercentageText } from '@/business/utils/percentage'

import PagePanel from '@/business/components/PagePanel.vue'

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
  <section class="desktop-workspace">
    <PagePanel title="市场涨跌分布">
      <template #actions>
        <el-button :loading="loading" text @click="load">刷新</el-button>
      </template>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="load">重试</el-button>
      </el-alert>
      <el-skeleton v-if="loading" :rows="5" animated />
      <template v-else-if="data">
        <div class="desktop-market-distribution" role="img" :aria-label="note">
          <div v-for="d in data.dist" :key="d.side + d.label">
            <span>{{ d.count }}</span>
            <div class="desktop-market-bar-track">
              <i :class="d.side" :style="{ height: barH(d.count) }" />
            </div>
            <span>{{ formatPercentageText(d.label) }}</span>
          </div>
        </div>
        <div class="desktop-distribution-totals">
          <span>
            下跌
            <strong>{{ data.down }}</strong>
          </span>
          <span>
            平盘
            <strong>{{ data.flat }}</strong>
          </span>
          <span>
            上涨
            <strong>{{ data.up }}</strong>
          </span>
        </div>
        <p v-if="turnover" class="desktop-note">
          实时成交额 {{ fmtAmount(turnover.today) }}
        </p>
      </template>
    </PagePanel>
  </section>
</template>
