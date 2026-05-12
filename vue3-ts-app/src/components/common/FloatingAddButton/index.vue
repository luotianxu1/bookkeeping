<script setup lang="ts">
// 公共悬浮新增按钮：用于页面右下角的新增操作入口。
withDefaults(defineProps<{
  ariaLabel?: string
}>(), {
  ariaLabel: '新增',
})

const emit = defineEmits<{
  click: []
}>()
</script>

<template>
  <button class="floating-add-button" type="button" :aria-label="ariaLabel" @click="emit('click')">
    +
  </button>
</template>

<style scoped lang="scss" src="./style.scss"></style>
