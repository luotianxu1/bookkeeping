<script setup lang="ts">
// 公共分段选择框：用于全部/收入/支出等同级筛选项切换。
import { computed } from 'vue'

type SegmentedOption = string | {
  label: string
  value: string
}

const props = defineProps<{
  options: SegmentedOption[]
  modelValue: string
  label: string
  variant?: 'brand' | 'surface'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const normalizedOptions = computed(() =>
  props.options.map((option) => (typeof option === 'string' ? { label: option, value: option } : option)),
)

const activeIndex = computed(() => {
  const index = normalizedOptions.value.findIndex((option) => option.value === props.modelValue)
  return index >= 0 ? index : 0
})
</script>

<template>
  <div
    class="segmented-control"
    :class="`segmented-control--${variant ?? 'brand'}`"
    :aria-label="label"
    :style="{ '--count': String(normalizedOptions.length), '--index': String(activeIndex) }"
  >
    <span class="segmented-control-indicator" aria-hidden="true"></span>
    <button
      v-for="option in normalizedOptions"
      :key="option.value"
      :class="['segmented-control-item', { active: option.value === modelValue }]"
      type="button"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
