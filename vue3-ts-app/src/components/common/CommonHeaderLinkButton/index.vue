<script setup lang="ts">
// 公共顶部图标导航按钮：用于 PageHeader 右侧跳转到同级明细或辅助页面。
defineProps<{
  to: string
  label: string
}>()
</script>

<template>
  <RouterLink class="common-header-link-button" :to="to" :aria-label="label">
    <slot>{{ label }}</slot>
  </RouterLink>
</template>

<style scoped lang="scss" src="./style.scss"></style>
