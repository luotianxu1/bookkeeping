<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    closeOnOverlay?: boolean
    showClose?: boolean
    size?: 'default' | 'compact'
    scrollBody?: boolean
  }>(),
  { closeOnOverlay: false, showClose: true },
)
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; close: [] }>()
</script>
<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="size === 'compact' ? 460 : 720"
    :close-on-click-modal="closeOnOverlay"
    :show-close="showClose"
    :close-on-press-escape="showClose && closeOnOverlay"
    append-to-body
    class="pc-business-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @close="emit('close')"
  >
    <div class="pc-dialog-content"><slot /></div>
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </el-dialog>
</template>
