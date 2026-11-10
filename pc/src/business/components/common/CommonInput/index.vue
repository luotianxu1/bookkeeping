<script setup lang="ts">
import { useId } from 'vue'
const id = useId()
defineProps<{
  label: string
  modelValue: string
  placeholder?: string
  inputType?: string
  inputMode?:
    | 'none'
    | 'text'
    | 'tel'
    | 'url'
    | 'email'
    | 'numeric'
    | 'decimal'
    | 'search'
  disabled?: boolean
  readonly?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>
<template>
  <el-form-item :label="label" :for="id" class="pc-field">
    <el-date-picker
      v-if="['date', 'datetime-local', 'month'].includes(inputType || '')"
      :id="id"
      :model-value="modelValue"
      :type="
        inputType === 'datetime-local'
          ? 'datetime'
          : inputType === 'month'
            ? 'month'
            : 'date'
      "
      :value-format="
        inputType === 'datetime-local'
          ? 'YYYY-MM-DDTHH:mm'
          : inputType === 'month'
            ? 'YYYY-MM'
            : 'YYYY-MM-DD'
      "
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      @update:model-value="emit('update:modelValue', $event || '')"
    />
    <el-input
      v-else
      :id="id"
      :model-value="modelValue"
      :type="inputType || 'text'"
      :step="inputType === 'number' ? 'any' : undefined"
      :inputmode="inputMode"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      @update:model-value="emit('update:modelValue', String($event))"
    />
  </el-form-item>
</template>
