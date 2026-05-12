<script setup lang="ts">
// 未实现页面占位：保留路由跳转能力，后续替换为对应 Pencil 页面。
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useRoute } from 'vue-router'

const route = useRoute()
const pageTitle = computed(() => route.meta.title)
</script>

<template>
  <section class="placeholder-page" :aria-label="`${pageTitle}页面`">
    <h1>{{ pageTitle }}</h1>
    <p>该页面将根据 Pencil 设计稿继续实现。</p>
    <RouterLink
      v-if="route.path === '/profile'"
      class="placeholder-link"
      to="/finance/accounts"
    >
      进入账户管理
    </RouterLink>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
