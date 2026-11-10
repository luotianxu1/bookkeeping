<script setup lang="ts">
withDefaults(
  defineProps<{
    visible?: boolean
    variant?: 'list' | 'card'
    rows?: number
  }>(),
  { visible: true, rows: 3 },
)
</script>
<template>
  <el-skeleton v-if="visible" :rows="rows" animated class="pc-loading" />
</template>
