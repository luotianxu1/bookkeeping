<script setup lang="ts">
// 公司公告 + 分红送配（A股）：时间轴 + 票根式方案，自 DetailPage 抽出。
import type { ExtraInfo } from '@/business/api/modules/market'

defineProps<{
  extra: ExtraInfo
}>()
</script>

<template>
  <section
    v-if="extra.announcements.length || extra.dividends.length"
    class="extra-row"
  >
    <el-card
      v-if="extra.announcements.length"
      class="pc-record-card panel"
      shadow="never"
    >
      <div class="spot-head">
        <h2>公司公告</h2>
        <span class="spot-note">
          最新 {{ extra.announcements.length }} 条 · 新浪
        </span>
      </div>
      <ol class="news-list">
        <li
          v-for="(a, i) in extra.announcements"
          :key="a.url"
          :class="{ lead: i === 0 }"
        >
          <span class="news-date num">{{ a.date }}</span>
          <a :href="a.url" target="_blank" rel="noopener">
            <span class="news-title">{{ a.title }}</span>
            <span class="news-arrow" aria-hidden="true">↗</span>
          </a>
        </li>
      </ol>
    </el-card>
    <el-card
      v-if="extra.dividends.length"
      class="pc-record-card panel"
      shadow="never"
    >
      <div class="spot-head">
        <h2>分红送配</h2>
        <span class="spot-note">每10股口径 · 新浪</span>
      </div>
      <el-table :data="extra.dividends" stripe empty-text="暂无数据">
        <el-table-column label="除权除息日" min-width="140">
          <template #default="{ row: d }">{{ d.ex_date ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="分红方案" min-width="120">
          <template #default="{ row: d }">
            <span v-if="d.dividend" class="div-amt num">
              派 {{ parseFloat(d.dividend.toFixed(2)) }}
            </span>
            <span v-if="d.bonus" class="div-tag">送 {{ d.bonus }}</span>
            <span v-if="d.convert" class="div-tag">转 {{ d.convert }}</span>
            <span v-if="!d.dividend && !d.bonus && !d.convert" class="muted">
              —
            </span>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="120">
          <template #default="{ row: d }">{{ d.progress }}</template>
        </el-table-column>
        <el-table-column label="公告日" min-width="120">
          <template #default="{ row: d }">{{ d.announce }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
