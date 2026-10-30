<script setup lang="ts">
// 板块列表页：全部行业/概念板块，涨跌幅或成交额排序（移植自 investment Sectors 页）。
import { computed, onMounted, ref } from 'vue'
import { getSectorSpot, type SectorKind, type SectorRow } from '@/api/modules/market'
import { amt, dirClass, pct } from '@/utils/marketFormat'

/* ---------- 状态 ---------- */
const kind = ref<SectorKind>('industry')
const sectors = ref<SectorRow[]>([])
const loading = ref(true)
const error = ref('')
const sortBy = ref<'pct' | 'amount'>('pct')

const KIND_META: Record<SectorKind, { title: string; unit: string }> = {
  industry: { title: '行业板块', unit: '个行业' },
  concept: { title: '概念板块', unit: '个概念' },
}

/* 排序：涨跌幅降序（默认）/ 成交额降序 */
const sorted = computed<SectorRow[]>(() => {
  const list = [...sectors.value]
  if (sortBy.value === 'pct') {
    list.sort((a, b) => (b['涨跌幅'] ?? -9999) - (a['涨跌幅'] ?? -9999))
  } else {
    list.sort((a, b) => (b['总成交额'] ?? 0) - (a['总成交额'] ?? 0))
  }
  return list
})

/* 入场错峰：前 12 个递增延迟，之后统一 */
function delay(idx: number): string {
  return `${Math.min(idx, 12) * 40}ms`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const data = await getSectorSpot(kind.value)
    sectors.value = data.data
  } catch (e) {
    error.value = e instanceof Error ? e.message || '板块数据加载失败' : '板块数据加载失败'
  } finally {
    loading.value = false
  }
}

/** 切换行业/概念 */
function switchKind(k: SectorKind): void {
  if (k === kind.value) return
  kind.value = k
  void load()
}

onMounted(load)
</script>

<template>
  <div class="sectors-page">
    <!-- 页头 -->
    <header class="page-head">
      <h1>{{ KIND_META[kind].title }}</h1>
      <span class="page-note num">共 {{ sectors.length }} {{ KIND_META[kind].unit }}</span>
    </header>

    <!-- 分类 + 排序工具条 -->
    <div class="toolbar">
      <div class="toolbar-group" role="group" aria-label="板块分类">
        <button
          v-for="k in (['industry', 'concept'] as SectorKind[])"
          :key="k"
          class="tab-btn"
          :class="{ active: kind === k }"
          :aria-pressed="kind === k"
          type="button"
          @click="switchKind(k)"
        >
          {{ KIND_META[k].title }}
        </button>
      </div>
      <div class="toolbar-group" role="group" aria-label="排序方式">
        <button
          class="sort-btn"
          :class="{ active: sortBy === 'pct' }"
          :aria-pressed="sortBy === 'pct'"
          type="button"
          @click="sortBy = 'pct'"
        >
          按涨跌幅
        </button>
        <button
          class="sort-btn"
          :class="{ active: sortBy === 'amount' }"
          :aria-pressed="sortBy === 'amount'"
          type="button"
          @click="sortBy = 'amount'"
        >
          按成交额
        </button>
      </div>
    </div>

    <p v-if="error" class="error-box" role="alert">
      板块加载失败：{{ error }}
      <button class="retry" type="button" @click="load">重试</button>
    </p>

    <div v-else-if="loading" class="tiles" aria-busy="true">
      <div v-for="n in 18" :key="n" class="tile skeleton-tile"></div>
    </div>

    <!-- 全部板块：正方形方块阵 -->
    <div v-else-if="sorted.length" class="tiles">
      <article
        v-for="(sec, idx) in sorted"
        :key="sec['代码']"
        class="tile"
        :style="{ '--rise-delay': delay(idx) }"
      >
        <span class="rank num">{{ idx + 1 }}</span>
        <span class="tile-name">{{ sec['名称'] }}</span>
        <span class="num tile-pct" :class="dirClass(sec['涨跌幅'])">{{ pct(sec['涨跌幅']) }}</span>
        <span class="num tile-vol">{{ amt(sec['总成交额']) }}</span>
      </article>
    </div>

    <p v-else class="empty">暂无数据</p>
  </div>
</template>

<style scoped lang="scss">
.sectors-page {
  display: grid;
  gap: var(--space-12);

  > * {
    min-width: 0;
  }
}

.num {
  font-variant-numeric: tabular-nums;
}

/* ---------- 页头 ---------- */
.page-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-12);

  h1 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: var(--font-size-20);
    font-weight: 800;
    letter-spacing: -0.02em;
  }
}

.page-note {
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

/* ---------- 分类 + 排序工具条 ---------- */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  flex-wrap: wrap;
}

.toolbar-group {
  display: flex;
  gap: var(--space-8);
}

.tab-btn {
  min-height: 36px;
  padding: 0 var(--space-16);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  color: var(--color-text-muted);
  font: inherit;
  font-size: var(--font-size-13);
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.15s ease-out,
    border-color 0.15s ease-out;

  &:hover {
    color: var(--color-text-primary);
  }

  &.active {
    color: var(--color-brand);
    border-color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

.sort-btn {
  min-height: 36px;
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
    color: var(--color-text-primary);
  }

  &.active {
    color: var(--color-brand);
    border-color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

/* ---------- 正方形方块阵 ---------- */
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: var(--space-8);
}

.tile {
  position: relative;
  display: flex;
  aspect-ratio: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-8);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  text-align: center;
  transition:
    border-color 0.15s ease-out,
    transform 0.15s ease-out;

  &:hover {
    border-color: var(--color-border-soft-2);
    transform: translateY(-2px);
  }
}

@media (prefers-reduced-motion: no-preference) {
  .tile {
    animation: tile-in 0.36s ease-out both;
    animation-delay: var(--rise-delay, 0ms);
  }
}

@keyframes tile-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

.rank {
  position: absolute;
  top: var(--space-6);
  left: var(--space-8);
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  opacity: 0.7;
}

.tile-name {
  max-width: 100%;
  overflow: hidden;
  font-size: var(--font-size-12);
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.tile-pct {
  font-size: var(--font-size-16);
  font-weight: 800;
  line-height: 1.1;

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }
}

.tile-vol {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.skeleton-tile {
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

.empty {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
  text-align: center;
  padding: var(--space-20) 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 520px) {
  .tiles {
    grid-template-columns: repeat(3, 1fr);
  }

  .page-head {
    flex-wrap: wrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tile,
  .skeleton-tile {
    animation: none;
  }
}
</style>
