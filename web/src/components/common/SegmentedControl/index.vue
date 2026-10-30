<script setup lang="ts">
// 公共分段选择框：用于全部/收入/支出等同级筛选项切换。
import { computed } from 'vue'

type SegmentedOption = string | {
  icon?: 'calendar-day' | 'calendar-month' | 'calendar-year'
  label: string
  value: string
}

const props = defineProps<{
  options: readonly SegmentedOption[]
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

function hasIcon(icon: unknown): icon is 'calendar-day' | 'calendar-month' | 'calendar-year' {
  return icon === 'calendar-day' || icon === 'calendar-month' || icon === 'calendar-year'
}
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
      <span v-if="hasIcon(option.icon)" class="segmented-control-item-inner">
        <svg
          v-if="option.icon === 'calendar-day'"
          class="segmented-control-icon"
          viewBox="0 0 20 20"
          fill="none"
          aria-hidden="true"
        >
          <path d="M6.167 2.5V5M13.833 2.5V5M3.667 7.5H16.333M5.333 4.167H14.667C15.587 4.167 16.333 4.913 16.333 5.833V14.667C16.333 15.587 15.587 16.333 14.667 16.333H5.333C4.413 16.333 3.667 15.587 3.667 14.667V5.833C3.667 4.913 4.413 4.167 5.333 4.167Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M10 10.167H10.008V10.175H10V10.167Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <svg
          v-else-if="option.icon === 'calendar-month'"
          class="segmented-control-icon"
          viewBox="0 0 20 20"
          fill="none"
          aria-hidden="true"
        >
          <path d="M6.167 2.5V5M13.833 2.5V5M3.667 7.5H16.333M5.333 4.167H14.667C15.587 4.167 16.333 4.913 16.333 5.833V14.667C16.333 15.587 15.587 16.333 14.667 16.333H5.333C4.413 16.333 3.667 15.587 3.667 14.667V5.833C3.667 4.913 4.413 4.167 5.333 4.167Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M6.667 10H8.333M10.833 10H12.5M6.667 12.833H8.333M10.833 12.833H12.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <svg
          v-else
          class="segmented-control-icon"
          viewBox="0 0 20 20"
          fill="none"
          aria-hidden="true"
        >
          <path d="M6.167 2.5V5M13.833 2.5V5M3.667 7.5H16.333M5.333 4.167H14.667C15.587 4.167 16.333 4.913 16.333 5.833V14.667C16.333 15.587 15.587 16.333 14.667 16.333H5.333C4.413 16.333 3.667 15.587 3.667 14.667V5.833C3.667 4.913 4.413 4.167 5.333 4.167Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M6.667 12.5H13.333M6.667 10H13.333" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <span>{{ option.label }}</span>
      </span>
      <span v-else>{{ option.label }}</span>
    </button>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
