<script setup lang="ts">
import { formatPercentageText } from '@/business/utils/percentage'

// 公司概况（A股）：资料字段 + 简介/主营业务 + 所属板块，自 DetailPage 抽出。
import { useRouter } from 'vue-router'
import type { F10Data } from '@/business/api/modules/market'
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
    <div class="pc-grid profile-grid">
      <div v-for="(v, k) in profile.fields" :key="k" class="profile-item">
        <span class="profile-label">{{ k }}</span>
        <span class="profile-value" :class="{ num: /\d/.test(v) }">
          {{ formatPercentageText(v) }}
        </span>
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
        <span v-if="sectors.industry" class="sector-chip industry">
          {{ sectors.industry }}
        </span>
        <span v-for="c in sectors.concepts" :key="c" class="sector-chip">
          {{ c }}
        </span>
      </div>
      <div v-if="sectors.siblings.length" class="sibling-row">
        <span class="sibling-label">同行业个股</span>
        <div class="sibling-links">
          <el-button
            v-for="s in sectors.siblings"
            :key="s.code"
            class="sibling-link"
            native-type="button"
            @click="openSibling(s.code)"
          >
            {{ s.name }}
          </el-button>
        </div>
      </div>
    </div>
  </section>
</template>
