<script setup lang="ts">
// 公共顶部返回栏：用于二级页面展示返回入口和页面标题。
import { useRouter } from 'vue-router'

const props = defineProps<{
  title: string
  backTo?: string
  backLabel?: string
}>()

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push(props.backTo ?? '/finance')
}
</script>

<template>
  <header class="page-header">
    <button class="page-header-back" type="button" :aria-label="backLabel ?? '返回'" @click="goBack">
      &lt;
    </button>
    <h1>{{ title }}</h1>
  </header>
</template>

<style scoped lang="scss" src="./style.scss"></style>
