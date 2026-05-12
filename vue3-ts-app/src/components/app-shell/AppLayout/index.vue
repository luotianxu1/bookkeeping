<script setup lang="ts">
// 通用应用布局：承载页面内容区和底部导航，不包含 Pencil 预览用手机外壳。
import type { AppSection, NavItem } from '@/types/navigation'
import BottomNavigation from '../BottomNavigation/index.vue'

defineProps<{
  activeSection: AppSection
  screenLabel: string
  navItems: NavItem[]
  showBottomNav: boolean
}>()
</script>

<template>
  <section class="app-layout" :aria-label="screenLabel">
    <div :class="['page-content', { 'without-nav': !showBottomNav }]">
      <slot />
    </div>

    <BottomNavigation v-if="showBottomNav" :active-section="activeSection" :items="navItems" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
