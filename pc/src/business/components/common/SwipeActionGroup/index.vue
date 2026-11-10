<script setup lang="ts">
withDefaults(
  defineProps<{
    showEdit?: boolean
    deleting?: boolean
    editLabel?: string
    deleteLabel?: string
  }>(),
  { showEdit: true, editLabel: '编辑', deleteLabel: '删除' },
)
const emit = defineEmits<{ edit: []; delete: [] }>()
</script>
<template>
  <div class="pc-row-actions">
    <el-button
      v-if="showEdit"
      link
      type="primary"
      :disabled="deleting"
      @click.stop="emit('edit')"
    >
      {{ editLabel }}
    </el-button>
    <el-button
      link
      type="danger"
      :loading="deleting"
      @click.stop="emit('delete')"
    >
      {{ deleteLabel }}
    </el-button>
  </div>
</template>
