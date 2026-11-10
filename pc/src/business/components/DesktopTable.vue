<script setup lang="ts" generic="T extends object">
import { computed, ref, watch } from 'vue'
import { Search } from '@lucide/vue'
const props = withDefaults(
  defineProps<{
    rows: T[]
    loading?: boolean
    title?: string
    searchable?: boolean
    searchPlaceholder?: string
    searchKeys?: string[]
    paginate?: boolean
  }>(),
  {
    loading: false,
    paginate: true,
    searchPlaceholder: '搜索列表',
  },
)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const sort = ref<{ prop: string; order: 'ascending' | 'descending' | null }>({
  prop: '',
  order: null,
})
function field(value: unknown, path: string): unknown {
  return path
    .split('.')
    .reduce<unknown>(
      (item, key) =>
        item && typeof item === 'object'
          ? (item as Record<string, unknown>)[key]
          : undefined,
      value,
    )
}
function searchableText(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'object')
    return Object.values(value).map(searchableText).join(' ')
  return String(value)
}
const filtered = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  if (!query || !props.searchable) return props.rows
  return props.rows.filter((row) => {
    const values = props.searchKeys?.length
      ? props.searchKeys.map((key) => field(row, key))
      : row
    return searchableText(values).toLocaleLowerCase().includes(query)
  })
})
const sorted = computed(() => {
  if (!sort.value.prop || !sort.value.order) return filtered.value
  const direction = sort.value.order === 'ascending' ? 1 : -1
  return [...filtered.value].sort((left, right) => {
    const a = field(left, sort.value.prop),
      b = field(right, sort.value.prop)
    if (a == null) return b == null ? 0 : 1
    if (b == null) return -1
    return (
      direction *
      (typeof a === 'number' && typeof b === 'number'
        ? a - b
        : String(a).localeCompare(String(b), 'zh-CN', { numeric: true }))
    )
  })
})
const paged = computed(() =>
  props.paginate
    ? sorted.value.slice((page.value - 1) * size.value, page.value * size.value)
    : sorted.value,
)
function changeSort(value: {
  prop: string | null
  order: 'ascending' | 'descending' | null
}) {
  sort.value = { prop: value.prop ?? '', order: value.order }
  page.value = 1
}
watch(
  () => props.rows,
  () => {
    page.value = 1
  },
)
watch([size, keyword], () => {
  page.value = 1
})
</script>
<template>
  <section
    class="pc-table-panel desktop-data-panel"
    :aria-busy="loading"
    :aria-label="title"
  >
    <header
      v-if="title || $slots.toolbar || searchable"
      class="desktop-data-toolbar"
    >
      <div class="desktop-data-heading">
        <h2 v-if="title">{{ title }}</h2>
        <slot name="toolbar" />
      </div>
      <el-input
        v-if="searchable"
        v-model="keyword"
        :placeholder="searchPlaceholder"
        :aria-label="searchPlaceholder"
        clearable
        class="desktop-table-search"
      >
        <template #prefix><Search :size="15" /></template>
      </el-input>
    </header>
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-table
      v-else
      :data="paged"
      stripe
      highlight-current-row
      empty-text="暂无数据"
      style="width: 100%"
      @sort-change="changeSort"
    >
      <slot />
    </el-table>
    <footer v-if="paginate && !loading" class="desktop-table-footer">
      <span>
        共 {{ filtered.length }} 条{{
          keyword ? ` / 全部 ${rows.length} 条` : ''
        }}
      </span>
      <el-pagination
        v-if="filtered.length > 20"
        v-model:current-page="page"
        v-model:page-size="size"
        :total="filtered.length"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
      />
    </footer>
  </section>
</template>
