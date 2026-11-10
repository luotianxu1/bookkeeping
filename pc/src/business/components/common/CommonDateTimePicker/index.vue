<script setup lang="ts">
import { useId } from 'vue'
const id = useId()
withDefaults(
  defineProps<{ modelValue: string; label?: string; disabled?: boolean }>(),
  { label: '选择日期和时间' },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>
<template>
  <el-form-item :label="label" :for="id" class="pc-field">
    <el-date-picker
      :id="id"
      :model-value="modelValue"
      type="datetime"
      value-format="YYYY-MM-DDTHH:mm"
      :aria-label="label"
      :placeholder="label"
      :disabled="disabled"
      :clearable="false"
      @update:model-value="emit('update:modelValue', $event || '')"
    />
  </el-form-item>
</template>
