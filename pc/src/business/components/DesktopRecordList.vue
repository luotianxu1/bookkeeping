<script setup lang="ts" generic="T extends object">
// 卡片列表面板：DesktopTable 的卡片化替代品。
// 保留搜索 / 排序 / 分页能力，内容结构交给页面的默认插槽决定。
import { computed, ref, watch } from 'vue'
import { Inbox, Search } from '@lucide/vue'

const props = withDefaults(
  defineProps<{
    rows: T[]
    loading?: boolean
    title?: string
    searchable?: boolean
    searchPlaceholder?: string
    searchKeys?: string[]
    paginate?: boolean
    rowKey?: string
    pageSize?: number
    emptyTitle?: string
    emptyHint?: string
  }>(),
  {
    loading: false,
    paginate: true,
    searchPlaceholder: '搜索列表',
    pageSize: 24,
    emptyTitle: '暂无数据',
    emptyHint: '内容会在这里出现',
  },
)

const keyword = ref('')
const page = ref(1)
const size = ref(props.pageSize)

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

const paged = computed(() =>
  props.paginate
    ? filtered.value.slice((page.value - 1) * size.value, page.value * size.value)
    : filtered.value,
)

function rowKeyOf(row: T, index: number): string | number {
  if (!props.rowKey) return index
  const key = field(row, props.rowKey)
  return key == null ? index : String(key)
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
    class="pc-table-panel desktop-data-panel record-list-panel"
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
    <div v-else class="record-list-body">
      <p v-if="!paged.length" class="record-list-empty">
        <Inbox :size="36" aria-hidden="true" />
        <strong>{{ emptyTitle }}</strong>
        <span>{{ emptyHint }}</span>
        <slot name="empty-action" />
      </p>
      <div v-else class="record-list-grid">
        <slot
          v-for="(row, index) in paged"
          :key="rowKeyOf(row, index)"
          :row="row"
          :index="index"
        />
      </div>
    </div>
    <footer
      v-if="paginate && !loading && filtered.length"
      class="desktop-table-footer"
    >
      <span>
        共 {{ filtered.length }} 条{{
          keyword ? ` / 全部 ${rows.length} 条` : ''
        }}
      </span>
      <el-pagination
        v-if="filtered.length > size"
        v-model:current-page="page"
        v-model:page-size="size"
        :total="filtered.length"
        :page-sizes="[pageSize, 48, 96]"
        layout="total, sizes, prev, pager, next"
      />
    </footer>
  </section>
</template>
