<script setup lang="ts">
import { ref } from 'vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  allAccountIconOptions,
  brokerAccountIconOptions,
  paymentAccountIconOptions,
} from '@/business/data/account-icons'

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

const accountIconModules = import.meta.glob(
  '../../../assets/account-icons/*.png',
  {
    eager: true,
    import: 'default',
  },
) as IconModuleMap

const accountLabelByKey = new Map(
  allAccountIconOptions.map((item) => [item.value, item.label] as const),
)

const paymentIconKeys = new Set(
  paymentAccountIconOptions.map((item) => item.value),
)
const brokerIconKeys = new Set(
  brokerAccountIconOptions.map((item) => item.value),
)

const accountIcons = createIconItems(
  accountIconModules,
  (key) => accountLabelByKey.get(key) ?? key,
)

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

function createIconItems(
  modules: IconModuleMap,
  getLabel: (key: string) => string,
) {
  return Object.entries(modules)
    .map(([path, source]) => {
      const filename = path.split('/').pop() ?? ''
      const key = filename.replace(/\.png$/i, '')
      return { key, label: getLabel(key), source }
    })
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN'))
}

const desktopIconQuery = ref('')
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="图标目录">
      <template #right>
        <el-input
          v-model="desktopIconQuery"
          placeholder="搜索图标名称"
          aria-label="搜索图标名称"
          clearable
          style="width: 240px"
        />
      </template>
    </PageHeader>
    <el-tabs class="desktop-tabs">
      <el-tab-pane
        v-for="group in iconGroups"
        :key="group.title"
        :label="group.title"
      >
        <div class="desktop-icon-library">
          <div
            v-for="item in group.items.filter((item) =>
              (item.label + item.key)
                .toLowerCase()
                .includes(desktopIconQuery.toLowerCase()),
            )"
            :key="item.key"
            class="desktop-icon-entry"
          >
            <el-image :src="item.source" :alt="item.label" fit="contain" />
            <strong>{{ item.label }}</strong>
            <code>{{ item.key }}</code>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
