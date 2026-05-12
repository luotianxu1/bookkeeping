<script setup lang="ts">
// 应用装配入口：负责把当前页面放入统一的应用布局中。
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { mainNavItems } from '@/data/navigation'
import AppLayout from '@/components/app-shell/AppLayout/index.vue'
import type { AppSection } from '@/types/navigation'

const route = useRoute()
const activeSection = computed<AppSection>(() => route.meta.section ?? 'finance')
const screenLabel = computed<string>(() => route.meta.title ?? '财务首页')
const tabRootPaths = new Set(['/finance', '/food', '/tools', '/profile'])
const showBottomNav = computed<boolean>(() => tabRootPaths.has(route.path))
</script>

<template>
  <main class="stage">
    <AppLayout
      :active-section="activeSection"
      :screen-label="screenLabel"
      :nav-items="mainNavItems"
      :show-bottom-nav="showBottomNav"
    >
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade">
          <component :is="Component" v-if="Component" />
        </Transition>
      </RouterView>
    </AppLayout>
  </main>
</template>

<style scoped lang="scss" src="./style.scss"></style>
