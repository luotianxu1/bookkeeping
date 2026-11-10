<script setup lang="ts">
import { formatAmount } from '@/business/utils/format'
// 黄金账户持仓页：对接黄金聚合查询接口，并补充持仓新增、修改、删除能力。
import { onMounted } from 'vue'
import AccountIcon from '@/business/components/common/AccountIcon/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/business/components/common/CommonHeaderActionButton/index.vue'
import CommonHeaderRefreshButton from '@/business/components/common/CommonHeaderRefreshButton/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { useFeedback } from '@/business/composables/useFeedback'
import { allAccountIconOptions } from '@/business/data/account-icons'
import {
  formatAveragePriceTag,
  formatCreatedDate,
  formatHoldingMeta,
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
</script>

<template>
  <section class="pc-page gold-position-page" aria-label="黄金账户持仓">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <PageHeader
      title="黄金账户持仓"
      back-to="/finance/accounts/gold"
      back-label="返回黄金账户"
    >
      <template #right>
        <div class="pc-toolbar gold-position-header-actions">
          <CommonHeaderActionButton
            label="清仓明细"
            @click="openLiquidationList"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M8 6H21"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
              <path
                d="M8 12H21"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
              <path
                d="M8 18H21"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
              <path
                d="M3.5 6H4.5"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
              />
              <path
                d="M3.5 12H4.5"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
              />
              <path
                d="M3.5 18H4.5"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
              />
            </svg>
          </CommonHeaderActionButton>
          <CommonHeaderRefreshButton
            label="刷新黄金信息"
            :loading="isRefreshingGold"
            @click="refreshGoldData"
          />
          <CommonHeaderActionButton
            v-if="scopedAccount"
            label="修改黄金账户"
            @click="openAccountSettingsModal"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z"
                stroke="currentColor"
                stroke-width="1.8"
              />
              <path
                d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </CommonHeaderActionButton>
        </div>
      </template>
    </PageHeader>
    <p
      v-if="pageError"
      class="gold-position-message gold-position-message-error"
    >
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />
    <template v-else>
      <section class="gold-position-summary">
        <div class="summary-head">
          <div class="summary-head-main">
            <div v-if="scopedAccount" class="summary-account-identity">
              <AccountIcon
                :icon="scopedAccount.icon"
                :account-type-code="scopedAccount.accountTypeCode"
                :color="scopedAccount.color"
                :name="scopedAccount.name"
                :size="28"
              />
              <div class="summary-account-copy">
                <strong>{{ scopedAccount.name }}</strong>
              </div>
            </div>
            <div class="summary-main">
              <strong class="summary-weight-value">
                {{ formatSummaryWeight(visibleSummary.totalWeight) }}
              </strong>
            </div>
          </div>
          <div class="gold-price-chip" aria-label="当前实时金价">
            <span class="gold-price-chip-label">实时金价</span>
            <strong class="gold-price-chip-value">
              {{
                realtimeGoldPrice > 0
                  ? `${formatAmount(realtimeGoldPrice)} 元/克`
                  : '--'
              }}
            </strong>
            <span class="gold-price-chip-time">
              {{ realtimeGoldUpdatedAt }}
            </span>
          </div>
        </div>
        <div class="pc-grid summary-grid">
          <el-card shadow="never" class="pc-record-card">
            <span>平均克价(元)</span>
            <AmountText
              tag="strong"
              class="summary-static-amount"
              tone="inherit"
              :value="formatAmount(visibleSummary.averagePrice)"
            />
          </el-card>
          <el-card shadow="never" class="pc-record-card">
            <span>购入总价(元)</span>
            <AmountText
              tag="strong"
              class="summary-static-amount"
              tone="inherit"
              :value="formatAmount(visibleSummary.purchaseTotal)"
            />
          </el-card>
          <el-card shadow="never" class="pc-record-card">
            <span>预估价值(元)</span>
            <AmountText
              tag="strong"
              class="summary-static-amount"
              tone="inherit"
              :value="formatAmount(visibleSummary.estimatedValue)"
            />
          </el-card>
          <el-card shadow="never" class="pc-record-card">
            <span>预估收益(元)</span>
            <AmountText
              tag="strong"
              :value="formatAmount(visibleSummary.estimatedProfit)"
            />
          </el-card>
          <el-card shadow="never" class="pc-record-card">
            <span>收益率(%)</span>
            <AmountText
              tag="strong"
              :value="formatRate(visibleSummary.profitRate)"
            />
          </el-card>
          <el-card shadow="never" class="pc-record-card">
            <span>累计收益(元)</span>
            <AmountText
              tag="strong"
              :value="formatAmount(visibleSummary.cumulativeProfit)"
            />
          </el-card>
        </div>
      </section>
      <p class="position-section-title">持仓明细</p>
      <section class="pc-grid position-list">
        <template v-if="hasHoldings">
          <el-table :data="filteredHoldings" stripe>
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
          </el-table>
        </template>
        <p v-else class="gold-position-empty">
          {{ isScopedToAccount ? '该黄金账户暂无持仓' : '暂无黄金持仓' }}
        </p>
      </section>
    </template>
    <FloatingAddButton
      aria-label="新增黄金持仓"
      storage-key="gold-position"
      @click="openCreateModal"
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
