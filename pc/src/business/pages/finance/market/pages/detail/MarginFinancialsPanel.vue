<script setup lang="ts">
// F10：融资融券 + 财务指标（A股），自 DetailPage 抽出。
import type { F10Data } from '@/business/api/modules/market'
import { dirClass, pct, signedYi } from '@/business/utils/marketFormat'
import { finVal, wan, yi } from './format'

defineProps<{
  f10: F10Data
}>()
</script>

<template>
  <section v-if="f10.margin || f10.financials" class="extra-row">
    <el-card v-if="f10.margin" class="pc-record-card panel" shadow="never">
      <div class="spot-head">
        <h2>融资融券</h2>
        <span class="spot-note">{{ f10.margin.date }} · 交易所官方</span>
      </div>
      <el-card class="pc-record-card margin-hero" shadow="never">
        <div class="margin-hero-main">
          <span class="margin-hero-label">融资余额</span>
          <span class="num flow-hero-value">{{ yi(f10.margin.balance) }}</span>
        </div>
        <div v-if="f10.margin.delta !== null" class="margin-hero-sub num">
          较上日
          <b :class="dirClass(f10.margin.delta)">
            {{ signedYi(f10.margin.delta) }}
          </b>
        </div>
      </el-card>
      <dl class="val-grid">
        <div class="val-item">
          <dt>融资买入额</dt>
          <dd class="num">{{ yi(f10.margin.buy) }}</dd>
        </div>
        <div class="val-item">
          <dt>融资偿还额</dt>
          <dd class="num">
            {{ f10.margin.repay === null ? '—' : yi(f10.margin.repay) }}
          </dd>
        </div>
        <div class="val-item">
          <dt>融券余量</dt>
          <dd class="num">{{ wan(f10.margin.short_vol) }}</dd>
        </div>
        <div class="val-item">
          <dt>融券卖出量</dt>
          <dd class="num">
            {{
              f10.margin.short_sell === null ? '—' : wan(f10.margin.short_sell)
            }}
          </dd>
        </div>
      </dl>
    </el-card>
    <el-card v-if="f10.financials" class="pc-record-card panel" shadow="never">
      <div class="spot-head">
        <h2>财务指标</h2>
        <span class="spot-note num">
          {{ f10.financials.latest_date }} vs {{ f10.financials.prev_date }}
        </span>
      </div>
      <div class="table-wrap">
        <el-table :data="f10.financials.rows" stripe empty-text="暂无数据">
          <el-table-column label="指标" min-width="140">
            <template #default="{ row: r }">{{ r.name }}</template>
          </el-table-column>
          <el-table-column label="最新报告期" min-width="120">
            <template #default="{ row: r }">{{ finVal(r.latest) }}</template>
          </el-table-column>
          <el-table-column label="上年同期" min-width="120">
            <template #default="{ row: r }">{{ finVal(r.prev) }}</template>
          </el-table-column>
          <el-table-column label="同比" min-width="120">
            <template #default="{ row: r }">
              <span :class="dirClass(r.yoy ?? 0)">
                {{ r.yoy === null ? '—' : pct(r.yoy) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </section>
</template>
