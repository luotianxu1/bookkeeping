<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

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
import { formatRate, formatSummaryWeight } from './goldPositionFormat'
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
</script>
<template>
  <section class="desktop-workspace">
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
      <DesktopTable :rows="filteredHoldings" title="黄金持仓" searchable>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column
          label="产品"
          prop="productName"
          min-width="180"
          show-overflow-tooltip
        />
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="克重" min-width="130">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatSummaryWeight(row.weight) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="成本均价" min-width="130">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatAmount(row.avgCostPrice) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="买入金额" min-width="150">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatAmount(row.purchaseAmount) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="收益" min-width="140">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatAmount(row.holdingProfit) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column
          label="买入时间"
          prop="createdAt"
          min-width="170"
          show-overflow-tooltip
        />
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }: { row: DetailTableRow }">
            <div class="pc-row-actions">
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
          </template>
        </el-table-column>
      </DesktopTable>
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
