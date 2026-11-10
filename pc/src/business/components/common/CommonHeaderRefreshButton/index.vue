<script setup lang="ts">
import { RotateCw } from '@lucide/vue'
withDefaults(
  defineProps<{ label?: string; loading?: boolean; disabled?: boolean }>(),
  { label: '刷新' },
)
</script>
<template>
  <el-button :aria-label="label" :loading="loading" :disabled="disabled">
    <RotateCw :size="16" />
    刷新
  </el-button>
</template>
