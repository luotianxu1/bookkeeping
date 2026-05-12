<script setup lang="ts">
// 通用金额文本：统一金额显示和正负颜色规则，便于后续扩展格式化能力。
import { computed } from 'vue'

type AmountTone = 'auto' | 'positive' | 'negative' | 'neutral' | 'inherit'

// 金额展示组件的可配置项：
// - tone: 颜色策略（auto 根据正负数自动判定）
// - showSign: 是否显示 +/- 号
// - showUnit: 是否显示货币单位（默认 ¥）
const props = withDefaults(defineProps<{
  value: string | number
  tag?: string
  tone?: AmountTone
  showSign?: boolean
  showUnit?: boolean
  unit?: string
}>(), {
  tag: 'span',
  tone: 'auto',
  showSign: false,
  showUnit: false,
  unit: '¥',
})

const text = computed(() => String(props.value ?? ''))
const trimmedText = computed(() => text.value.trim())
const numericValue = computed(() => {
  const normalized = trimmedText.value.replace(/[^\d.+-]/g, '')
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : null
})

const toneClass = computed(() => {
  if (props.tone === 'positive') return 'amount-text--positive'
  if (props.tone === 'negative') return 'amount-text--negative'
  if (props.tone === 'neutral') return 'amount-text--neutral'
  if (props.tone === 'inherit') return ''

  const normalized = trimmedText.value
  if (normalized.startsWith('+')) return 'amount-text--positive'
  if (normalized.startsWith('-')) return 'amount-text--negative'
  if (numericValue.value !== null) {
    if (numericValue.value > 0) return 'amount-text--positive'
    if (numericValue.value < 0) return 'amount-text--negative'
  }
  return 'amount-text--neutral'
})

const signText = computed(() => {
  if (!props.showSign) return ''
  if (props.tone === 'positive') return '+'
  if (props.tone === 'negative') return '-'
  if (trimmedText.value.startsWith('+')) return '+'
  if (trimmedText.value.startsWith('-')) return '-'
  return ''
})

const valueText = computed(() => {
  let normalized = trimmedText.value

  if (props.showSign && (normalized.startsWith('+') || normalized.startsWith('-'))) {
    normalized = normalized.slice(1).trimStart()
  }

  if (props.showUnit) {
    const escapedUnit = props.unit.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    normalized = normalized.replace(new RegExp(`^${escapedUnit}\\s*`), '')
  }

  return normalized
})
</script>

<template>
  <component :is="tag" :class="['amount-text', toneClass]">
    <template v-if="showSign || showUnit">
      <span v-if="showSign && signText">{{ signText }}</span>
      <span v-if="showUnit">{{ unit }}</span>
      <span>{{ valueText }}</span>
    </template>
    <template v-else>
      {{ text }}
    </template>
  </component>
</template>

<style scoped lang="scss" src="./style.scss"></style>
