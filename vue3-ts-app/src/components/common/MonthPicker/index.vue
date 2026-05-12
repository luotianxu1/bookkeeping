<script setup lang="ts">
// 公共月份选择器：支持左右切换月份与点击中间选择月份。
import { computed, ref } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const monthInputRef = ref<HTMLInputElement | null>(null)

// 标准化月份字符串：保证为 YYYY-MM 格式。
function normalizeMonth(value: string) {
  const matched = /^(\d{4})-(\d{1,2})$/.exec(value)
  if (!matched) return '2026-03'
  const year = matched[1]
  const month = matched[2].padStart(2, '0')
  return `${year}-${month}`
}

function parseMonth(value: string) {
  const normalized = normalizeMonth(value)
  const [yearText, monthText] = normalized.split('-')
  return {
    year: Number(yearText),
    month: Number(monthText),
  }
}

function formatMonth(year: number, month: number) {
  const normalizedMonth = String(month).padStart(2, '0')
  return `${year}-${normalizedMonth}`
}

const displayText = computed(() => {
  const { year, month } = parseMonth(props.modelValue)
  return `${year}年${String(month).padStart(2, '0')}月`
})

function shiftMonth(offset: number) {
  const { year, month } = parseMonth(props.modelValue)
  const date = new Date(year, month - 1 + offset, 1)
  emit('update:modelValue', formatMonth(date.getFullYear(), date.getMonth() + 1))
}

function openMonthPicker() {
  monthInputRef.value?.showPicker?.()
  monthInputRef.value?.click()
}

function handleInputChange(event: Event) {
  const value = (event.target as HTMLInputElement).value
  if (!value) return
  emit('update:modelValue', normalizeMonth(value))
}
</script>

<template>
  <section class="month-picker" aria-label="月份选择">
    <button type="button" class="month-picker-arrow" @click="shiftMonth(-1)">‹</button>
    <button type="button" class="month-picker-label" @click="openMonthPicker">{{ displayText }}</button>
    <button type="button" class="month-picker-arrow" @click="shiftMonth(1)">›</button>
    <input
      ref="monthInputRef"
      class="month-picker-native"
      type="month"
      :value="normalizeMonth(modelValue)"
      @change="handleInputChange"
    />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
