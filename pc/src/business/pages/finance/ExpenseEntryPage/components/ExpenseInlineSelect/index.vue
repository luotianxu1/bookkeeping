<script setup lang="ts">
import { useId } from 'vue'
export type ExpenseInlineSelectValue = string | number | null
export type ExpenseInlineSelectOption = {
  label: string
  value: string | number
  disabled?: boolean
  description?: string
}
defineProps<{
  label: string
  modelValue: ExpenseInlineSelectValue
  options: ExpenseInlineSelectOption[]
  disabled?: boolean
  placeholder?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [value: ExpenseInlineSelectValue]
}>()
const id = useId()
</script>
<template>
  <el-form-item :label="label" :for="id" class="pc-field">
    <el-select
      :id="id"
      :model-value="modelValue ?? undefined"
      :disabled="disabled"
      :placeholder="placeholder || '请选择'"
      filterable
      @update:model-value="emit('update:modelValue', $event)"
    >
      <el-option
        v-for="option in options"
        :key="option.value"
        :label="option.label"
        :value="option.value"
        :disabled="option.disabled"
      >
        <span>{{ option.label }}</span>
        <small v-if="option.description" class="muted">
          · {{ option.description }}
        </small>
      </el-option>
    </el-select>
  </el-form-item>
</template>
