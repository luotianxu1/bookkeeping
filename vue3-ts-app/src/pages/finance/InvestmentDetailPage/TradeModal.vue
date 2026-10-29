<script setup lang="ts">
// 加仓/减仓/修改待确认交易弹窗：表单状态与提交逻辑内聚在本组件。
import { computed, ref } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  createInvestmentTransaction,
  deleteInvestmentTransaction,
  updateInvestmentTransaction,
  type Account,
  type InvestmentAssetDetail,
  type InvestmentFundRedeemFeeOption,
  type InvestmentTransaction,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import {
  buildFundingAccountOptions,
  dividendTypeOptions,
  formatCurrency,
  formatNumber,
  formatPercentValue,
  formatTradeQuantityInput,
  getFundTransactionSubmitMessage,
  resolveTradeAtPayload,
  tradeModeOptions,
  tradeQuantityQuickFillPresets,
  tradeTimeSlotOptions,
  type TradeFundFeeMode,
} from './utils'

const props = defineProps<{
  detail: InvestmentAssetDetail
  fundingAccounts: Account[]
}>()

const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'feedback', message: string, type: 'success' | 'error'): void
}>()

const visible = defineModel<boolean>('visible', { default: false })

const isSubmitting = ref(false)
const editingTransaction = ref<InvestmentTransaction | null>(null)
const currentTradeAction = ref<'buy' | 'sell'>('buy')
const tradeInputMode = ref<'amount' | 'quantity'>('amount')
const tradeFundingAccountId = ref('')
const tradeDividendType = ref<'cash' | 'reinvest'>('cash')
const tradeAmount = ref('')
const tradeQuantity = ref('')
const tradePrice = ref('')
const tradeDate = ref('')
const tradeRemark = ref('')
const tradeError = ref('')
const tradeTimeSlot = ref<'before_1500' | 'after_1500'>('before_1500')
const tradeFundFeeMode = ref<TradeFundFeeMode>('auto')

const currentPosition = computed(() => props.detail.position)
const isPendingSubscription = computed(() => currentPosition.value?.subscriptionStatus === 'pending')
const isFundPosition = computed(() => (props.detail.productType || currentPosition.value?.productType) === 'fund')
const currentUnitName = computed(() => props.detail.unitName || props.detail.position.unitName || '份')
const tradeModalTitle = computed(() => editingTransaction.value
  ? '修改待确认交易'
  : currentTradeAction.value === 'buy' ? '加仓' : '减仓')
const tradeAmountLabel = computed(() => {
  if (isFundPosition.value) {
    return currentTradeAction.value === 'buy' ? '申购金额' : '回款金额'
  }
  return currentTradeAction.value === 'buy' ? '加仓金额' : '减仓金额'
})
const tradeAccountLabel = computed(() => currentTradeAction.value === 'buy' ? '资金账户' : '回款账户')
const fundRedeemFeeOptions = computed<InvestmentFundRedeemFeeOption[]>(() => props.detail.fundRedeemFeeOptions ?? [])
const showFundRedeemFeeSelector = computed(() =>
  isFundPosition.value && currentTradeAction.value === 'sell' && fundRedeemFeeOptions.value.length > 0,
)
const selectedTradeFundFeeOption = computed<InvestmentFundRedeemFeeOption | null>(() => {
  if (tradeFundFeeMode.value === 'auto') {
    return null
  }
  const index = Number(tradeFundFeeMode.value.replace('manual-', ''))
  return Number.isInteger(index) && index >= 0 ? fundRedeemFeeOptions.value[index] ?? null : null
})
const tradeFundFeeSelectOptions = computed<CommonSelectOption[]>(() => [
  { label: '自动计算（推荐）', value: 'auto' as const },
  ...fundRedeemFeeOptions.value.map((option, index) => ({
    label: `${option.label}（${formatPercentValue(Number(option.feeRate))}）`,
    value: `manual-${index}`,
  })),
])
const tradeFundFeeSelectValue = computed({
  get: () => tradeFundFeeMode.value,
  set: (value: string) => {
    tradeFundFeeMode.value = value === 'auto' || value.startsWith('manual-')
      ? value as TradeFundFeeMode
      : 'auto'
  },
})
const showTradeInputMode = computed(() => currentTradeAction.value === 'buy' && !isFundPosition.value)
const tradePrimaryLabel = computed(() => {
  if (isFundPosition.value) {
    return currentTradeAction.value === 'buy' ? '申购金额' : '赎回份额'
  }
  if (currentTradeAction.value === 'buy') {
    return tradeInputMode.value === 'amount' ? '加仓金额' : '加仓份额'
  }
  return '减仓金额'
})
const tradePrimaryPlaceholder = computed(() => {
  if (isFundPosition.value) {
    return currentTradeAction.value === 'buy' ? '请输入申购金额' : '请输入赎回份额'
  }
  if (currentTradeAction.value === 'buy') {
    return tradeInputMode.value === 'amount' ? '请输入金额' : '请输入份额'
  }
  return '请输入金额'
})
const fundingAccountOptions = computed<CommonSelectOption[]>(() => buildFundingAccountOptions(props.fundingAccounts))
const tradeModeValue = computed({
  get: () => tradeInputMode.value === 'quantity' ? '按份额和净值' : '按金额',
  set: (value: string) => {
    tradeInputMode.value = value === '按份额和净值' ? 'quantity' : 'amount'
  },
})
const showTradePriceField = computed(() => !isFundPosition.value)
const showTradeQuantityPreview = computed(() => !isFundPosition.value)
const showTradeAmountPreview = computed(() => !isFundPosition.value && currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity')
const showTradeQuantityQuickTabs = computed(() => isFundPosition.value && currentTradeAction.value === 'sell')
const tradeQuantityQuickFillTabs = computed(() => {
  const availableQuantity = Number(currentPosition.value?.availableQuantity ?? 0)
  if (!Number.isFinite(availableQuantity) || availableQuantity <= 0) {
    return []
  }
  return tradeQuantityQuickFillPresets.map((preset) => ({
    label: preset.label,
    value: formatTradeQuantityInput(availableQuantity * preset.ratio),
  })).filter((item) => item.value)
})
const activeTradeQuantityQuickTab = computed(() => {
  const currentQuantity = Number(tradeQuantity.value)
  if (!Number.isFinite(currentQuantity) || currentQuantity <= 0) {
    return ''
  }
  const matchedTab = tradeQuantityQuickFillTabs.value.find((item) => {
    const tabQuantity = Number(item.value)
    return Number.isFinite(tabQuantity) && Math.abs(tabQuantity - currentQuantity) < 0.000001
  })
  return matchedTab?.label ?? ''
})
const tradeQuantityPreview = computed(() => {
  const amount = getTradeAmountValue()
  const quantity = getTradeQuantityValue()
  if (!Number.isFinite(quantity) || quantity <= 0) {
    return '--'
  }
  if (currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity') {
    return `${formatNumber(quantity, 2)} ${currentUnitName.value}`
  }
  const price = Number(tradePrice.value)
  if (!Number.isFinite(amount) || !Number.isFinite(price) || amount <= 0 || price <= 0) {
    return '--'
  }
  return `${formatNumber(quantity, 2)} ${currentUnitName.value}`
})
const tradeAmountPreview = computed(() => {
  const amount = getTradeAmountValue()
  if (!Number.isFinite(amount) || amount <= 0) {
    return '--'
  }
  return formatCurrency(amount)
})
const tradeFundFeeAmountPreview = computed(() => {
  if (!showFundRedeemFeeSelector.value) {
    return '--'
  }
  if (tradeFundFeeMode.value === 'auto') {
    return '按持仓批次自动计算'
  }
  const amount = getTradeFundSellFeeAmountValue()
  return Number.isFinite(amount) && amount >= 0 ? formatCurrency(amount) : '--'
})
const tradeFundNetAmountPreview = computed(() => {
  if (!showFundRedeemFeeSelector.value) {
    return '--'
  }
  const amount = getTradeAmountValue()
  if (!Number.isFinite(amount) || amount <= 0) {
    return '--'
  }
  if (tradeFundFeeMode.value === 'auto') {
    return '以提交结果为准'
  }
  const feeAmount = getTradeFundSellFeeAmountValue()
  return Number.isFinite(feeAmount) ? formatCurrency(amount - feeAmount) : '--'
})

function getTradeAmountValue() {
  if (isFundPosition.value) {
    if (currentTradeAction.value === 'buy') {
      return Number(tradeAmount.value)
    }
    const quantity = Number(tradeQuantity.value)
    const price = Number(props.detail.latestPrice ?? currentPosition.value?.currentPrice ?? 0)
    return Number.isFinite(quantity) && quantity > 0 && Number.isFinite(price) && price > 0 ? quantity * price : NaN
  }
  if (currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity') {
    const quantity = Number(tradeQuantity.value)
    const price = Number(tradePrice.value)
    return quantity > 0 && price > 0 ? quantity * price : NaN
  }
  return Number(tradeAmount.value)
}

function getTradeFundSellFeeAmountValue() {
  if (!isFundPosition.value || currentTradeAction.value !== 'sell') {
    return NaN
  }
  const option = selectedTradeFundFeeOption.value
  const amount = getTradeAmountValue()
  if (!option || !Number.isFinite(amount) || amount <= 0) {
    return NaN
  }
  const feeRate = Number(option.feeRate)
  if (!Number.isFinite(feeRate) || feeRate < 0) {
    return NaN
  }
  return Number((amount * feeRate).toFixed(2))
}

function getTradeFundSellFeeAmountPayload() {
  if (!isFundPosition.value || currentTradeAction.value !== 'sell' || tradeFundFeeMode.value === 'auto') {
    return undefined
  }
  const feeAmount = getTradeFundSellFeeAmountValue()
  return Number.isFinite(feeAmount) && feeAmount >= 0 ? feeAmount : undefined
}

function getTradeQuantityValue() {
  if (isFundPosition.value) {
    if (currentTradeAction.value === 'sell') {
      return Number(tradeQuantity.value)
    }
    return NaN
  }
  if (currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity') {
    return Number(tradeQuantity.value)
  }
  const amount = Number(tradeAmount.value)
  const price = Number(tradePrice.value)
  if (!Number.isFinite(amount) || !Number.isFinite(price) || amount <= 0 || price <= 0) {
    return NaN
  }
  return amount / price
}

function formatDateInput(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function open(action: 'buy' | 'sell') {
  const position = currentPosition.value
  if (!position || (isPendingSubscription.value && action === 'sell')) {
    return
  }
  currentTradeAction.value = action
  editingTransaction.value = null
  tradeInputMode.value = 'amount'
  tradeTimeSlot.value = 'before_1500'
  tradeDividendType.value = position.dividendType === 'reinvest' ? 'reinvest' : 'cash'
  const preferredFundingAccountId = position.dividendFundingAccountId
    ?? props.fundingAccounts[0]?.id
  tradeFundingAccountId.value = preferredFundingAccountId ? String(preferredFundingAccountId) : ''
  tradeAmount.value = ''
  tradeQuantity.value = ''
  tradePrice.value = isFundPosition.value ? '' : String(Number(props.detail.latestPrice ?? position.currentPrice ?? 0) || '')
  tradeDate.value = formatDateInput(new Date())
  tradeRemark.value = ''
  tradeFundFeeMode.value = 'auto'
  tradeError.value = ''
  visible.value = true
}

function openPending(entry: InvestmentTransaction) {
  if (entry.id <= 0 || entry.settlementStatus !== 'pending' || !currentPosition.value) return
  editingTransaction.value = entry
  currentTradeAction.value = entry.tradeType === 'sell' ? 'sell' : 'buy'
  tradeInputMode.value = 'amount'
  tradeDividendType.value = currentPosition.value.dividendType === 'reinvest' ? 'reinvest' : 'cash'
  tradeFundingAccountId.value = entry.fundingAccountId
    ? String(entry.fundingAccountId)
    : (currentPosition.value.dividendFundingAccountId
      ? String(currentPosition.value.dividendFundingAccountId)
      : (props.fundingAccounts[0] ? String(props.fundingAccounts[0].id) : ''))
  tradeAmount.value = currentTradeAction.value === 'buy' ? String(Number(entry.amount) || '') : ''
  tradeQuantity.value = currentTradeAction.value === 'sell' ? String(Number(entry.quantity) || '') : ''
  tradePrice.value = ''
  tradeDate.value = String(entry.tradeAt || '').slice(0, 10)
  tradeTimeSlot.value = entry.settlementAppliedDate && String(entry.tradeAt).slice(11, 16) >= '15:00' ? 'after_1500' : 'before_1500'
  tradeRemark.value = entry.remark || ''
  tradeFundFeeMode.value = 'auto'
  tradeError.value = ''
  visible.value = true
}

async function removePendingTransaction() {
  const entry = editingTransaction.value
  const currentUser = getStoredCurrentUser()
  if (!entry || !currentUser || isSubmitting.value) return
  if (!window.confirm('确认删除这条待确认交易吗？删除后会退回资金或解除冻结份额。')) return
  isSubmitting.value = true
  try {
    await deleteInvestmentTransaction(entry.id, currentUser.id)
    close(true)
    emit('feedback', '待确认交易已删除', 'success')
    emit('saved')
  } catch (error) {
    emit('feedback', error instanceof Error ? error.message : '删除交易失败', 'error')
  } finally {
    isSubmitting.value = false
  }
}

function close(force = false) {
  if (isSubmitting.value && !force) {
    return
  }
  visible.value = false
  tradeError.value = ''
}

function applyTradeQuantityQuickFill(value: string) {
  tradeQuantity.value = value
  tradeError.value = ''
}

async function submitTrade() {
  if (isSubmitting.value || !currentPosition.value || !props.detail) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    tradeError.value = '请先登录后再操作'
    return
  }

  const fundingAccountId = Number(tradeFundingAccountId.value)
  const amount = getTradeAmountValue()
  const price = Number(tradePrice.value)
  const quantity = getTradeQuantityValue()
  const resolvedTradeAt = resolveTradeAtPayload(tradeDate.value, isFundPosition.value ? tradeTimeSlot.value : undefined)

  if (!Number.isFinite(fundingAccountId) || fundingAccountId <= 0) {
    tradeError.value = '请选择资金账户'
    return
  }
  if (!resolvedTradeAt) {
    tradeError.value = '请选择有效的交易日期'
    return
  }
  if (isFundPosition.value) {
    if (currentTradeAction.value === 'buy') {
      if (!Number.isFinite(amount) || amount <= 0) {
        tradeError.value = '请输入有效的申购金额'
        return
      }
    } else {
      if (!Number.isFinite(quantity) || quantity <= 0) {
        tradeError.value = '请输入有效的赎回份额'
        return
      }
      if (quantity > Number(currentPosition.value.availableQuantity)) {
        tradeError.value = '赎回份额不能超过可用持仓'
        return
      }
    }

    isSubmitting.value = true
    tradeError.value = ''

    try {
      const payload = {
        userId: currentUser.id,
        accountId: currentPosition.value.accountId,
        positionId: currentPosition.value.id,
        productId: currentPosition.value.productId,
        tradeType: currentTradeAction.value,
        quantity: currentTradeAction.value === 'sell' ? Number(quantity.toFixed(6)) : 0,
        price: null,
        amount: Number((currentTradeAction.value === 'buy' ? amount : getTradeAmountValue()).toFixed(2)),
        feeAmount: currentTradeAction.value === 'sell'
          ? getTradeFundSellFeeAmountPayload()
          : (editingTransaction.value ? Number(editingTransaction.value.feeAmount ?? 0) : 0),
        taxAmount: editingTransaction.value ? Number(editingTransaction.value.taxAmount ?? 0) : 0,
        currencyCode: currentPosition.value.currencyCode || 'CNY',
        tradeAt: resolvedTradeAt,
        fundingAccountId,
        subscriptionTimeSlot: tradeTimeSlot.value,
        dividendType: currentTradeAction.value === 'buy' ? tradeDividendType.value : undefined,
        remark: tradeRemark.value.trim() || null,
      }
      const transaction = editingTransaction.value
        ? await updateInvestmentTransaction(editingTransaction.value.id, payload)
        : await createInvestmentTransaction(payload)
      close(true)
      emit('feedback', editingTransaction.value ? '待确认交易已更新' : getFundTransactionSubmitMessage(transaction), 'success')
      emit('saved')
    } catch (error) {
      const message = error instanceof Error ? error.message : `${tradeModalTitle.value}失败`
      tradeError.value = message
      emit('feedback', message, 'error')
    } finally {
      isSubmitting.value = false
    }
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    tradeError.value = currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity'
      ? '请输入有效的加仓份额'
      : `请输入有效的${tradeAmountLabel.value}`
    return
  }
  if (!Number.isFinite(price) || price <= 0) {
    tradeError.value = '请输入有效的成交价格'
    return
  }
  if (!Number.isFinite(quantity) || quantity <= 0) {
    tradeError.value = '金额或价格不正确'
    return
  }
  if (currentTradeAction.value === 'sell' && quantity > Number(currentPosition.value.availableQuantity)) {
    tradeError.value = '减仓数量不能超过可用持仓'
    return
  }

  isSubmitting.value = true
  tradeError.value = ''

  try {
    await createInvestmentTransaction({
      userId: currentUser.id,
      accountId: currentPosition.value.accountId,
      positionId: currentPosition.value.id,
      productId: currentPosition.value.productId,
      tradeType: currentTradeAction.value,
      quantity: Number(quantity.toFixed(6)),
      price,
      amount: Number(amount.toFixed(2)),
      feeAmount: 0,
      taxAmount: 0,
      currencyCode: currentPosition.value.currencyCode || 'CNY',
      tradeAt: resolvedTradeAt,
      fundingAccountId,
      remark: tradeRemark.value.trim() || null,
    })
    close(true)
    emit('feedback', currentTradeAction.value === 'buy' ? '加仓成功' : '减仓成功', 'success')
    emit('saved')
  } catch (error) {
    const message = error instanceof Error ? error.message : `${tradeModalTitle.value}失败`
    tradeError.value = message
    emit('feedback', message, 'error')
  } finally {
    isSubmitting.value = false
  }
}

defineExpose({ open, openPending })
</script>

<template>
  <CommonModal
    v-model="visible"
    :title="tradeModalTitle"
    size="compact"
    :close-on-overlay="!isSubmitting"
    @close="close"
  >
    <div class="investment-detail-modal-form">
      <label class="investment-detail-modal-field">
        <span>资产名称</span>
        <input
          class="investment-detail-field-control"
          :value="detail.name || detail.position.productName || ''"
          type="text"
          readonly
        />
      </label>

      <SegmentedControl
        v-if="showTradeInputMode"
        v-model="tradeModeValue"
        :options="tradeModeOptions"
        label="加仓方式切换"
      />

      <div :class="['investment-detail-modal-row', { 'single-column': isFundPosition }]">
        <label class="investment-detail-modal-field">
          <span>{{ tradePrimaryLabel }}</span>
          <input
            v-if="isFundPosition ? currentTradeAction === 'buy' : currentTradeAction !== 'buy' || tradeInputMode === 'amount'"
            v-model="tradeAmount"
            class="investment-detail-field-control"
            type="number"
            inputmode="decimal"
            :placeholder="tradePrimaryPlaceholder"
          />
          <input
            v-else
            v-model="tradeQuantity"
            class="investment-detail-field-control"
            type="number"
            inputmode="decimal"
            :placeholder="tradePrimaryPlaceholder"
          />
          <div v-if="showTradeQuantityQuickTabs" class="investment-detail-quick-tabs" aria-label="赎回份额快捷填充">
            <button
              v-for="item in tradeQuantityQuickFillTabs"
              :key="item.label"
              :class="['investment-detail-quick-tab', { active: activeTradeQuantityQuickTab === item.label }]"
              type="button"
              @click="applyTradeQuantityQuickFill(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </label>

        <label v-if="showTradePriceField" class="investment-detail-modal-field">
          <span>成交价格</span>
          <input
            v-model="tradePrice"
            class="investment-detail-field-control"
            type="number"
            inputmode="decimal"
            placeholder="请输入价格"
          />
        </label>
      </div>

      <CommonSelect
        v-model="tradeFundingAccountId"
        :label="isFundPosition && currentTradeAction === 'buy' && tradeDividendType === 'cash' ? '申购及现金分红到账账户' : tradeAccountLabel"
        :options="fundingAccountOptions"
      />

      <label v-if="isFundPosition && currentTradeAction === 'buy'" class="investment-detail-modal-field">
        <span>分红方式</span>
        <SegmentedControl
          v-model="tradeDividendType"
          :options="dividendTypeOptions"
          label="基金分红方式"
        />
      </label>

      <label class="investment-detail-modal-field">
        <span>交易日期</span>
        <input
          v-model="tradeDate"
          class="investment-detail-field-control"
          type="date"
        />
      </label>

      <label v-if="showTradeQuantityPreview" class="investment-detail-modal-field">
        <span>数量</span>
        <input class="investment-detail-field-control" :value="tradeQuantityPreview" type="text" readonly />
      </label>

      <label v-if="showTradeAmountPreview" class="investment-detail-modal-field">
        <span>金额</span>
        <input class="investment-detail-field-control" :value="tradeAmountPreview" type="text" readonly />
      </label>

      <label v-if="isFundPosition" class="investment-detail-modal-field">
        <span>{{ currentTradeAction === 'buy' ? '申购时点' : '赎回时点' }}</span>
        <SegmentedControl
          v-model="tradeTimeSlot"
          :options="tradeTimeSlotOptions"
          label="基金交易时点"
        />
      </label>

      <CommonSelect
        v-if="showFundRedeemFeeSelector"
        v-model="tradeFundFeeSelectValue"
        label="手续费档位"
        :options="tradeFundFeeSelectOptions"
      />

      <label v-if="showFundRedeemFeeSelector" class="investment-detail-modal-field">
        <span>预计手续费</span>
        <input class="investment-detail-field-control" :value="tradeFundFeeAmountPreview" type="text" readonly />
      </label>

      <label v-if="showFundRedeemFeeSelector" class="investment-detail-modal-field">
        <span>预计到账</span>
        <input class="investment-detail-field-control" :value="tradeFundNetAmountPreview" type="text" readonly />
      </label>

      <label class="investment-detail-modal-field">
        <span>备注</span>
        <textarea
          v-model="tradeRemark"
          class="investment-detail-textarea-control"
          rows="1"
          maxlength="200"
          placeholder="选填"
        ></textarea>
      </label>

      <p v-if="tradeError" class="investment-detail-modal-error">{{ tradeError }}</p>
    </div>

    <template #footer>
      <div class="investment-detail-modal-actions">
        <button v-if="editingTransaction" type="button" class="investment-detail-transaction-delete" :disabled="isSubmitting" @click="removePendingTransaction">删除</button>
        <CommonButton variant="secondary" :disabled="isSubmitting" @click="close">
          取消
        </CommonButton>
        <CommonButton variant="primary" :disabled="isSubmitting" @click="submitTrade">
          {{ isSubmitting ? '提交中...' : editingTransaction ? '保存修改' : tradeModalTitle }}
        </CommonButton>
      </div>
    </template>
  </CommonModal>
</template>
