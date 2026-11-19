<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

import { formatAmount } from '@/business/utils/format'
// 黄金账户持仓页：对接黄金聚合查询接口，并补充持仓新增、修改、删除能力。
import { onMounted } from 'vue'

import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { useFeedback } from '@/business/composables/useFeedback'
import { allAccountIconOptions } from '@/business/data/account-icons'
import {
  formatCreatedDate,
  formatRate,
  formatSummaryWeight,
  getProfitToneClass,
} from './goldPositionFormat'
import { useGoldPositionData } from './useGoldPositionData'
import { useGoldPositionActions } from './useGoldPositionActions'

const { showFeedbackModal, feedbackMessage, feedbackType, showFeedback } =
  useFeedback()

const {
  isLoading,
  pageError,
  isRefreshingGold,
  goldAccounts,
  formAccountId,
  formFundingAccountId,
  isScopedToAccount,
  scopedAccount,
  realtimeGoldPrice,
  realtimeGoldUpdatedAt,
  filteredHoldings,
  visibleSummary,
  hasHoldings,
  fundingAccountOptions,
  sellFundingAccountOptions,
  syncFormSelections,
  loadGoldPosition,
  refreshGoldData,
  openLiquidationList,
} = useGoldPositionData({ showFeedback })

const {
  isSavingPosition,
  isDeletingPosition,
  isLoadingPositionDetail,
  isSellingPosition,
  isSavingAccountSettings,
  actionError,
  positionFormError,
  deleteError,
  sellError,
  showPositionModal,
  showDeleteConfirmModal,
  showSellModal,
  showAccountSettingsModal,
  editingPosition,
  selectedHolding,
  deletingHolding,
  formWeight,
  formBuyPrice,
  formIncludeInNetWorth,
  formRemark,
  sellFundingAccountId,
  sellWeight,
  sellPrice,
  sellFee,
  sellRemark,
  accountSettingsName,
  accountSettingsIcon,
  accountSettingsRemark,
  accountSettingsIncludeInNetWorth,
  accountSettingsError,
  positionModalTitle,
  sellModalTitle,
  sellAvailableQuantityText,
  sellAmountPreview,
  openAccountSettingsModal,
  closeAccountSettingsModal,
  saveAccountSettings,
  openCreateModal,
  toggleHoldingActions,
  handleHoldingCardKeydown,
  openEditModalFromAction,
  closePositionModal,
  savePosition,
  openDeleteConfirmModalFromAction,
  openSellModalFromAction,
  closeDeleteConfirmModal,
  closeSellModal,
  confirmDeletePosition,
  submitSellPosition,
} = useGoldPositionActions({
  goldAccounts,
  isScopedToAccount,
  scopedAccount,
  realtimeGoldPrice,
  formAccountId,
  formFundingAccountId,
  syncFormSelections,
  loadGoldPosition,
  showFeedback,
})

onMounted(() => {
  void loadGoldPosition()
})

type DetailTableRow = (typeof filteredHoldings.value)[number]

function formatSignedGoldProfit(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}¥${formatAmount(Math.abs(amount))}`
}
</script>
<template>
  <section class="desktop-workspace stagger-in">
    <PageHeader :title="scopedAccount?.name || '黄金持仓'">
      <template #right>
        <el-button @click="openLiquidationList">清仓明细</el-button>
        <el-button :loading="isRefreshingGold" @click="refreshGoldData">
          刷新金价
        </el-button>
        <el-button v-if="scopedAccount" @click="openAccountSettingsModal">
          账户设置
        </el-button>
        <el-button type="primary" @click="openCreateModal">新增持仓</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        {
          label: '持有克重',
          value: formatSummaryWeight(visibleSummary.totalWeight),
        },
        {
          label: '持仓估值',
          value: formatAmount(visibleSummary.estimatedValue),
        },
        {
          label: '预估收益',
          value: formatAmount(visibleSummary.estimatedProfit),
        },
        { label: '收益率', value: formatRate(visibleSummary.profitRate) },
      ]"
    />
    <div class="desktop-main-aside">
      <DesktopRecordList
        :rows="filteredHoldings"
        title="黄金持仓"
        searchable
        search-placeholder="搜索持仓"
        :search-keys="['productName']"
        row-key="id"
      >
        <template #default="{ row }: { row: DetailTableRow }">
          <article class="record-card gold-holding-card">
            <div class="record-card-head">
              <div class="record-card-title">
                <span>{{ row.productName }}</span>
              </div>
              <div class="record-card-aside">
                <el-tag
                  v-if="realtimeGoldPrice > 0"
                  size="small"
                  effect="plain"
                  class="gold-holding-price-tag"
                >
                  实时金价 {{ formatAmount(realtimeGoldPrice) }}/克
                </el-tag>
              </div>
            </div>
            <div class="gold-holding-weight">
              <span class="gold-holding-weight-label">持有克重</span>
              <strong class="gold-holding-weight-value">
                {{ formatSummaryWeight(row.weight) }}
              </strong>
              <span class="gold-holding-weight-unit">克</span>
            </div>
            <div
              class="gold-holding-profit"
              :class="getProfitToneClass(row.holdingProfit)"
            >
              <span>持仓收益</span>
              <strong>{{ formatSignedGoldProfit(row.holdingProfit) }}</strong>
            </div>
            <dl class="record-card-body">
              <div>
                <dt>成本均价</dt>
                <dd>{{ formatAmount(row.avgCostPrice) }} 元/克</dd>
              </div>
              <div>
                <dt>买入金额</dt>
                <dd>{{ formatAmount(row.purchaseAmount) }}</dd>
              </div>
              <div>
                <dt>买入时间</dt>
                <dd>{{ formatCreatedDate(row.createdAt) }}</dd>
              </div>
            </dl>
            <div class="record-card-foot">
              <el-button
                link
                :disabled="
                  isLoadingPositionDetail ||
                  isSavingPosition ||
                  isDeletingPosition ||
                  isSellingPosition
                "
                type="primary"
                @click="
                  () => {
                    selectedHolding = row
                    openEditModalFromAction()
                  }
                "
              >
                修改
              </el-button>
              <el-button
                link
                :disabled="
                  isLoadingPositionDetail ||
                  isSavingPosition ||
                  isDeletingPosition ||
                  isSellingPosition
                "
                type="primary"
                @click="
                  () => {
                    selectedHolding = row
                    openSellModalFromAction()
                  }
                "
              >
                卖出
              </el-button>
              <el-button
                link
                :disabled="
                  isLoadingPositionDetail ||
                  isSavingPosition ||
                  isDeletingPosition ||
                  isSellingPosition
                "
                type="danger"
                @click="
                  () => {
                    selectedHolding = row
                    openDeleteConfirmModalFromAction()
                  }
                "
              >
                删除
              </el-button>
            </div>
          </article>
        </template>
      </DesktopRecordList>
      <PagePanel title="价格与成本">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="实时金价">
            {{
              realtimeGoldPrice > 0
                ? formatAmount(realtimeGoldPrice) + ' 元/克'
                : '—'
            }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ realtimeGoldUpdatedAt }}
          </el-descriptions-item>
          <el-descriptions-item label="平均成本">
            {{ formatAmount(visibleSummary.averagePrice) }}
          </el-descriptions-item>
          <el-descriptions-item label="购入总价">
            {{ formatAmount(visibleSummary.purchaseTotal) }}
          </el-descriptions-item>
          <el-descriptions-item label="累计收益">
            {{ formatAmount(visibleSummary.cumulativeProfit) }}
          </el-descriptions-item>
        </el-descriptions>
      </PagePanel>
    </div>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showAccountSettingsModal"
      title="修改黄金账户"
      :close-on-overlay="!isSavingAccountSettings"
      @close="closeAccountSettingsModal()"
    >
      <el-form
        class="gold-position-form"
        @submit.prevent="saveAccountSettings"
        label-position="top"
      >
        <CommonInput
          v-model="accountSettingsName"
          label="账户名称"
          placeholder="输入黄金账户名称"
        />
        <CommonSelect
          v-model="accountSettingsIcon"
          label="账户图标"
          :options="allAccountIconOptions"
          :disabled="isSavingAccountSettings"
        />
        <CommonInput
          v-model="accountSettingsRemark"
          label="备注"
          placeholder="输入账户说明"
        />
        <CommonSwitch
          v-model="accountSettingsIncludeInNetWorth"
          label="是否计入总资产"
        />
        <p v-if="accountSettingsError" class="gold-position-form-error">
          {{ accountSettingsError }}
        </p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar gold-position-actions">
          <CommonButton
            variant="secondary"
            type="button"
            :disabled="isSavingAccountSettings"
            @click="closeAccountSettingsModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            type="button"
            :disabled="isSavingAccountSettings"
            @click="saveAccountSettings"
          >
            {{ isSavingAccountSettings ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showPositionModal"
      :title="positionModalTitle"
      :close-on-overlay="!isSavingPosition"
      @close="closePositionModal"
    >
      <el-form
        class="gold-position-form"
        @submit.prevent="savePosition"
        label-position="top"
      >
        <CommonSelect
          v-if="!editingPosition"
          v-model="formFundingAccountId"
          label="资金账户（选填）"
          :options="fundingAccountOptions"
          :disabled="false"
        />
        <p
          v-if="!editingPosition"
          class="gold-position-form-hint gold-position-form-hint-warning"
        >
          不选则只记录黄金持仓；如选择资金账户，保存时会同步扣减该现金账户余额
        </p>
        <CommonInput
          v-model="formWeight"
          label="持仓重量(克)"
          placeholder="请输入持仓重量"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="formBuyPrice"
          label="买入价格(元/克)"
          placeholder="请输入买入价格"
          input-type="number"
          input-mode="decimal"
        />
        <CommonSwitch v-model="formIncludeInNetWorth" label="是否计入总资产" />
        <CommonInput
          v-model="formRemark"
          label="备注"
          placeholder="例如：长期配置"
        />
        <p v-if="positionFormError" class="gold-position-form-error">
          {{ positionFormError }}
        </p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar gold-position-actions">
          <CommonButton
            variant="secondary"
            type="button"
            :disabled="isSavingPosition"
            @click="closePositionModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            type="button"
            :disabled="isSavingPosition"
            @click="savePosition"
          >
            {{ isSavingPosition ? '保存中...' : '确认' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :close-on-overlay="!isDeletingPosition"
      @close="closeDeleteConfirmModal"
    >
      <p class="gold-delete-message">
        确认删除“{{
          deletingHolding?.productName ??
          deletingHolding?.productSymbol ??
          '黄金持仓'
        }}”吗？
      </p>
      <p v-if="deleteError" class="gold-position-form-error">
        {{ deleteError }}
      </p>
      <template #footer>
        <div class="pc-toolbar gold-position-actions">
          <CommonButton
            variant="secondary"
            type="button"
            :disabled="isDeletingPosition"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            class="gold-position-button-danger"
            type="button"
            :disabled="isDeletingPosition"
            @click="confirmDeletePosition"
          >
            {{ isDeletingPosition ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showSellModal"
      :title="sellModalTitle"
      :close-on-overlay="!isSellingPosition"
      @close="closeSellModal"
    >
      <el-form
        class="gold-position-form"
        @submit.prevent="submitSellPosition"
        label-position="top"
      >
        <CommonSelect
          v-model="sellFundingAccountId"
          label="到账资金账户"
          :options="sellFundingAccountOptions"
          :disabled="isSellingPosition"
        />
        <CommonInput
          v-model="sellWeight"
          label="卖出克数(克)"
          placeholder="请输入卖出克数"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="sellPrice"
          label="卖出价格(元/克)"
          placeholder="请输入卖出价格"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="sellFee"
          label="手续费(元)"
          placeholder="请输入手续费"
          input-type="number"
          input-mode="decimal"
        />
        <p class="gold-position-form-hint">
          可卖持仓：{{ sellAvailableQuantityText }}，预计到账：{{
            sellAmountPreview
          }}
        </p>
        <CommonInput
          v-model="sellRemark"
          label="备注"
          placeholder="例如：止盈卖出"
        />
        <p v-if="sellError" class="gold-position-form-error">{{ sellError }}</p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar gold-position-actions">
          <CommonButton
            variant="secondary"
            type="button"
            :disabled="isSellingPosition"
            @click="closeSellModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            type="button"
            :disabled="isSellingPosition"
            @click="submitSellPosition"
          >
            {{ isSellingPosition ? '卖出中...' : '确认卖出' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped>
/* 黄金持仓卡：金色左侧饰条 + 克重大数字金色渐变（红涨绿跌收益） */
.gold-holding-card::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #e2b64f, #b8860b);
}

.gold-holding-price-tag {
  color: #8a6417;
  border-color: rgba(184, 134, 11, 0.35);
  background: rgba(226, 182, 79, 0.12);
}

.gold-holding-weight {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.gold-holding-weight-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.gold-holding-weight-value {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(120deg, #b8860b, #e2b64f);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.gold-holding-weight-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.gold-holding-profit {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.gold-holding-profit strong {
  font-size: 15px;
  font-weight: 600;
}

/* 中国市场惯例：盈利红、亏损绿 */
.gold-holding-profit.positive strong {
  color: var(--color-danger);
}

.gold-holding-profit.negative strong {
  color: var(--color-success);
}
</style>
