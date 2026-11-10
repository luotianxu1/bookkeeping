<script setup lang="ts">
import CommonModal from '../CommonModal/index.vue'
export type CommonOptionSheetOption = {
  label: string
  value: string
  description?: string
}
defineProps<{
  modelValue: boolean
  title: string
  selectedValue: string
  options: CommonOptionSheetOption[]
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [value: string]
}>()
function select(value: string | number | boolean | undefined) {
  if (value === undefined) return
  emit('select', String(value))
  emit('update:modelValue', false)
}
</script>
<template>
  <CommonModal
    :model-value="modelValue"
    :title="title"
    size="compact"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-radio-group
      :model-value="selectedValue"
      class="pc-option-list"
      @change="select"
    >
      <el-radio
        v-for="item in options"
        :key="item.value"
        :value="item.value"
        border
      >
        {{ item.label }}
        <small v-if="item.description">{{ item.description }}</small>
      </el-radio>
    </el-radio-group>
  </CommonModal>
</template>
