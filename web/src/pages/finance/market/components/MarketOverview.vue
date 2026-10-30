<script setup lang="ts">
// 市场概况：11 档涨跌分布直方图 + 涨跌占比条 + 今日实时成交额脚注。
import { computed, onMounted, ref, watch } from 'vue'
import {
  getActivity,
  getTurnover,
  type ActivityMarket,
  type MarketActivity,
  type MarketTurnover,
} from '@/api/modules/market'
import { ApiError } from '@/api/request'

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
  const n = data.value.sample ?? data.value.up + data.value.down + data.value.flat
  return `全市场 ${n} 只个股涨跌分布`
})

function errText(e: unknown, fallback: string): string {
  if (e instanceof ApiError) return e.message || fallback
  return e instanceof Error ? e.message : fallback
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
      <button
        class="fold-btn"
        type="button"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      >
        {{ collapsed ? '展开' : '收起' }}
      </button>
    </div>

    <p v-if="error" class="error-box" role="alert">
      {{ error }}
      <button class="retry" type="button" @click="load">重试</button>
    </p>

    <div v-else-if="loading" class="ov-skel" aria-busy="true"></div>

    <template v-else-if="data">
      <!-- 11 档涨跌分布直方图 -->
      <div v-show="!collapsed" class="bars" role="img" aria-label="全市场个股涨跌幅分布直方图">
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
        今日实时成交额 <strong>{{ fmtAmount(turnover.today) }}</strong>
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

<style scoped lang="scss">
/* 与行业板块同级：无卡片容器，仅区块留白 */
.ov {
  padding-top: var(--space-4);
}

.num {
  font-variant-numeric: tabular-nums;
}

/* ---------- 头部 ---------- */
.spot-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
  margin-bottom: var(--space-10);

  h2 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: var(--font-size-14);
    font-weight: 700;
  }
}

.spot-note {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.fold-btn {
  margin-left: auto;
  min-height: 34px;
  padding: 0 var(--space-14);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  color: var(--color-text-muted);
  font: inherit;
  font-size: var(--font-size-11);
  cursor: pointer;
  transition:
    color 0.15s ease-out,
    border-color 0.15s ease-out;

  &:hover {
    color: var(--color-brand);
    border-color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

/* ---------- 分布直方图 ---------- */
.bars {
  display: flex;
  align-items: flex-end;
  gap: clamp(4px, 1.2vw, 16px);
}

.col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  min-width: 0;
}

.cnt {
  font-size: var(--font-size-11);
  font-weight: 700;
  line-height: 1;

  &.down {
    color: var(--color-success);
  }

  &.flat {
    color: var(--color-text-muted);
  }

  &.up {
    color: var(--color-danger);
  }
}

.bar-track {
  display: flex;
  align-items: flex-end; /* 柱子自下而上生长 */
  width: 100%;
  height: 150px;
}

.bar {
  width: 100%;
  max-width: 46px;
  margin: 0 auto;
  border-radius: 3px 3px 0 0;
  transform-origin: bottom;
  animation: bar-rise 0.5s ease-out both;
  animation-delay: var(--bar-delay);

  &.down {
    background: linear-gradient(to top, rgba(22, 163, 74, 0.45), var(--color-success));
  }

  &.flat {
    background: rgba(100, 116, 139, 0.35);
  }

  &.up {
    background: linear-gradient(to top, rgba(220, 38, 38, 0.45), var(--color-danger));
  }
}

.col:hover .bar {
  filter: brightness(1.15);
}

@keyframes bar-rise {
  from {
    transform: scaleY(0);
  }

  to {
    transform: scaleY(1);
  }
}

.lbl {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  line-height: 1;
  white-space: nowrap;
}

/* ---------- 涨跌占比条 ---------- */
.sum {
  display: flex;
  align-items: center;
  gap: var(--space-16);
  margin-top: var(--space-16);
}

.sum-num {
  flex-shrink: 0;
  font-size: var(--font-size-14);
  font-weight: 800;

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }
}

.ratio {
  display: flex;
  flex: 1;
  gap: var(--space-2);
  height: 8px;
  min-width: 0;
}

.seg {
  height: 100%;
  border-radius: 2px;
  flex-basis: 0;
  min-width: 3px;
  transition: flex-grow 0.6s ease-out;

  &.down {
    background: var(--color-success);
  }

  &.flat {
    background: rgba(100, 116, 139, 0.3);
  }

  &.up {
    background: var(--color-danger);
  }
}

/* ---------- 成交额脚注 ---------- */
.turnover {
  margin: var(--space-12) 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-12);

  strong {
    color: var(--color-text-primary);
    font-weight: 700;
  }
}

/* ---------- 骨架 ---------- */
.ov-skel {
  height: 220px;
  border-radius: var(--radius-10);
  background: linear-gradient(90deg, var(--color-bg-soft) 25%, var(--color-bg-card) 50%, var(--color-bg-soft) 75%);
  background-size: 200% 100%;
  animation: skeleton-flow 1.4s ease infinite;
}

@keyframes skeleton-flow {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -200% 0;
  }
}

.error-box {
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

.retry {
  min-height: 36px;
  margin-left: var(--space-10);
  padding: 0 var(--space-16);
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-brand);
  font: inherit;
  font-size: var(--font-size-12);
  font-weight: 700;
  cursor: pointer;
}

/* ---------- 响应式 ---------- */
@media (max-width: 560px) {
  .bar-track {
    height: 110px;
  }

  .lbl {
    font-size: 10px;
  }
}

/* ---------- 动效降级 ---------- */
@media (prefers-reduced-motion: reduce) {
  .bar,
  .seg {
    animation: none;
    transition: none;
  }
}
</style>
