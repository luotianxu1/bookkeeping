<script setup lang="ts">
import { formatPercentageText } from '@/business/utils/percentage'

defineProps<{
  items: {
    label: string
    value: string | number
    detail?: string
    tone?: string
  }[]
}>()
</script>

<template>
  <div class="desktop-metrics">
    <div v-for="item in items" :key="item.label" class="desktop-metric">
      <span>{{ item.label }}</span>
      <strong :class="item.tone ? `metric-${item.tone}` : undefined">
        {{ formatPercentageText(item.value) }}
      </strong>
      <small v-if="item.detail">{{ formatPercentageText(item.detail) }}</small>
    </div>
  </div>
</template>
