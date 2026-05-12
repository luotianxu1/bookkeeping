<script setup lang="ts">
// 公共年份选择器：支持左右切换年份与中间点击选择年份。
import { computed, ref } from 'vue'

const props = defineProps<{
  modelValue: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const showOptions = ref(false)

const yearOptions = computed(() => {
  const center = props.modelValue
  return Array.from({ length: 11 }).map((_, index) => center - 5 + index)
})

function shiftYear(offset: number) {
  emit('update:modelValue', props.modelValue + offset)
}

function toggleOptions() {
  showOptions.value = !showOptions.value
}

function selectYear(year: number) {
  emit('update:modelValue', year)
  showOptions.value = false
}
</script>

<template>
  <section class="year-picker" aria-label="年份选择">
    <button type="button" class="year-picker-arrow" @click="shiftYear(-1)">‹</button>
    <button type="button" class="year-picker-label" @click="toggleOptions">{{ modelValue }}年</button>
    <button type="button" class="year-picker-arrow" @click="shiftYear(1)">›</button>

    <div v-if="showOptions" class="year-picker-panel">
      <button
        v-for="year in yearOptions"
        :key="year"
        type="button"
        :class="['year-picker-option', { active: year === modelValue }]"
        @click="selectYear(year)"
      >
        {{ year }}年
      </button>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
