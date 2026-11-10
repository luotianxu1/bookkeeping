<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@lucide/vue'
import { modules } from '@/data/modules'
import { can, session } from '@/stores/session'
const keyword = ref('')
const available = computed(() =>
  modules
    .map((group) => ({
      ...group,
      entries: group.entries.filter(
        (item) =>
          can(item.permission || 'pc.access') &&
          (!item.adminOnly ||
            ['admin', 'super_admin'].includes(
              session.data?.user.roleCode || '',
            )),
      ),
    }))
    .filter((group) => group.entries.length),
)
const filtered = computed(() =>
  available.value
    .map((group) => ({
      ...group,
      entries: group.entries.filter((item) =>
        (group.title + item.title).includes(keyword.value.trim()),
      ),
    }))
    .filter((group) => group.entries.length),
)
const total = computed(() =>
  available.value.reduce((count, group) => count + group.entries.length, 0),
)
</script>
<template>
  <section>
    <div class="page-heading">
      <h1>
        功能总览
        <el-tag type="info" size="small">{{ total }} 项功能</el-tag>
      </h1>
      <el-input
        v-model="keyword"
        aria-label="搜索项目功能"
        placeholder="搜索功能"
        clearable
        class="feature-search"
      >
        <template #prefix><Search :size="16" /></template>
      </el-input>
    </div>
    <div class="feature-grid">
      <el-card
        v-for="group in filtered"
        :key="group.id"
        class="feature-card"
        shadow="never"
      >
        <template #header>
          <div class="feature-card-top">
            <h2>{{ group.title }}</h2>
            <el-tag type="info" size="small">{{ group.entries.length }}</el-tag>
          </div>
        </template>
        <div class="module-links">
          <RouterLink
            v-for="entry in group.entries"
            :key="entry.path"
            :to="entry.path"
          >
            {{ entry.title }}
            <span>›</span>
          </RouterLink>
        </div>
      </el-card>
    </div>
    <el-empty v-if="!filtered.length" description="没有找到相关功能" />
  </section>
</template>
<style scoped>
.module-links {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 14px;
}
.module-links a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  font-size: 13px;
  border-radius: 4px;
}
.module-links a:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.module-links span {
  color: var(--el-text-color-secondary);
}
</style>
