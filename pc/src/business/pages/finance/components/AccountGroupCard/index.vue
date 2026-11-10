<script setup lang="ts">
// 账户分组卡片：展示分组标题及组内账户条目。
import { RouterLink } from 'vue-router'
import type { AccountGroup } from '@/business/types/account'
import AccountIcon from '@/business/components/common/AccountIcon/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'

defineProps<{
  group: AccountGroup
}>()

defineEmits<{
  toggle: []
}>()

function isNegativeAmount(value?: string | number) {
  const normalized = String(value ?? '').trim()
  if (normalized.startsWith('-')) {
    return true
  }

  const parsed = Number(normalized.replace(/[^\d.+-]/g, ''))
  return Number.isFinite(parsed) && parsed < 0
}

function getAmountTone(value?: string | number): 'negative' | 'inherit' {
  return isNegativeAmount(value) ? 'negative' : 'inherit'
}
</script>

<template>
  <el-card shadow="never" class="pc-record-card">
    <div class="pc-toolbar">
      <h2>
        <RouterLink v-if="group.path" :to="group.path">
          {{ group.title }}
        </RouterLink>
        <span v-else>{{ group.title }}</span>
      </h2>
      <AmountText :value="group.amount || ''" />
      <el-button text @click="$emit('toggle')">
        {{ group.collapsed ? '展开' : '收起' }}
      </el-button>
    </div>
    <el-table v-show="!group.collapsed" :data="group.items" stripe>
      <el-table-column label="账户" prop="name">
        <template #default="{ row }">
          <RouterLink v-if="row.path" :to="row.path">{{ row.name }}</RouterLink>
          <span v-else>{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="金额" align="right">
        <template #default="{ row }">
          <AmountText
            :value="row.amount"
            :tone="getAmountTone(row.amount)"
            :show-sign="isNegativeAmount(row.amount)"
            show-unit
          />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
