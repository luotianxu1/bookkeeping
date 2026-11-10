<script setup lang="ts">
import { computed, useId } from 'vue'
export type CommonSelectOption =
  | string
  | { label: string; value: string; disabled?: boolean; icon?: string }
const props = defineProps<{
  label: string
  modelValue: string
  options: CommonSelectOption[]
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const id = useId()
const items = computed(() =>
  props.options.map((item) =>
    typeof item === 'string' ? { label: item, value: item } : item,
  ),
)
</script>
<template>
  <el-form-item :label="label" :for="id" class="pc-field">
    <el-select
      :id="id"
      :model-value="modelValue"
      :disabled="disabled"
      filterable
      :aria-label="label"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <el-option
        v-for="item in items"
        :key="item.value"
        :label="item.label"
        :value="item.value"
        :disabled="item.disabled"
      />
    </el-select>
  </el-form-item>
</template>
