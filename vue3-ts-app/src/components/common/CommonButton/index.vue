<script setup lang="ts">
// 公共按钮组件：统一主次按钮风格与交互尺寸。
withDefaults(defineProps<{
  type?: 'button' | 'submit' | 'reset'
  variant?: 'primary' | 'secondary'
  size?: 'md' | 'sm'
  disabled?: boolean
}>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  disabled: false,
})
</script>

<template>
  <button
    :type="type"
    :class="['common-button', `common-button-${variant}`, `common-button-${size}`]"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped lang="scss" src="./style.scss"></style>
