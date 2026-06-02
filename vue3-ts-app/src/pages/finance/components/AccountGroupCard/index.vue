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
</script>

<template>
  <section class="account-group-card">
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
        <span>{{ group.collapsed ? '展开' : '收起' }}</span>
        <span class="account-group-toggle-icon" :class="{ 'is-collapsed': group.collapsed }">⌃</span>
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
            <AmountText tag="strong" :value="item.amount" show-unit />
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
            <AmountText tag="strong" :value="item.amount" show-unit />
          </span>
        </template>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
