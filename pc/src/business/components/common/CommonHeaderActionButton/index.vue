<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'button' | 'submit' | 'reset'
    label: string
    disabled?: boolean
  }>(),
  { type: 'button' },
)
</script>
<template>
  <el-button :native-type="type" :aria-label="label" :disabled="disabled">
    <slot />
    {{ label }}
  </el-button>
</template>
