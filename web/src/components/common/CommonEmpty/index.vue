<script setup lang="ts">
// 公共空态：统一展示"暂无数据"场景，支持主行动按钮。
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  visible?: boolean
  title?: string
  description?: string
  actionText?: string
}>(), {
  visible: true,
  title: '暂无内容',
  description: '',
  actionText: '',
})

const emit = defineEmits<{
  action: []
}>()

const isOpen = computed(() => props.visible)
</script>

<template>
  <div v-if="isOpen" class="common-empty" role="status">
    <span class="common-empty-mark" aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none">
        <path d="M8 9.5 16 5l8 4.5v13L16 27l-8-4.5v-13Z" stroke="currentColor" stroke-width="2" />
        <path d="M8.5 10 16 14l7.5-4M16 14v12" stroke="currentColor" stroke-width="2" />
      </svg>
    </span>
    <p class="common-empty-title">{{ title }}</p>
    <p v-if="description" class="common-empty-description">{{ description }}</p>
    <button v-if="actionText" type="button" class="common-empty-action" @click="emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
