<script setup lang="ts">
// 账户分组卡片：展示分组标题及组内账户条目。
import { RouterLink } from 'vue-router'
import type { AccountGroup } from '@/types/account'
import AmountText from '@/components/common/AmountText/index.vue'

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
  <section :class="['account-group-card', { 'is-collapsed': group.collapsed }]">
    <header class="account-group-header">
      <div class="account-group-heading">
        <RouterLink v-if="group.path" class="account-group-title-link" :to="group.path">
          <h2>{{ group.title }}</h2>
        </RouterLink>
        <h2 v-else class="account-group-title">{{ group.title }}</h2>

        <AmountText
          v-if="group.amount"
          class="account-group-amount"
          tag="strong"
          :value="group.amount"
          :tone="getAmountTone(group.amount)"
          :show-sign="isNegativeAmount(group.amount)"
          show-unit
        />
      </div>

      <button
        class="account-group-toggle"
        type="button"
        :aria-expanded="!group.collapsed"
        :aria-label="group.collapsed ? `展开${group.title}` : `收起${group.title}`"
        @click="$emit('toggle')"
      >
        <span class="account-group-toggle-icon" :class="{ 'is-collapsed': group.collapsed }" aria-hidden="true"></span>
      </button>
    </header>

    <ul v-show="!group.collapsed" class="account-list">
      <li
        v-for="item in group.items"
        :key="item.id ?? item.name"
        class="account-list-item"
      >
        <RouterLink v-if="item.path" class="account-item-link" :to="item.path">
          <span class="account-item-left">
            <span class="account-item-icon">{{ item.icon }}</span>
            <span class="account-item-text">
              <span class="account-item-name">{{ item.name }}</span>
            </span>
          </span>
          <span class="account-item-right">
            <AmountText
              tag="strong"
              :value="item.amount"
              :tone="getAmountTone(item.amount)"
              :show-sign="isNegativeAmount(item.amount)"
              show-unit
            />
            <span class="account-item-chevron" aria-hidden="true"></span>
          </span>
        </RouterLink>

        <template v-else>
          <span class="account-item-left">
            <span class="account-item-icon">{{ item.icon }}</span>
            <span class="account-item-text">
              <span class="account-item-name">{{ item.name }}</span>
            </span>
          </span>
          <span class="account-item-right">
            <AmountText
              tag="strong"
              :value="item.amount"
              :tone="getAmountTone(item.amount)"
              :show-sign="isNegativeAmount(item.amount)"
              show-unit
            />
          </span>
        </template>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
