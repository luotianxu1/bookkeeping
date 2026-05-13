<script setup lang="ts">
// 公共下拉框组件：封装标签、选项和 v-model。
export type CommonSelectOption = string | {
  label: string
  value: string
  disabled?: boolean
}

defineProps<{
  label: string
  modelValue: string
  options: CommonSelectOption[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function getOptionLabel(option: CommonSelectOption) {
  return typeof option === 'string' ? option : option.label
}

function getOptionValue(option: CommonSelectOption) {
  return typeof option === 'string' ? option : option.value
}

function isOptionDisabled(option: CommonSelectOption) {
  return typeof option === 'string' ? false : option.disabled === true
}
</script>

<template>
  <label class="common-field">
    <span>{{ label }}</span>
    <select
      class="common-field-control"
      :value="modelValue"
      :disabled="disabled"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option
        v-for="option in options"
        :key="getOptionValue(option)"
        :value="getOptionValue(option)"
        :disabled="isOptionDisabled(option)"
      >
        {{ getOptionLabel(option) }}
      </option>
    </select>
  </label>
</template>

<style scoped lang="scss" src="./style.scss"></style>
