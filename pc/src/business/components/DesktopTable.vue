<script setup lang="ts" generic="T extends object">
import { computed, ref, watch } from 'vue'
const props = withDefaults(defineProps<{ rows: T[]; loading?: boolean }>(), {
  loading: false,
})
const page = ref(1)
const size = ref(20)
const paged = computed(() =>
  props.rows.slice((page.value - 1) * size.value, page.value * size.value),
)
watch(
  () => props.rows,
  () => {
    page.value = 1
  },
)
watch(size, () => {
  page.value = 1
})
</script>
<template>
  <div class="pc-table-panel" :aria-busy="loading">
    <div v-if="$slots.toolbar" class="pc-toolbar"><slot name="toolbar" /></div>
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-table
      v-else
      :data="paged"
      stripe
      empty-text="暂无数据"
      style="width: 100%"
    >
      <slot />
    </el-table>
    <div v-if="rows.length > 20" class="pc-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="rows.length"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
      />
    </div>
  </div>
</template>
