<script setup lang="ts">
// 账户分组卡片：展示分组标题及组内账户条目。
import { RouterLink } from 'vue-router'
import type { AccountGroup } from '@/types/account'
import AmountText from '@/components/common/AmountText/index.vue'

defineProps<{
  group: AccountGroup
}>()
</script>

<template>
  <section class="account-group-card">
    <RouterLink v-if="group.path" class="account-group-title-link" :to="group.path">
      <h2>{{ group.title }}</h2>
      <span>&gt;</span>
    </RouterLink>
    <h2 v-else>{{ group.title }}</h2>

    <ul class="account-list">
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
            <AmountText tag="strong" :value="item.amount" show-unit show-sign />
            <span>&gt;</span>
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
            <AmountText tag="strong" :value="item.amount" show-unit show-sign />
            <span>&gt;</span>
          </span>
        </template>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
