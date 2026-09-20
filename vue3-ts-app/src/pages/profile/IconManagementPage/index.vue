<script setup lang="ts">
import { ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  allAccountIconOptions,
  brokerAccountIconOptions,
  paymentAccountIconOptions,
} from '@/data/account-icons'

type IconModuleMap = Record<string, string>

type IconCatalogItem = {
  key: string
  label: string
  source: string
}

type IconCatalogGroup = {
  title: string
  items: IconCatalogItem[]
}

const accountIconModules = import.meta.glob('../../../assets/account-icons/*.png', {
  eager: true,
  import: 'default',
}) as IconModuleMap

const accountLabelByKey = new Map(
  allAccountIconOptions.map((item) => [item.value, item.label] as const),
)

const paymentIconKeys = new Set(paymentAccountIconOptions.map((item) => item.value))
const brokerIconKeys = new Set(brokerAccountIconOptions.map((item) => item.value))
const collapsedGroups = ref<Set<string>>(new Set())

const accountIcons = createIconItems(accountIconModules, (key) => (
  accountLabelByKey.get(key) ?? key
))

const iconGroups: IconCatalogGroup[] = [
  {
    title: '支付钱包',
    items: accountIcons.filter((item) => paymentIconKeys.has(item.key)),
  },
  {
    title: '银行图标',
    items: accountIcons.filter((item) => item.key.startsWith('bank-')),
  },
  {
    title: '证券公司',
    items: accountIcons.filter((item) => brokerIconKeys.has(item.key)),
  },
]

function createIconItems(modules: IconModuleMap, getLabel: (key: string) => string) {
  return Object.entries(modules)
    .map(([path, source]) => {
      const filename = path.split('/').pop() ?? ''
      const key = filename.replace(/\.png$/i, '')
      return { key, label: getLabel(key), source }
    })
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN'))
}

function isGroupCollapsed(title: string) {
  return collapsedGroups.value.has(title)
}

function toggleGroup(title: string) {
  const nextGroups = new Set(collapsedGroups.value)
  if (nextGroups.has(title)) {
    nextGroups.delete(title)
  } else {
    nextGroups.add(title)
  }
  collapsedGroups.value = nextGroups
}
</script>

<template>
  <section class="icon-management-page" aria-label="图标管理">
    <PageHeader title="图标管理" back-to="/profile" back-label="返回我的" :prefer-back-to="true" />

    <section
      v-for="group in iconGroups"
      :key="group.title"
      class="icon-catalog-section"
      :aria-label="group.title"
    >
      <header class="icon-catalog-heading">
        <h2>{{ group.title }}</h2>
        <div class="icon-catalog-heading-actions">
          <span class="icon-catalog-count">{{ group.items.length }}</span>
          <button
            class="icon-catalog-toggle"
            type="button"
            :title="isGroupCollapsed(group.title) ? `展开${group.title}` : `收起${group.title}`"
            :aria-label="isGroupCollapsed(group.title) ? `展开${group.title}` : `收起${group.title}`"
            :aria-expanded="!isGroupCollapsed(group.title)"
            @click="toggleGroup(group.title)"
          >
            <span
              :class="['icon-catalog-toggle-chevron', { collapsed: isGroupCollapsed(group.title) }]"
              aria-hidden="true"
            ></span>
          </button>
        </div>
      </header>

      <div v-show="!isGroupCollapsed(group.title)" class="icon-catalog-grid">
        <article v-for="item in group.items" :key="item.key" class="icon-catalog-item">
          <div class="icon-preview">
            <img :src="item.source" :alt="`${item.label}图标`" width="30" height="30" />
          </div>
          <strong>{{ item.label }}</strong>
          <code>{{ item.key }}</code>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
