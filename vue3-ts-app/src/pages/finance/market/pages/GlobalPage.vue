<script setup lang="ts">
// 全球 tab：六大模块行情（移植自 investment GlobalPage，数据来自 ModuleGrid 组件）。
import ModuleGrid from '../components/ModuleGrid.vue'
</script>

<template>
  <div class="global-page">
    <ModuleGrid />
  </div>
</template>

<style scoped lang="scss">
.global-page {
  display: grid;
  gap: var(--space-16);

  > * {
    min-width: 0;
  }
}
</style>
