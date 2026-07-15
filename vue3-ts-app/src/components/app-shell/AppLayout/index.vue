<script setup lang="ts">
// 通用应用布局：承载页面内容区和底部导航，不包含 Pencil 预览用手机外壳。
import { onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { AppSection, NavItem } from '@/types/navigation'
import BottomNavigation from '../BottomNavigation/index.vue'

defineProps<{
  activeSection: AppSection
  contentMode: 'module-home' | 'subpage' | 'auth'
  screenLabel: string
  navItems: NavItem[]
  showBottomNav: boolean
}>()

const router = useRouter()
const route = useRoute()

const EDGE_SWIPE_START_X = 48
const EDGE_SWIPE_TRIGGER_X = 80
const EDGE_SWIPE_MAX_Y = 56
const EDGE_SWIPE_RATIO = 1.6

let touchStartX = 0
let touchStartY = 0
let isTrackingEdgeSwipe = false
const touchListenerOptions = {
  passive: true,
  capture: true,
}

onMounted(() => {
  document.addEventListener('touchstart', handleTouchStart, touchListenerOptions)
  document.addEventListener('touchmove', handleTouchMove, touchListenerOptions)
  document.addEventListener('touchend', handleTouchEnd, touchListenerOptions)
  document.addEventListener('touchcancel', resetEdgeSwipe, touchListenerOptions)
})

onBeforeUnmount(() => {
  document.removeEventListener('touchstart', handleTouchStart, touchListenerOptions)
  document.removeEventListener('touchmove', handleTouchMove, touchListenerOptions)
  document.removeEventListener('touchend', handleTouchEnd, touchListenerOptions)
  document.removeEventListener('touchcancel', resetEdgeSwipe, touchListenerOptions)
})

function handleTouchStart(event: TouchEvent) {
  if (event.touches.length !== 1) {
    resetEdgeSwipe()
    return
  }

  const touch = event.touches[0]
  if (!touch || touch.clientX > EDGE_SWIPE_START_X) {
    resetEdgeSwipe()
    return
  }

  touchStartX = touch.clientX
  touchStartY = touch.clientY
  isTrackingEdgeSwipe = true
}

function handleTouchMove(event: TouchEvent) {
  if (!isTrackingEdgeSwipe || event.touches.length !== 1) {
    return
  }

  const touch = event.touches[0]
  if (!touch) {
    resetEdgeSwipe()
    return
  }

  const deltaX = touch.clientX - touchStartX
  const deltaY = touch.clientY - touchStartY

  if (deltaX < -8 || Math.abs(deltaY) > EDGE_SWIPE_MAX_Y) {
    resetEdgeSwipe()
  }
}

function handleTouchEnd(event: TouchEvent) {
  if (!isTrackingEdgeSwipe) {
    return
  }

  const touch = event.changedTouches[0]
  const deltaX = touch ? touch.clientX - touchStartX : 0
  const deltaY = touch ? touch.clientY - touchStartY : 0
  const isBackSwipe = deltaX >= EDGE_SWIPE_TRIGGER_X && deltaX / Math.max(Math.abs(deltaY), 1) >= EDGE_SWIPE_RATIO

  resetEdgeSwipe()

  if (isBackSwipe) {
    goBack()
  }
}

function resetEdgeSwipe() {
  isTrackingEdgeSwipe = false
  touchStartX = 0
  touchStartY = 0
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
    return
  }

  router.push(resolveParentRoutePath())
}

function resolveParentRoutePath() {
  const normalizedCurrentPath = normalizePath(route.path)
  const availablePaths = new Set(router.getRoutes().map((item) => normalizePath(item.path)))

  for (const candidate of buildParentCandidates(normalizedCurrentPath)) {
    if (availablePaths.has(candidate)) {
      return candidate
    }
  }

  return '/'
}

function buildParentCandidates(path: string) {
  if (path === '/') {
    return []
  }

  const segments = path.split('/').filter(Boolean)
  const candidates: string[] = []

  for (let index = segments.length - 1; index >= 0; index -= 1) {
    candidates.push(normalizePath(`/${segments.slice(0, index).join('/')}`))
  }

  if (!candidates.includes('/')) {
    candidates.push('/')
  }

  return candidates
}

function normalizePath(path: string) {
  if (!path || path === '/') {
    return '/'
  }

  return path.endsWith('/') ? path.slice(0, -1) : path
}

</script>

<template>
  <section class="app-layout" :aria-label="screenLabel">
    <div
      :class="[
        'page-content',
        `page-content--${contentMode}`,
        { 'page-content--without-nav': !showBottomNav },
      ]"
    >
      <slot />
    </div>

    <BottomNavigation v-if="showBottomNav" :active-section="activeSection" :items="navItems" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
