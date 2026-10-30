<script setup lang="ts">
// 全球页六大模块行情网格（A股指数/全球指数/股指期货/汇率指数/债券期货/全球商品）。
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getGlobalModules, type GlobalModule } from '@/api/modules/market'
import { dirClass, f2 } from '@/utils/marketFormat'
import { detailRoute } from '../routes'

const router = useRouter()

/* ---------- 状态 ---------- */
const modules = ref<GlobalModule[]>([])
const loading = ref(true)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const data = await getGlobalModules()
    modules.value = data.modules
  } catch (e) {
    error.value = e instanceof Error ? e.message : '行情加载失败'
  } finally {
    loading.value = false
  }
}

void load()

/** 商品模块品种多，用 4 列，其余 3 列 */
function gridClass(m: GlobalModule): string {
  return m.key === 'commodity' ? 'cols-4' : 'cols-3'
}

/* ---------- 展示辅助 ---------- */
/** 智能价格：汇率等小数品种保留 4 位，指数/期货 2 位 */
function price(v: number | null): string {
  if (v == null) return '—'
  return Math.abs(v) < 10 ? v.toFixed(4) : f2(v)
}

/** 涨跌额带符号 */
function chg(row: Record<string, unknown>): string {
  const v = row['涨跌额'] as number | null
  if (v == null) return ''
  const s = Math.abs(v) < 10 ? v.toFixed(4) : f2(v)
  return (v > 0 ? '+' : '') + s
}

/** 涨跌幅带符号（后端已给 % 值） */
function pctS(v: number | null): string {
  if (v == null) return '—'
  return `${v > 0 ? '+' : ''}${v.toFixed(2)}%`
}

/** 模块涨跌家数统计 */
function stat(m: GlobalModule): { up: number; down: number } {
  let up = 0
  let down = 0
  for (const r of m.rows) {
    const p = r['涨跌幅']
    if (p == null) continue
    if (p > 0) up++
    else if (p < 0) down++
  }
  return { up, down }
}

/** 模块条目详情路由：A股指数走 index，其余走 module（完整新浪代码） */
function openModule(m: GlobalModule, row: GlobalModule['rows'][number]): void {
  if (m.key === 'cn_index') {
    void router.push(detailRoute('index', row['代码']))
    return
  }
  void router.push(detailRoute('module', row['源代码'] ?? row['代码']))
}
</script>

<template>
  <div>
    <p v-if="error" class="error-box" role="alert">
      行情加载失败：{{ error }}
      <button class="retry" type="button" @click="load">重试</button>
    </p>

    <div v-else-if="loading" class="loading" aria-busy="true">
      <div v-for="n in 3" :key="n" class="skel-group"></div>
    </div>

    <!-- 六模块按 01-06 顺序纵向排列 -->
    <template v-else>
      <section
        v-for="(m, mi) in modules"
        :key="m.key"
        class="module"
        :aria-label="m.title"
      >
        <header class="m-head">
          <span class="num m-idx">{{ String(mi + 1).padStart(2, '0') }}</span>
          <h2 class="m-title">{{ m.title }}</h2>
          <span class="num m-stat">
            <em class="up">{{ stat(m).up }}涨</em><i>/</i><em class="down">{{ stat(m).down }}跌</em>
          </span>
        </header>
        <div class="m-grid" :class="gridClass(m)">
          <template v-for="q in m.rows" :key="m.key + q['代码']">
            <button
              v-if="q['linkable'] !== false"
              class="cell link"
              type="button"
              :aria-label="`${q['名称']} 详情`"
              @click="openModule(m, q)"
            >
              <span class="cell-top">
                <span class="cell-name">{{ q['名称'] }}</span>
                <span class="num cell-pct" :class="dirClass(q['涨跌幅'])">{{ pctS(q['涨跌幅']) }}</span>
              </span>
              <span class="num cell-price" :class="dirClass(q['涨跌幅'])">{{ price(q['最新价']) }}</span>
              <span class="num cell-chg" :class="dirClass(q['涨跌幅'])">{{ chg(q) }}</span>
            </button>
            <article v-else class="cell">
              <span class="cell-top">
                <span class="cell-name">{{ q['名称'] }}</span>
                <span class="num cell-pct" :class="dirClass(q['涨跌幅'])">{{ pctS(q['涨跌幅']) }}</span>
              </span>
              <span class="num cell-price" :class="dirClass(q['涨跌幅'])">{{ price(q['最新价']) }}</span>
              <span class="num cell-chg" :class="dirClass(q['涨跌幅'])">{{ chg(q) }}</span>
            </article>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.num {
  font-variant-numeric: tabular-nums;
}

/* ---------- 模块信息头：编号 + 标题 + 涨跌统计 ---------- */
.module {
  min-width: 0;

  + .module {
    margin-top: var(--space-20);
  }
}

.m-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
  margin-bottom: var(--space-8);
  padding-bottom: var(--space-8);
  border-bottom: 1px solid var(--color-border);
}

.m-idx {
  color: var(--color-chart-gold);
  font-size: var(--font-size-12);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.m-title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-14);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.m-stat {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: var(--font-size-11);

  i {
    font-style: normal;
    margin: 0 var(--space-4);
    opacity: 0.5;
  }

  em {
    font-style: normal;
    font-weight: 700;

    &.up {
      color: var(--color-danger);
    }

    &.down {
      color: var(--color-success);
    }
  }
}

/* ---------- 发丝线网格：容器画上/左外框，单元格画右/下边线，全图恰好 1px 无叠线 ---------- */
.m-grid {
  display: grid;
  overflow: hidden;
  border-top: 1px solid var(--color-border);
  border-left: 1px solid var(--color-border);
  border-radius: var(--radius-8);
}

.cols-3 {
  grid-template-columns: repeat(3, 1fr);
}

.cols-4 {
  grid-template-columns: repeat(4, 1fr);
}

.cell {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--space-12) var(--space-14);
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border); /* 右/下边线构成内部网格线 */
  border-bottom: 1px solid var(--color-border);
  font: inherit;
  text-align: left;
  transition: background 0.15s ease-out;

  &.link {
    cursor: pointer;
  }

  &:hover {
    background: var(--color-bg-blue-soft);
  }

  &.link:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: -2px;
  }
}

.cell-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-8);
}

.cell-name {
  overflow: hidden;
  font-size: var(--font-size-12);
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cell-pct {
  flex-shrink: 0;
  font-size: var(--font-size-11);
  font-weight: 700;

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }
}

.cell-price {
  font-size: var(--font-size-16);
  font-weight: 800;
  line-height: 1.15;

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }
}

.cell-chg {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }
}

/* ---------- 加载骨架 ---------- */
.loading {
  display: flex;
  flex-direction: column;
  gap: var(--space-16);
}

.skel-group {
  height: 150px;
  border-radius: var(--radius-8);
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
  margin: 0;
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

/* ---------- 响应式：网格降列（移动端保持 2 列密度，不降单列） ---------- */
@media (max-width: 720px) {
  .cols-3,
  .cols-4 {
    grid-template-columns: repeat(2, 1fr);
  }

  .cell {
    padding: var(--space-10) var(--space-12);
  }
}

@media (prefers-reduced-motion: reduce) {
  .skel-group {
    animation: none;
  }
}
</style>
