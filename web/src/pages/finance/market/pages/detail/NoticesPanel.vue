<script setup lang="ts">
// 公司公告 + 分红送配（A股）：时间轴 + 票根式方案，自 DetailPage 抽出。
import type { ExtraInfo } from '@/api/modules/market'

defineProps<{
  extra: ExtraInfo
}>()
</script>

<template>
  <section v-if="extra.announcements.length || extra.dividends.length" class="extra-row">
    <div v-if="extra.announcements.length" class="panel">
      <div class="spot-head">
        <h2>公司公告</h2>
        <span class="spot-note">最新 {{ extra.announcements.length }} 条 · 新浪</span>
      </div>
      <ol class="news-list">
        <li v-for="(a, i) in extra.announcements" :key="a.url" :class="{ lead: i === 0 }">
          <span class="news-date num">{{ a.date }}</span>
          <a :href="a.url" target="_blank" rel="noopener">
            <span class="news-title">{{ a.title }}</span>
            <span class="news-arrow" aria-hidden="true">↗</span>
          </a>
        </li>
      </ol>
    </div>
    <div v-if="extra.dividends.length" class="panel">
      <div class="spot-head">
        <h2>分红送配</h2>
        <span class="spot-note">每10股口径 · 新浪</span>
      </div>
      <table class="div-table">
        <thead>
          <tr>
            <th>除权除息日</th>
            <th>分红方案</th>
            <th>进度</th>
            <th class="num">公告日</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in extra.dividends" :key="d.announce + (d.ex_date ?? '')">
            <td class="num">{{ d.ex_date ?? '—' }}</td>
            <td>
              <span v-if="d.dividend" class="div-amt num">派 {{ parseFloat(d.dividend.toFixed(2)) }}</span>
              <span v-if="d.bonus" class="div-tag">送 {{ d.bonus }}</span>
              <span v-if="d.convert" class="div-tag">转 {{ d.convert }}</span>
              <span v-if="!d.dividend && !d.bonus && !d.convert" class="muted">—</span>
            </td>
            <td class="muted">{{ d.progress }}</td>
            <td class="num muted">{{ d.announce }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use './panelShared';

/* 公司公告：时间轴 */
.news-list {
  position: relative;
  margin: 0;
  padding: 0 0 0 var(--space-16);
  list-style: none;

  /* 左侧规则线，串起所有节点 */
  &::before {
    content: '';
    position: absolute;
    left: 3px;
    top: var(--space-10);
    bottom: var(--space-10);
    width: 1px;
    background: var(--color-border);
  }

  li {
    position: relative;
    border-bottom: 1px solid var(--color-border);

    &:last-child {
      border-bottom: none;
    }

    /* 节点圆点：首条用强调色实心 */
    &::before {
      content: '';
      position: absolute;
      left: calc(-1 * var(--space-16));
      top: 15px;
      width: 7px;
      height: 7px;
      border: 1px solid var(--color-border);
      border-radius: 50%;
      background: var(--color-bg-card);
    }

    &.lead::before {
      border-color: var(--color-brand);
      background: var(--color-brand);
    }
  }

  a {
    display: flex;
    gap: var(--space-12);
    align-items: baseline;
    padding: var(--space-8) var(--space-2);
    color: inherit;
    text-decoration: none;
  }
}

.news-date {
  flex-shrink: 0;
  width: 76px;
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.news-title {
  flex: 1;
  overflow: hidden;
  font-size: var(--font-size-13);
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color 0.15s ease-out;
}

/* 首条公告破格：换行全文 + 加重 */
.news-list li.lead .news-title {
  font-weight: 600;
  line-height: 1.55;
  white-space: normal;
}

.news-arrow {
  flex-shrink: 0;
  color: var(--color-brand);
  opacity: 0;
  transform: translate(-4px, 2px);
  transition:
    opacity 0.15s ease-out,
    transform 0.15s ease-out;
}

.news-list a:hover .news-title,
.news-list a:focus-visible .news-title {
  color: var(--color-brand);
}

.news-list a:hover .news-arrow,
.news-list a:focus-visible .news-arrow {
  opacity: 1;
  transform: none;
}

/* 分红送配：派息金额强调 */
.div-table th,
.div-table td {
  padding: var(--space-8) var(--space-10);
}

.div-amt {
  color: var(--color-chart-gold);
  font-weight: 700;
}

.div-tag {
  display: inline-block;
  margin-left: var(--space-6);
  padding: 1px var(--space-8);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}
</style>
