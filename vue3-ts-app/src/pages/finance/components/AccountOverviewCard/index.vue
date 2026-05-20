<script setup lang="ts">
// 账户总览卡片：展示账户管理页顶部总资产信息。
import type { AccountOverview } from '@/types/account'
import AmountText from '@/components/common/AmountText/index.vue'

defineProps<{
  overview: AccountOverview
}>()
</script>

<template>
  <section class="account-overview-card" aria-label="总资产">
    <p>{{ overview.label }}</p>
    <AmountText tag="strong" :value="overview.amount" show-unit show-sign />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
