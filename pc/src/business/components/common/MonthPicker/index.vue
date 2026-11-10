<script setup lang="ts">
defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>
<template>
  <el-date-picker
    :model-value="modelValue"
    type="month"
    value-format="YYYY-MM"
    format="YYYY年MM月"
    :clearable="false"
    aria-label="选择月份"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
