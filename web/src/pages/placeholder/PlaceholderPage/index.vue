<script setup lang="ts">
// 未实现页面占位：保留路由跳转能力，后续替换为对应 Pencil 页面。
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'

const route = useRoute()
const pageTitle = computed(() => String(route.meta.title ?? '页面'))
</script>

<template>
  <section class="placeholder-page" :aria-label="`${pageTitle}页面`">
    <PageHeader :title="pageTitle" back-to="/finance" back-label="返回财务首页" />
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
