<script setup lang="ts">
// 现金资产详情页：还原 Pencil「现金资产-详情」画板中的总览与修改历史。
import { computed, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import { cashAssetDetailOverview, cashAssetHistory } from '@/data/account'

type ChangeMode = 'increase' | 'decrease'

const showEditModal = ref(false)
const changeMode = ref<ChangeMode>('increase')
const changeAmount = ref('0.00')
const remark = ref('')

const detailOverview = ref({ ...cashAssetDetailOverview })
const historyList = ref(
  cashAssetHistory.map((entry) => ({
    ...entry,
    change: formatHistoryAmount(parseAmount(entry.change), entry.trend === 'up' ? 'increase' : 'decrease'),
  })),
)

const historyCountText = computed(() => `共 ${historyList.value.length} 条`)

function parseAmount(amountText: string) {
  return Number(amountText.replace(/[^\d.-]/g, '')) || 0
}

function formatCurrency(amount: number) {
  return `${amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatHistoryAmount(amount: number, mode: ChangeMode) {
  void mode
  const abs = Math.abs(amount)
  return `${abs.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function openEditModal() {
  showEditModal.value = true
}

function closeEditModal() {
  showEditModal.value = false
}

function resetEditForm() {
  changeMode.value = 'increase'
  changeAmount.value = '0.00'
  remark.value = ''
}

function saveAssetChange() {
  const amount = Number(changeAmount.value || '0')
  if (!Number.isFinite(amount) || amount <= 0) return

  const previousBalance = parseAmount(detailOverview.value.amount)
  const nextBalance = changeMode.value === 'increase'
    ? previousBalance + amount
    : previousBalance - amount

  detailOverview.value.amount = formatCurrency(nextBalance)
  historyList.value.unshift({
    title: remark.value.trim() || (changeMode.value === 'increase' ? '手动增加余额' : '手动减少余额'),
    time: '刚刚',
    change: formatHistoryAmount(amount, changeMode.value),
    balance: `余额 ${formatCurrency(nextBalance)}`,
    trend: changeMode.value === 'increase' ? 'up' : 'down',
  })

  closeEditModal()
  resetEditForm()
}
</script>

<template>
  <section class="cash-asset-detail-page" aria-label="现金资产详情">
    <header class="cash-asset-detail-header">
      <PageHeader title="资产详情" back-to="/finance/accounts/cash" back-label="返回现金账户" />
    </header>

    <section class="cash-asset-overview-card" aria-label="现金资产总览">
      <div class="cash-asset-overview-top">
        <div class="cash-asset-overview-title">
          <strong>{{ detailOverview.accountName }}</strong>
          <span>{{ detailOverview.assetType }}</span>
        </div>
        <button type="button" class="cash-asset-edit" @click="openEditModal">修改</button>
      </div>

      <AmountText tag="p" class="cash-asset-overview-amount" :value="detailOverview.amount" />
    </section>

    <section class="cash-asset-history-wrap" aria-label="修改历史">
      <header class="cash-asset-history-head">
        <strong>修改历史</strong>
        <span>{{ historyCountText }}</span>
      </header>

      <section class="cash-asset-history-card">
        <article v-for="entry in historyList" :key="`${entry.title}-${entry.time}`" class="cash-asset-history-item">
          <div class="cash-asset-history-left">
            <strong>{{ entry.title }}</strong>
            <span>{{ entry.time }}</span>
          </div>
          <div class="cash-asset-history-right">
            <AmountText
              tag="strong"
              :class="entry.trend === 'up' ? 'up' : 'down'"
              :tone="entry.trend === 'up' ? 'positive' : 'negative'"
              :value="entry.change"
            />
            <span>{{ entry.balance }}</span>
          </div>
        </article>
      </section>
    </section>

    <Transition name="edit-fade">
      <section
        v-if="showEditModal"
        class="asset-edit-overlay"
        role="dialog"
        aria-modal="true"
      >
        <button type="button" class="asset-edit-backdrop" aria-label="关闭弹窗" @click="closeEditModal"></button>

        <section class="asset-edit-modal">
          <h2>修改资产</h2>

          <div class="asset-edit-field">
            <label>资产名称</label>
            <div class="asset-edit-input static">{{ detailOverview.accountName }}</div>
          </div>

          <div class="asset-edit-field">
            <label>金额变动</label>
            <div class="asset-edit-segment">
              <button
                type="button"
                :class="['asset-edit-segment-item', { active: changeMode === 'increase' }]"
                @click="changeMode = 'increase'"
              >
                增加
              </button>
              <button
                type="button"
                :class="['asset-edit-segment-item', { active: changeMode === 'decrease' }]"
                @click="changeMode = 'decrease'"
              >
                减少
              </button>
            </div>
            <div class="asset-edit-input amount-wrap">
              <span></span>
              <input
                v-model="changeAmount"
                class="asset-edit-amount-input"
                type="number"
                inputmode="decimal"
                placeholder="0.00"
              />
            </div>
          </div>

          <div class="asset-edit-field">
            <label>备注</label>
            <input
              v-model="remark"
              class="asset-edit-input"
              type="text"
              placeholder="添加说明"
            />
          </div>

          <div class="asset-edit-actions">
            <button type="button" class="asset-edit-btn cancel" @click="closeEditModal">
              取消
            </button>
            <button type="button" class="asset-edit-btn confirm" @click="saveAssetChange">
              保存
            </button>
          </div>
        </section>
      </section>
    </Transition>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
