<script setup lang="ts">
// 公司概况（A股）：资料字段 + 简介/主营业务 + 所属板块，自 DetailPage 抽出。
import { useRouter } from 'vue-router'
import type { F10Data } from '@/api/modules/market'
import { detailRoute } from '../../routes'

defineProps<{
  profile: NonNullable<F10Data['profile']>
  sectors: F10Data['sectors']
}>()

const router = useRouter()

/** 同行业个股跳详情 */
function openSibling(code2: string): void {
  void router.push(detailRoute('stock', code2))
}
</script>

<template>
  <section class="kline-section">
    <div class="spot-head">
      <h2>公司概况</h2>
      <span class="spot-note">新浪公司资料</span>
    </div>
    <div class="profile-grid">
      <div v-for="(v, k) in profile.fields" :key="k" class="profile-item">
        <span class="profile-label">{{ k }}</span>
        <span class="profile-value" :class="{ num: /\d/.test(v) }">{{ v }}</span>
      </div>
    </div>
    <div v-if="profile.intro" class="profile-block">
      <h3>公司简介</h3>
      <p>{{ profile.intro }}</p>
    </div>
    <div v-if="profile.mainbiz" class="profile-block">
      <h3>主营业务</h3>
      <p>{{ profile.mainbiz }}</p>
    </div>
    <!-- 所属板块：申万行业 + 概念 -->
    <div v-if="sectors" class="profile-block">
      <h3>所属板块</h3>
      <div class="sector-chips">
        <span v-if="sectors.industry" class="sector-chip industry">{{ sectors.industry }}</span>
        <span v-for="c in sectors.concepts" :key="c" class="sector-chip">{{ c }}</span>
      </div>
      <div v-if="sectors.siblings.length" class="sibling-row">
        <span class="sibling-label">同行业个股</span>
        <div class="sibling-links">
          <button
            v-for="s in sectors.siblings"
            :key="s.code"
            class="sibling-link"
            type="button"
            @click="openSibling(s.code)"
          >{{ s.name }}</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use './panelShared';

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-8) var(--space-16);
}

.profile-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-6) 0;
  border-bottom: 1px solid var(--color-border);
}

.profile-label {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.profile-value {
  color: var(--color-text-primary);
  font-size: var(--font-size-12);
  word-break: break-all;
}

.profile-block {
  margin-top: var(--space-12);
  padding-top: var(--space-10);
  border-top: 1px dashed var(--color-border);

  h3 {
    margin: 0 0 var(--space-6);
    color: var(--color-brand);
    font-size: var(--font-size-11);
    letter-spacing: 0.08em;
  }

  p {
    margin: 0;
    color: var(--color-text-primary);
    font-size: var(--font-size-12);
    line-height: 1.7;
  }
}

/* 板块 chips：行业琥珀实底，概念描边 */
.sector-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-6);
}

.sector-chip {
  padding: var(--space-2) var(--space-10);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  color: var(--color-text-secondary);
  font-size: var(--font-size-11);

  &.industry {
    border-color: rgba(234, 179, 8, 0.5);
    background: rgba(234, 179, 8, 0.1);
    color: #b45309;
  }
}

.sibling-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
  margin-top: var(--space-10);
}

.sibling-label {
  flex-shrink: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.sibling-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4) var(--space-12);
}

.sibling-link {
  padding: 0;
  background: transparent;
  border: none;
  border-bottom: 1px dashed transparent;
  color: var(--color-text-primary);
  font: inherit;
  font-size: var(--font-size-12);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;

  &:hover {
    color: var(--color-brand);
    border-bottom-color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}
</style>
