<script setup lang="ts">
// 个股双列榜（通用模块）：
// - a：涨跌停个股（/limits，按成交额降序）
// - hk / us：领涨 / 领跌 TOP15（/movers，带流动性门槛，按涨跌幅排序）
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getLimits,
  getMovers,
  type ActivityMarket,
  type LimitStock,
  type MoverStock,
} from '@/api/modules/market'
import { ApiError } from '@/api/request'
import { amt, pct } from '@/utils/marketFormat'
import { detailRoute } from '../routes'

const props = defineProps<{ market?: ActivityMarket }>()
const router = useRouter()

type RankStock = LimitStock | MoverStock

const mk = computed<ActivityMarket>(() => props.market ?? 'a')

/* ---------- 状态 ---------- */
const data = ref<{ up: RankStock[]; down: RankStock[]; extraNote: string } | null>(null)
const loading = ref(true)
const error = ref('')
const REFRESH_MS = 60_000 // 与后端 /activity 缓存 TTL 对齐

/* ---------- 文案 ---------- */
const heading = computed(() => (mk.value === 'a' ? '涨跌停' : '涨跌幅榜'))
const upLabel = computed(() => (mk.value === 'a' ? '涨停' : '领涨'))
const downLabel = computed(() => (mk.value === 'a' ? '跌停' : '领跌'))
const floorNote = computed(() =>
  mk.value === 'hk'
    ? '成交额 ≥ 5000万港元'
    : mk.value === 'us'
      ? '成交额 ≥ 200万美元'
      : '按成交额排序',
)

/* 币种符号（成交额读数），与市场概况脚注口径一致 */
const currency = computed(() => (mk.value === 'us' ? '$' : mk.value === 'hk' ? 'HK$' : '¥'))

/** 详情路由：A股详情接口只认 6 位代码，港/美股为对应类型 */
function open(code: string): void {
  void router.push(detailRoute(mk.value === 'a' ? 'stock' : mk.value === 'hk' ? 'hk' : 'us', code))
}

/* ---------- 加载（静默刷新：有数据时不闪骨架） ---------- */
async function load(silent = false): Promise<void> {
  if (!silent) {
    loading.value = true
    error.value = ''
  }
  try {
    if (mk.value === 'a') {
      const d = await getLimits()
      data.value = {
        up: d.up,
        down: d.down,
        extraNote: `涨停 ${d.limit_up} 家 / 跌停 ${d.limit_down} 家 · ${floorNote.value}`,
      }
    } else {
      const d = await getMovers(mk.value === 'hk' ? 'hk' : 'us')
      data.value = {
        up: d.up,
        down: d.down,
        extraNote: `${upLabel.value} / ${downLabel.value} TOP15 · ${floorNote.value}`,
      }
    }
    error.value = ''
  } catch (e) {
    if (!data.value) {
      error.value = e instanceof ApiError ? e.message || '榜单数据加载失败' : '榜单数据加载失败'
    }
  } finally {
    if (!silent) loading.value = false
  }
}

let timer: number | undefined

function onVisible(): void {
  if (!document.hidden) load(true)
}

onMounted(() => {
  load()
  timer = window.setInterval(() => {
    if (!document.hidden) load(true)
  }, REFRESH_MS)
  document.addEventListener('visibilitychange', onVisible)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisible)
})

// 切市场（hk ↔ us）时重新拉取
watch(mk, () => {
  data.value = null
  load()
})

/* ---------- 阵营统计 ---------- */
/** 全模块最大单股成交额：热度条归一化基准 */
const maxAmount = computed(() => {
  if (!data.value) return 0
  const all = [...data.value.up, ...data.value.down]
  return all.reduce((m, s) => Math.max(m, s.amount), 0)
})

const upTotal = computed(() => (data.value ? data.value.up.reduce((a, s) => a + s.amount, 0) : 0))
const downTotal = computed(() =>
  data.value ? data.value.down.reduce((a, s) => a + s.amount, 0) : 0,
)

/** 热度条宽度百分比（按全模块最大成交额归一化） */
function heatW(amount: number): string {
  if (!maxAmount.value) return '0%'
  return `${Math.max((amount / maxAmount.value) * 100, 6).toFixed(1)}%`
}

/** 入场错峰下标：长列表截断在 12 档，避免尾巴等太久 */
function stagger(i: number): number {
  return Math.min(i, 12)
}
</script>

<template>
  <section class="limits-mod" aria-label="个股榜">
    <div class="spot-head">
      <h2>{{ heading }}</h2>
      <span v-if="data" class="spot-note">{{ data.extraNote }}</span>
      <button v-if="error" class="retry" type="button" @click="load()">重试</button>
    </div>

    <p v-if="error" class="error-box" role="alert">{{ error }}</p>

    <div v-else-if="loading" class="limit-skel" aria-busy="true"></div>

    <div v-else-if="data" class="cols">
      <!-- 领涨阵营 -->
      <div class="col">
        <header class="sub-head">
          <h3 class="sub up">
            {{ upLabel }} <span class="num count">{{ data.up.length }}</span>
          </h3>
          <span class="num total" :title="`${upLabel}个股成交额合计`">{{ amt(upTotal, currency) }}</span>
        </header>
        <div v-if="data.up.length" class="chips" role="list">
          <button
            v-for="(s, i) in data.up"
            :key="s.code"
            class="chip"
            role="listitem"
            type="button"
            :style="{ '--w': heatW(s.amount), '--i': stagger(i) }"
            :title="`${mk === 'us' ? s.code + ' · ' : ''}${s.name} · 价格 ${s.price.toFixed(2)} · 成交额 ${amt(s.amount, currency)}`"
            @click="open(s.code)"
          >
            <span class="row">
              <span class="name">{{ s.name }}</span>
              <span class="num val up">{{ pct(s.pct) }}</span>
            </span>
            <span class="num chip-amt">{{ amt(s.amount, currency) }}</span>
            <i class="heat up" aria-hidden="true"></i>
          </button>
        </div>
        <p v-else class="empty">暂无</p>
      </div>

      <!-- 领跌阵营 -->
      <div class="col">
        <header class="sub-head">
          <h3 class="sub down">
            {{ downLabel }} <span class="num count">{{ data.down.length }}</span>
          </h3>
          <span class="num total" :title="`${downLabel}个股成交额合计`">{{ amt(downTotal, currency) }}</span>
        </header>
        <div v-if="data.down.length" class="chips" role="list">
          <button
            v-for="(s, i) in data.down"
            :key="s.code"
            class="chip"
            role="listitem"
            type="button"
            :style="{ '--w': heatW(s.amount), '--i': stagger(i) }"
            :title="`${mk === 'us' ? s.code + ' · ' : ''}${s.name} · 价格 ${s.price.toFixed(2)} · 成交额 ${amt(s.amount, currency)}`"
            @click="open(s.code)"
          >
            <span class="row">
              <span class="name">{{ s.name }}</span>
              <span class="num val down">{{ pct(s.pct) }}</span>
            </span>
            <span class="num chip-amt">{{ amt(s.amount, currency) }}</span>
            <i class="heat down" aria-hidden="true"></i>
          </button>
        </div>
        <p v-else class="empty">暂无</p>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.limits-mod {
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

.retry {
  margin-left: auto;
  min-height: 34px;
  padding: 0 var(--space-14);
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-brand);
  font: inherit;
  font-size: var(--font-size-11);
  font-weight: 700;
  cursor: pointer;
}

.error-box {
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
  margin: 0;
}

/* ---------- 双列：领涨 / 领跌阵营 ---------- */
.cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-12) var(--space-16);

  > .col {
    min-width: 0;
  }
}

.sub-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-8);
  margin: 0 0 var(--space-8);
  padding-bottom: var(--space-6);
  border-bottom: 1px solid var(--color-border); /* 发丝规则线，杂志感分隔 */
}

.sub {
  margin: 0;
  font-size: var(--font-size-12);
  font-weight: 600;
  letter-spacing: 0.04em;

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }

  .count {
    font-size: var(--font-size-14);
    font-weight: 800;
    font-variant-numeric: tabular-nums;
  }
}

/* 阵营成交额合计：等宽读数 */
.total {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  letter-spacing: 0.02em;
}

/* ---------- 个股 chips：名称+涨跌幅 / 成交额 / 热度条 ---------- */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
  max-height: 264px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.chip {
  /* 布局：上=名称+涨跌幅，下=成交额，底=热度条 */
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: calc(50% - var(--space-4)); /* 双列 chip 阵，密度可控 */
  min-height: 46px; /* 兼顾触控目标 */
  min-width: 0;
  padding: var(--space-6) var(--space-10) var(--space-8);
  overflow: hidden;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  font: inherit;
  font-size: var(--font-size-11);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease-out,
    transform 0.15s ease-out;

  &:hover {
    border-color: var(--color-border-soft-2);
    transform: translateY(-1px);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }

  .row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: var(--space-6);
    min-width: 0;
  }

  .name {
    overflow: hidden;
    color: var(--color-text-primary);
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .val {
    flex-shrink: 0;
    font-weight: 800;

    &.up {
      color: var(--color-danger);
    }

    &.down {
      color: var(--color-success);
    }
  }

  .chip-amt {
    color: var(--color-text-muted);
    font-size: var(--font-size-11);
  }
}

/* 记忆点：底部成交额热度条（宽度按全模块最大成交额归一化） */
.heat {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 3px;
  width: var(--w, 0%);
  border-radius: 0 2px 0 0;
  pointer-events: none;

  &.up {
    background: linear-gradient(to right, rgba(220, 38, 38, 0.35), var(--color-danger));
  }

  &.down {
    background: linear-gradient(to right, rgba(22, 163, 74, 0.35), var(--color-success));
  }
}

.empty {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

/* ---------- 入场编排：chip 错峰浮现（30ms 步进，截断 12 档） ---------- */
@media (prefers-reduced-motion: no-preference) {
  .chip {
    animation: chip-in 0.36s ease-out both;
    animation-delay: calc(var(--i, 0) * 30ms);
  }

  .sub-head {
    animation: chip-in 0.4s ease-out both;
  }

  .down-col .sub-head,
  .col:nth-child(2) .sub-head {
    animation-delay: 60ms;
  }
}

@keyframes chip-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------- 骨架 ---------- */
.limit-skel {
  height: 160px;
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

/* ---------- 响应式：移动端阵营上下分区，chip 保持双列密度 ---------- */
@media (max-width: 760px) {
  .cols {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chip,
  .sub-head,
  .limit-skel {
    animation: none;
  }
}
</style>
