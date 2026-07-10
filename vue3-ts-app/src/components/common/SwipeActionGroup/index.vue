<script setup lang="ts">
// 左滑操作组：为列表项提供统一的图标化编辑/删除动作。
withDefaults(defineProps<{
  showEdit?: boolean
  deleting?: boolean
  editLabel?: string
  deleteLabel?: string
}>(), {
  showEdit: false,
  deleting: false,
  editLabel: '修改',
  deleteLabel: '删除',
})

const emit = defineEmits<{
  edit: []
  delete: []
}>()
</script>

<template>
  <div class="swipe-action-group">
    <button
      v-if="showEdit"
      class="swipe-action swipe-action-edit"
      type="button"
      :aria-label="editLabel"
      @click="emit('edit')"
    >
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M12 20H20" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        <path d="M16.5 3.5C17.328 2.672 18.672 2.672 19.5 3.5C20.328 4.328 20.328 5.672 19.5 6.5L8 18L4 20L6 16L16.5 3.5Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <span>{{ editLabel }}</span>
    </button>

    <button
      class="swipe-action swipe-action-delete"
      type="button"
      :disabled="deleting"
      :aria-label="deleteLabel"
      @click="emit('delete')"
    >
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M4 7H20" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        <path d="M10 11V17M14 11V17M6 7L7 20H17L18 7M9 7L9.8 4H14.2L15 7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <span>{{ deleting ? '删除中' : deleteLabel }}</span>
    </button>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
