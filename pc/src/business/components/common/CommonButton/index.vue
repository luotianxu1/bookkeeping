<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'button' | 'submit' | 'reset'
    variant?: 'primary' | 'secondary'
    size?: 'md' | 'sm'
    disabled?: boolean
  }>(),
  { type: 'button', variant: 'primary' },
)
</script>
<template>
  <el-button
    :native-type="type"
    :type="variant === 'primary' ? 'primary' : undefined"
    :size="size === 'sm' ? 'small' : 'default'"
    :disabled="disabled"
  >
    <slot />
  </el-button>
</template>
