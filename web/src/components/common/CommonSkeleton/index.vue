<script setup lang="ts">
// 公共骨架屏：首屏数据加载前的占位，减少"闪白"观感。
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  visible?: boolean
  variant?: 'list' | 'card'
  rows?: number
}>(), {
  visible: true,
  variant: 'list',
  rows: 3,
})

const rowList = computed(() => Array.from({ length: Math.max(1, props.rows) }, (_, index) => index))
</script>

<template>
  <div v-if="visible" class="common-skeleton" aria-hidden="true">
    <template v-if="variant === 'card'">
      <div class="skeleton-card">
        <span class="skeleton-bar skeleton-bar--title"></span>
        <span class="skeleton-bar skeleton-bar--wide"></span>
        <span class="skeleton-bar skeleton-bar--half"></span>
      </div>
    </template>
    <template v-else>
      <div v-for="row in rowList" :key="row" class="skeleton-row">
        <span class="skeleton-avatar"></span>
        <span class="skeleton-lines">
          <span class="skeleton-bar skeleton-bar--title"></span>
          <span class="skeleton-bar skeleton-bar--half"></span>
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
