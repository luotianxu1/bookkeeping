<script setup lang="ts">
// 公共头部刷新按钮：统一 PageHeader 右侧刷新入口及加载状态。
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'

withDefaults(defineProps<{
  label?: string
  loading?: boolean
  disabled?: boolean
}>(), {
  label: '刷新',
  loading: false,
  disabled: false,
})
</script>

<template>
  <CommonHeaderActionButton
    :label="loading ? `${label}中` : label"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <svg
      :class="['common-header-refresh-icon', { spinning: loading }]"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path d="M20 11A8.1 8.1 0 0 0 5.5 6.1L3 9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M3 4V9H8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M4 13A8.1 8.1 0 0 0 18.5 17.9L21 15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M21 20V15H16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  </CommonHeaderActionButton>
</template>

<style scoped lang="scss" src="./style.scss"></style>
