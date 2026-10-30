<script setup lang="ts">
// 行情搜索：新浪 suggest 联想（代码/名称/拼音），选中跳详情。
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchStocks, type SearchRow } from '@/api/modules/market'
import { detailRoute } from '../routes'

const router = useRouter()

const wrapEl = ref<HTMLElement | null>(null)
const keyword = ref('')
const results = ref<SearchRow[]>([])
const open = ref(false)
const searching = ref(false)
const searched = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

async function runSearch(): Promise<void> {
  const kw = keyword.value.trim()
  if (!kw) {
    results.value = []
    searched.value = false
    open.value = false
    return
  }
  searching.value = true
  try {
    const data = await searchStocks(kw)
    results.value = data.data
    searched.value = true
    open.value = true
  } catch {
    results.value = []
    searched.value = true
    open.value = true
  } finally {
    searching.value = false
  }
}

function onInput(): void {
  clearTimeout(timer)
  timer = setTimeout(runSearch, 300)
}

function onFocus(): void {
  // 已有关键词时聚焦立即搜索/展示上次结果
  if (keyword.value.trim()) void runSearch()
}

function pick(r: SearchRow): void {
  open.value = false
  keyword.value = ''
  results.value = []
  searched.value = false
  void router.push(detailRoute(r.类型, r.代码))
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    open.value = false
  } else if (e.key === 'Enter' && results.value.length) {
    pick(results.value[0])
  }
}

function onDocClick(e: MouseEvent): void {
  if (wrapEl.value && !wrapEl.value.contains(e.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  clearTimeout(timer)
})
</script>

<template>
  <div ref="wrapEl" class="search-wrap">
    <input
      v-model="keyword"
      class="search-input"
      type="search"
      placeholder="搜索股票 / 指数 / 港股 / 美股…"
      aria-label="搜索股票"
      autocomplete="off"
      @input="onInput"
      @keydown="onKeydown"
      @focus="onFocus"
    />
    <button
      class="search-btn"
      type="button"
      aria-label="搜索"
      @mousedown.prevent
      @click="runSearch"
    >
      <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
        <circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" stroke-width="2" />
        <line x1="15.5" y1="15.5" x2="21" y2="21" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </svg>
    </button>
    <ul v-if="open" class="search-drop" role="listbox">
      <li v-if="searching" class="s-empty">搜索中…</li>
      <li v-else-if="!results.length" class="s-empty">无匹配结果，换个关键词试试</li>
      <li
        v-for="r in results"
        :key="r.类型 + r.代码"
        role="option"
        :aria-selected="false"
        @mousedown.prevent="pick(r)"
      >
        <span class="s-name">{{ r.名称 }}</span>
        <span class="s-meta num">{{ r.代码 }}</span>
        <span class="s-market">{{ r.市场 }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.search-wrap {
  position: relative;
}

.search-input {
  width: 100%;
  padding: 9px 40px 9px var(--space-14);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  color: var(--color-text-primary);
  font: inherit;
  font-size: var(--font-size-13);
  outline: none;
  transition: border-color 0.15s ease-out;

  &::placeholder {
    color: var(--color-text-muted);
  }

  &:focus {
    border-color: var(--color-brand);
  }
}

.search-btn {
  position: absolute;
  right: 5px;
  top: 50%;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: none;
  border-radius: 50%;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color 0.15s ease-out;

  &:hover {
    color: var(--color-brand);
  }
}

.search-drop {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 60;
  max-height: 320px;
  margin: 0;
  padding: var(--space-4);
  overflow-y: auto;
  list-style: none;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  box-shadow: 0 10px 24px rgba(183, 200, 244, 0.28);

  li {
    display: flex;
    align-items: center;
    gap: var(--space-10);
    padding: var(--space-8) var(--space-10);
    border-radius: var(--radius-8);
    font-size: var(--font-size-13);
    cursor: pointer;
  }

  li:not(.s-empty):hover {
    background: var(--color-bg-brand-soft);
  }
}

.s-empty {
  color: var(--color-text-muted);
  text-align: center;
  cursor: default;
}

.s-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.s-meta {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  font-variant-numeric: tabular-nums;
}

.s-market {
  flex: 0 0 auto;
  padding: 1px var(--space-8);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  white-space: nowrap;
}

.num {
  font-variant-numeric: tabular-nums;
}
</style>
