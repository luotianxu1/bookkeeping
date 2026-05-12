<script setup lang="ts">
// 应用底部主导航：根据当前业务分区高亮对应入口。
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { AppSection, NavItem } from '@/types/navigation'

const props = defineProps<{
  activeSection: AppSection
  items: NavItem[]
}>()

const activeIndex = computed<number>(() => {
  const index = props.items.findIndex((item) => item.section === props.activeSection)
  return index >= 0 ? index : 0
})
</script>

<template>
  <nav class="bottom-nav" aria-label="主导航">
    <span
      class="nav-active-indicator"
      :style="{ transform: `translateX(calc(${activeIndex} * 100%))` }"
      aria-hidden="true"
    />
    <RouterLink
      v-for="item in items"
      :key="item.section"
      :class="['nav-item', { active: item.section === activeSection }]"
      :to="item.path"
    >
      <span>{{ item.icon }}</span>
      <strong>{{ item.label }}</strong>
    </RouterLink>
  </nav>
</template>

<style scoped lang="scss" src="./style.scss"></style>
