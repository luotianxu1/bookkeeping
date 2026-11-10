<script setup lang="ts">
import { computed } from 'vue'
type SegmentedOption = string | { icon?: string; label: string; value: string }
const props = defineProps<{
  options: readonly SegmentedOption[]
  modelValue: string
  label: string
  variant?: 'brand' | 'surface'
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const items = computed(() =>
  props.options.map((item) =>
    typeof item === 'string' ? { label: item, value: item } : item,
  ),
)
</script>
<template>
  <el-segmented
    :model-value="modelValue"
    :options="items"
    :aria-label="label"
    @update:model-value="emit('update:modelValue', String($event))"
  />
</template>
