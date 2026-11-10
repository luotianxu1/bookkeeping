<script setup lang="ts">
defineProps<{ modelValue: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()
</script>
<template>
  <el-date-picker
    :model-value="String(modelValue)"
    type="year"
    value-format="YYYY"
    format="YYYY年"
    :clearable="false"
    aria-label="选择年份"
    @update:model-value="emit('update:modelValue', Number($event))"
  />
</template>
