<script setup lang="ts">
// 公共头部圆形操作按钮：用于 PageHeader 右侧的图标按钮。
import { useAttrs } from 'vue'

defineOptions({
  inheritAttrs: false,
})

withDefaults(defineProps<{
  type?: 'button' | 'submit' | 'reset'
  label: string
  disabled?: boolean
}>(), {
  type: 'button',
  disabled: false,
})

const attrs = useAttrs()
</script>

<template>
  <button
    v-bind="attrs"
    class="common-header-action-button"
    :type="type"
    :disabled="disabled"
    :aria-label="label"
  >
    <slot />
  </button>
</template>

<style scoped lang="scss" src="./style.scss"></style>
