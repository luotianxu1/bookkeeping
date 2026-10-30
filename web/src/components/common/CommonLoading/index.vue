<script setup lang="ts">
// 公共加载态：统一展示带遮罩层的居中 loading 弹层。
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  visible?: boolean
  text?: string
}>(), {
  visible: true,
  text: '加载中...',
})

const isOpen = computed(() => props.visible)
</script>

<template>
  <Teleport to="body">
    <Transition name="common-loading">
      <div
        v-if="isOpen"
        class="common-loading-root"
        role="status"
        aria-live="polite"
        aria-busy="true"
      >
        <div class="common-loading-mask"></div>
        <div class="common-loading-panel">
          <span class="common-loading-spinner" aria-hidden="true"></span>
          <span class="common-loading-text">{{ text }}</span>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss" src="./style.scss"></style>
