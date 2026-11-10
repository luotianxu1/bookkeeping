<script setup lang="ts">
import CommonModal from '../CommonModal/index.vue'
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    closeOnOverlay?: boolean
    showClose?: boolean
    compact?: boolean
  }>(),
  { closeOnOverlay: false, showClose: true },
)
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; close: [] }>()
</script>
<template>
  <CommonModal
    :model-value="modelValue"
    :title="title"
    :size="compact ? 'compact' : 'default'"
    :close-on-overlay="closeOnOverlay"
    :show-close="showClose"
    @update:model-value="emit('update:modelValue', $event)"
    @close="emit('close')"
  >
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </CommonModal>
</template>
