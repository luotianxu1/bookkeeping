<script setup lang="ts">
defineProps<{ label: string; modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>
<template>
  <el-switch
    :model-value="modelValue"
    :active-text="label"
    :aria-label="label"
    @update:model-value="emit('update:modelValue', Boolean($event))"
  />
</template>
