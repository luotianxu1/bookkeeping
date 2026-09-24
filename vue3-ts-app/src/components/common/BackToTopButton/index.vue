<script setup lang="ts">
// 公共返回顶部按钮：优先监听页面所在的应用滚动容器，未找到时回退到窗口滚动。
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{
  threshold?: number
}>(), {
  threshold: 320,
})

const componentRoot = ref<HTMLElement | null>(null)
const isVisible = ref(false)
let scrollContainer: HTMLElement | null = null

onMounted(() => {
  scrollContainer = componentRoot.value?.closest<HTMLElement>('.page-content') ?? null
  const eventTarget: HTMLElement | Window = scrollContainer ?? window
  eventTarget.addEventListener('scroll', updateVisibility, { passive: true })
  updateVisibility()
})

onBeforeUnmount(() => {
  const eventTarget: HTMLElement | Window = scrollContainer ?? window
  eventTarget.removeEventListener('scroll', updateVisibility)
})

function updateVisibility() {
  const scrollTop = scrollContainer?.scrollTop ?? window.scrollY
  isVisible.value = scrollTop >= props.threshold
}

function scrollToTop() {
  if (scrollContainer) {
    scrollContainer.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }

  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div ref="componentRoot" class="back-to-top-button-container">
    <Transition name="back-to-top">
      <button
        v-if="isVisible"
        class="back-to-top-button"
        type="button"
        aria-label="返回顶部"
        @click="scrollToTop"
      >
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 19V5M6 11L12 5L18 11" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </Transition>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
