<script setup lang="ts">
import DesktopInput from '@/business/components/DesktopInput.vue'

// 行情搜索：新浪 suggest 联想（代码/名称/拼音），选中跳详情。
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isCancelRequest } from '@/business/api/request'
import { useRequestAbort } from '@/business/composables/useRequestAbort'
import { searchStocks, type SearchRow } from '@/business/api/modules/market'
import { detailRoute } from '../routes'

const router = useRouter()
// 输入型联想搜索：新关键词触发即中止上一请求，防止慢响应覆盖新结果
const { signalFor } = useRequestAbort()

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
    const data = await searchStocks(kw, { signal: signalFor() })
    results.value = data.data
    searched.value = true
    open.value = true
  } catch (e) {
    // 被新请求中止：新请求已在跑，保持 searching 状态
    if (isCancelRequest(e)) return
    results.value = []
    searched.value = true
    open.value = true
  }
  searching.value = false
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
  if (wrapEl.value && !wrapEl.value.contains(e.target as Node))
    open.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  clearTimeout(timer)
})
</script>

<template>
  <div ref="wrapEl" class="search-wrap">
    <DesktopInput
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
    <el-button
      class="search-btn"
      native-type="button"
      aria-label="搜索"
      @mousedown.prevent
      @click="runSearch"
    >
      <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
        <circle
          cx="10.5"
          cy="10.5"
          r="6.5"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        />
        <line
          x1="15.5"
          y1="15.5"
          x2="21"
          y2="21"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
        />
      </svg>
    </el-button>
    <ul v-if="open" class="search-drop" role="listbox">
      <li v-if="searching" class="s-empty">搜索中…</li>
      <li v-else-if="!results.length" class="s-empty">
        无匹配结果，换个关键词试试
      </li>
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
