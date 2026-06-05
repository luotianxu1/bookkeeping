<script setup lang="ts">
// 公共顶部返回栏：用于二级页面展示返回入口和页面标题。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps<{
  title: string
  backTo?: string
  backLabel?: string
  preferBackTo?: boolean
}>()

const router = useRouter()
const route = useRoute()

const parentRoutePath = computed(() => {
  const normalizedCurrentPath = normalizePath(route.path)
  const availablePaths = new Set(router.getRoutes().map((item) => normalizePath(item.path)))

  for (const candidate of buildParentCandidates(normalizedCurrentPath)) {
    if (availablePaths.has(candidate)) {
      return candidate
    }
  }

  return props.backTo ?? '/login'
})

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }

  router.push(parentRoutePath.value)
}

function buildParentCandidates(path: string) {
  if (path === '/') {
    return []
  }

  const segments = path.split('/').filter(Boolean)
  const candidates: string[] = []

  for (let index = segments.length - 1; index >= 0; index -= 1) {
    const nextPath = `/${segments.slice(0, index).join('/')}`
    candidates.push(normalizePath(nextPath))
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
  <header class="page-header">
    <div class="page-header-main">
      <button class="page-header-back" type="button" :aria-label="backLabel ?? '返回'" @click="goBack">
        &lt;
      </button>
      <h1>{{ title }}</h1>
    </div>
    <div v-if="$slots.default" class="page-header-extra">
      <slot />
    </div>
  </header>
</template>

<style scoped lang="scss" src="./style.scss"></style>
