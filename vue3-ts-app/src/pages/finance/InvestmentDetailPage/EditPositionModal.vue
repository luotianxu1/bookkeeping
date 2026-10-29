<script setup lang="ts">
// 修改持仓弹窗：价格、份额、成本、分红方式与备注编辑。
import { computed, ref } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  updateInvestmentPosition,
  type Account,
  type InvestmentAssetDetail,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import {
  buildFundingAccountOptions,
  dividendTypeOptions,
  formatEditableFundQuantity,
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
const editPrice = ref('')
const editHoldingQuantity = ref('')
const editCostPrice = ref('')
const editDividendType = ref<'cash' | 'reinvest'>('cash')
const editDividendFundingAccountId = ref('')
const editIncludeInNetWorth = ref(true)
const editRemark = ref('')
const editError = ref('')

const currentPosition = computed(() => props.detail.position)
const isPendingSubscription = computed(() => currentPosition.value?.subscriptionStatus === 'pending')
const isFundPosition = computed(() => (props.detail.productType || currentPosition.value?.productType) === 'fund')
const currentUnitName = computed(() => props.detail.unitName || props.detail.position.unitName || '份')
const fundingAccountOptions = computed<CommonSelectOption[]>(() => buildFundingAccountOptions(props.fundingAccounts))

function open() {
  const position = currentPosition.value
  if (!position) {
    return
  }
  editPrice.value = isPendingSubscription.value ? '' : String(Number(props.detail.latestPrice ?? position.currentPrice ?? 0) || '')
  editHoldingQuantity.value = isFundPosition.value
    ? formatEditableFundQuantity(position.holdingQuantity)
    : String(Number(position.holdingQuantity ?? 0) || '')
  editCostPrice.value = String(Number(position.avgCostPrice ?? 0) || '')
  editDividendType.value = position.dividendType === 'reinvest' ? 'reinvest' : 'cash'
  editDividendFundingAccountId.value = position.dividendFundingAccountId ? String(position.dividendFundingAccountId) : ''
  editIncludeInNetWorth.value = Boolean(position.includeInNetWorth)
  editRemark.value = position.remark || ''
  editError.value = ''
  visible.value = true
}

function close(force = false) {
  if (isSubmitting.value && !force) {
    return
  }
  visible.value = false
  editError.value = ''
}

async function submitEdit() {
  if (isSubmitting.value || !currentPosition.value) {
    return
  }
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    editError.value = '请先登录后再操作'
    return
  }
  const price = Number(editPrice.value)
  const holdingQuantity = Number(editHoldingQuantity.value)
  const costPrice = Number(editCostPrice.value)
  const frozenQuantity = Number(currentPosition.value.frozenQuantity ?? 0)
  const isEditingDirectPosition = !isPendingSubscription.value
  if (!isPendingSubscription.value && (!Number.isFinite(price) || price <= 0)) {
    editError.value = '请输入有效的当前价格'
    return
  }
  if (isEditingDirectPosition) {
    if (!Number.isFinite(holdingQuantity) || holdingQuantity <= 0) {
      editError.value = `请输入有效的当前${currentUnitName.value}`
      return
    }
    if (!Number.isFinite(costPrice) || costPrice < 0) {
      editError.value = '请输入有效的持仓成本价'
      return
    }
    if (holdingQuantity < frozenQuantity) {
      editError.value = '当前份额不能小于冻结份额'
      return
    }
  }
  const isEditingFundPosition = isFundPosition.value
  const dividendFundingAccountId = Number(editDividendFundingAccountId.value)
  if (isEditingFundPosition && editDividendType.value === 'cash'
    && (!Number.isFinite(dividendFundingAccountId) || dividendFundingAccountId <= 0)) {
    editError.value = '现金分红必须选择到账资金账户'
    return
  }

  isSubmitting.value = true
  editError.value = ''

  try {
    const nextHoldingQuantity = isEditingDirectPosition ? holdingQuantity : Number(currentPosition.value.holdingQuantity)
    const nextCostAmount = isEditingDirectPosition
      ? Number((holdingQuantity * costPrice).toFixed(2))
      : Number(currentPosition.value.costAmount)
    const nextAvailableQuantity = isEditingDirectPosition
      ? Number((holdingQuantity - frozenQuantity).toFixed(6))
      : Number(currentPosition.value.availableQuantity)
    await updateInvestmentPosition(currentPosition.value.id, {
      userId: currentUser.id,
      accountId: currentPosition.value.accountId,
      productId: currentPosition.value.productId,
      holdingQuantity: isEditingDirectPosition ? Number(nextHoldingQuantity.toFixed(6)) : nextHoldingQuantity,
      availableQuantity: nextAvailableQuantity,
      frozenQuantity,
      costAmount: nextCostAmount,
      currentPrice: isPendingSubscription.value ? undefined : price,
      fundingAccountId: isEditingFundPosition && editDividendType.value === 'cash'
        ? dividendFundingAccountId
        : undefined,
      dividendType: isEditingFundPosition ? editDividendType.value : undefined,
      includeInNetWorth: editIncludeInNetWorth.value,
      status: currentPosition.value.status,
      remark: editRemark.value.trim() || null,
    })
    close(true)
    emit('feedback', '修改成功', 'success')
    emit('saved')
  } catch (error) {
    const message = error instanceof Error ? error.message : '修改失败'
    editError.value = message
    emit('feedback', message, 'error')
  } finally {
    isSubmitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <CommonModal
    v-model="visible"
    title="修改持仓"
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

      <label v-if="!isPendingSubscription" class="investment-detail-modal-field">
        <span>当前价格</span>
        <input
          v-model="editPrice"
          class="investment-detail-field-control investment-detail-number-control"
          type="number"
          inputmode="decimal"
          placeholder="请输入当前价格"
        />
      </label>

      <div v-if="!isPendingSubscription" class="investment-detail-modal-row">
        <label class="investment-detail-modal-field">
          <span>当前{{ currentUnitName }}</span>
          <input
            v-model="editHoldingQuantity"
            class="investment-detail-field-control investment-detail-number-control"
            type="number"
            inputmode="decimal"
            step="0.01"
            placeholder="请输入当前份额"
          />
        </label>

        <label class="investment-detail-modal-field">
          <span>持仓成本价</span>
          <input
            v-model="editCostPrice"
            class="investment-detail-field-control investment-detail-number-control"
            type="number"
            inputmode="decimal"
            placeholder="请输入持仓成本价"
          />
        </label>
      </div>

      <label v-if="isFundPosition" class="investment-detail-modal-field">
        <span>分红方式</span>
        <SegmentedControl
          v-model="editDividendType"
          :options="dividendTypeOptions"
          label="基金分红方式"
        />
      </label>

      <CommonSelect
        v-if="isFundPosition && editDividendType === 'cash'"
        v-model="editDividendFundingAccountId"
        label="现金分红到账账户"
        :options="fundingAccountOptions"
      />

      <label class="investment-detail-switch-field">
        <span>计入总资产</span>
        <button
          type="button"
          :class="['investment-detail-switch', { active: editIncludeInNetWorth }]"
          @click="editIncludeInNetWorth = !editIncludeInNetWorth"
        >
          <span></span>
        </button>
      </label>

      <label class="investment-detail-modal-field">
        <span>备注</span>
        <textarea
          v-model="editRemark"
          class="investment-detail-textarea-control"
          rows="1"
          maxlength="200"
          placeholder="选填"
        ></textarea>
      </label>

      <p v-if="editError" class="investment-detail-modal-error">{{ editError }}</p>
    </div>

    <template #footer>
      <div class="investment-detail-modal-actions">
        <CommonButton variant="secondary" :disabled="isSubmitting" @click="close">
          取消
        </CommonButton>
        <CommonButton variant="primary" :disabled="isSubmitting" @click="submitEdit">
          {{ isSubmitting ? '保存中...' : '保存修改' }}
        </CommonButton>
      </div>
    </template>
  </CommonModal>
</template>
