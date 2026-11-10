<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'
import { formatPercentageInput } from '@/business/utils/percentage'
const id = useId()
const props = defineProps<{
  label: string
  modelValue: string
  placeholder?: string
  inputType?: string
  percentage?: boolean
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
const focused = ref(false)
const displayValue = computed(() =>
  props.percentage && !focused.value
    ? formatPercentageInput(props.modelValue)
    : props.modelValue,
)
function focus(event: FocusEvent) {
  focused.value = true
  // 恢复完整精度后选中数值，避免格式切换导致替换输入变为追加。
  if (props.percentage)
    nextTick(() => (event.target as HTMLInputElement).select())
}
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
      :model-value="displayValue"
      @focus="focus"
      @blur="focused = false"
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
