<script setup lang="ts">
withDefaults(
  defineProps<{
    visible?: boolean
    title?: string
    description?: string
    actionText?: string
  }>(),
  { visible: true, title: '暂无数据' },
)
const emit = defineEmits<{ action: [] }>()
</script>
<template>
  <el-empty v-if="visible" :description="title" :image-size="70">
    <el-button v-if="actionText" type="primary" @click="emit('action')">
      {{ actionText }}
    </el-button>
  </el-empty>
</template>
