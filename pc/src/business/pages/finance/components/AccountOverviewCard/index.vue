<script setup lang="ts">
// 账户总览卡片：展示账户管理页顶部总资产信息。
import type { AccountOverview } from '@/business/types/account'
import AmountText from '@/business/components/common/AmountText/index.vue'

defineProps<{
  overview: AccountOverview
}>()
</script>

<template>
  <el-card
    class="pc-record-card account-overview-card"
    aria-label="总资产"
    shadow="never"
  >
    <p>{{ overview.label }}</p>
    <AmountText tag="strong" :value="overview.amount" show-unit />
  </el-card>
</template>
