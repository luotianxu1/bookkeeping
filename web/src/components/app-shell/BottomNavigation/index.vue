<script setup lang="ts">
// 应用底部主导航：根据当前业务分区高亮对应入口。
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import financeIconActive from '@/assets/navigation-icons/finance-active.png'
import financeIcon from '@/assets/navigation-icons/finance.png'
import foodIconActive from '@/assets/navigation-icons/food-active.png'
import foodIcon from '@/assets/navigation-icons/food.png'
import profileIconActive from '@/assets/navigation-icons/profile-active.png'
import profileIcon from '@/assets/navigation-icons/profile.png'
import toolsIconActive from '@/assets/navigation-icons/tools-active.png'
import toolsIcon from '@/assets/navigation-icons/tools.png'
import type { AppSection, NavItem } from '@/types/navigation'

type NavigationIconSources = {
  active: string
  default: string
}

const navigationIconSources: Record<AppSection, NavigationIconSources> = {
  finance: { active: financeIconActive, default: financeIcon },
  food: { active: foodIconActive, default: foodIcon },
  tools: { active: toolsIconActive, default: toolsIcon },
  profile: { active: profileIconActive, default: profileIcon },
}

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
      <img
        class="nav-icon"
        :src="navigationIconSources[item.section][item.section === activeSection ? 'active' : 'default']"
        width="24"
        height="24"
        alt=""
        aria-hidden="true"
      />
      <strong>{{ item.label }}</strong>
    </RouterLink>
  </nav>
</template>

<style scoped lang="scss" src="./style.scss"></style>
