<script setup lang="ts">
// 公共分段选择框：用于全部/收入/支出等同级筛选项切换。
import { computed } from 'vue'

const props = defineProps<{
  options: string[]
  modelValue: string
  label: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const activeIndex = computed(() => {
  const index = props.options.indexOf(props.modelValue)
  return index >= 0 ? index : 0
})
</script>

<template>
  <div
    class="segmented-control"
    :aria-label="label"
    :style="{ '--count': String(options.length), '--index': String(activeIndex) }"
  >
    <span class="segmented-control-indicator" aria-hidden="true"></span>
    <button
      v-for="option in options"
      :key="option"
      :class="['segmented-control-item', { active: option === modelValue }]"
      type="button"
      @click="emit('update:modelValue', option)"
    >
      {{ option }}
    </button>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
