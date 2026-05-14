<script setup lang="ts">
// 公共接口反馈提示：统一展示接口成功和失败结果。
import { computed, onBeforeUnmount, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  message: string
  type?: 'success' | 'error'
  duration?: number
}>(), {
  type: 'success',
  duration: 1800,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

let timer: number | undefined

const isOpen = computed(() => props.modelValue && props.message.length > 0)

watch(
  () => [props.modelValue, props.message, props.duration] as const,
  ([visible]) => {
    window.clearTimeout(timer)
    if (visible && props.duration > 0) {
      timer = window.setTimeout(() => {
        emit('update:modelValue', false)
      }, props.duration)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.clearTimeout(timer)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="common-feedback">
      <div
        v-if="isOpen"
        :class="['common-feedback', `common-feedback-${type}`]"
        role="status"
        aria-live="polite"
      >
        {{ message }}
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss" src="./style.scss"></style>
