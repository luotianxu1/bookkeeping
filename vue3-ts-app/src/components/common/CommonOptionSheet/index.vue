<script setup lang="ts">
// 公共单选抽屉：用于筛选项、排序项等列表选择。
import CommonBottomSheet from '@/components/common/CommonBottomSheet/index.vue'

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

function selectOption(value: string) {
  emit('select', value)
  emit('update:modelValue', false)
}
</script>

<template>
  <CommonBottomSheet
    :model-value="modelValue"
    :title="title"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="common-option-sheet-list">
      <button
        v-for="option in options"
        :key="option.value"
        :class="['common-option-sheet-item', { active: option.value === selectedValue }]"
        type="button"
        @click="selectOption(option.value)"
      >
        <span class="common-option-sheet-copy">
          <strong>{{ option.label }}</strong>
          <span v-if="option.description">{{ option.description }}</span>
        </span>
        <span class="common-option-sheet-check" aria-hidden="true"></span>
      </button>
    </div>
  </CommonBottomSheet>
</template>

<style scoped lang="scss" src="./style.scss"></style>
