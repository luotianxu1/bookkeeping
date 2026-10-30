<script setup lang="ts">
// 定投计划新增/修改弹窗。
import { computed, ref } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  createInvestmentAutoInvestPlan,
  updateInvestmentAutoInvestPlan,
  type Account,
  type InvestmentAutoInvestFrequency,
  type InvestmentAutoInvestPlan,
  type InvestmentPosition,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import {
  autoInvestFrequencyOptions,
  buildFundingAccountOptions,
} from './utils'

const props = defineProps<{
  position: InvestmentPosition
  assetName: string
  plans: InvestmentAutoInvestPlan[]
  fundingAccounts: Account[]
}>()

const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'feedback', message: string, type: 'success' | 'error'): void
}>()

const visible = defineModel<boolean>('visible', { default: false })

const isSaving = ref(false)
const editingAutoInvestPlanId = ref<number | null>(null)
const autoInvestFundingAccountId = ref('')
const autoInvestFrequency = ref<InvestmentAutoInvestFrequency>('daily')
const autoInvestAmount = ref('')
const autoInvestNextExecuteDate = ref('')
const autoInvestRemark = ref('')
const autoInvestError = ref('')

const fundingAccountOptions = computed<CommonSelectOption[]>(() => buildFundingAccountOptions(props.fundingAccounts))

function formatDateInput(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function resetAutoInvestForm() {
  autoInvestFundingAccountId.value = props.fundingAccounts[0] ? String(props.fundingAccounts[0].id) : ''
  autoInvestFrequency.value = 'daily'
  autoInvestAmount.value = ''
  autoInvestNextExecuteDate.value = formatDateInput(new Date())
  autoInvestRemark.value = ''
  autoInvestError.value = ''
}

function open(plan?: InvestmentAutoInvestPlan) {
  autoInvestError.value = ''
  if (!plan) {
    editingAutoInvestPlanId.value = null
    resetAutoInvestForm()
  } else {
    editingAutoInvestPlanId.value = plan.id
    autoInvestFundingAccountId.value = String(plan.fundingAccountId)
    autoInvestFrequency.value = plan.frequency === 'monthly'
      ? 'monthly'
      : plan.frequency === 'weekly'
        ? 'weekly'
        : 'daily'
    autoInvestAmount.value = String(Number(plan.amount) || '')
    autoInvestNextExecuteDate.value = plan.nextExecuteDate
    autoInvestRemark.value = plan.remark || ''
  }
  visible.value = true
}

function close(force = false) {
  if (isSaving.value && !force) {
    return
  }
  visible.value = false
  autoInvestError.value = ''
}

async function saveAutoInvestPlan() {
  if (isSaving.value || !props.position) {
    return
  }
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    autoInvestError.value = '请先登录后再操作'
    return
  }
  const fundingAccountId = Number(autoInvestFundingAccountId.value)
  const amount = Number(autoInvestAmount.value)
  if (!Number.isFinite(fundingAccountId) || fundingAccountId <= 0) {
    autoInvestError.value = '请选择资金账户'
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    autoInvestError.value = '请输入有效的定投金额'
    return
  }
  if (!autoInvestNextExecuteDate.value) {
    autoInvestError.value = '请选择下次执行日期'
    return
  }

  isSaving.value = true
  autoInvestError.value = ''

  try {
    const existingPlan = editingAutoInvestPlanId.value
      ? props.plans.find((item) => item.id === editingAutoInvestPlanId.value) ?? null
      : null
    const payload = {
      userId: currentUser.id,
      accountId: props.position.accountId,
      positionId: props.position.id,
      fundingAccountId,
      frequency: autoInvestFrequency.value,
      amount: Number(amount.toFixed(2)),
      currencyCode: props.position.currencyCode || 'CNY',
      nextExecuteDate: autoInvestNextExecuteDate.value,
      status: existingPlan?.status === 'paused' ? 'paused' as const : existingPlan?.status === 'cancelled' ? 'cancelled' as const : 'active' as const,
      remark: autoInvestRemark.value.trim() || null,
    }
    if (editingAutoInvestPlanId.value) {
      await updateInvestmentAutoInvestPlan(editingAutoInvestPlanId.value, payload)
    } else {
      await createInvestmentAutoInvestPlan(payload)
    }
    close(true)
    emit('feedback', editingAutoInvestPlanId.value ? '定投计划已更新' : '定投计划已创建', 'success')
    emit('saved')
  } catch (error) {
    const message = error instanceof Error ? error.message : '定投计划保存失败'
    autoInvestError.value = message
    emit('feedback', message, 'error')
  } finally {
    isSaving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <CommonModal
    v-model="visible"
    :title="editingAutoInvestPlanId ? '修改定投计划' : '新增定投计划'"
    size="compact"
    :close-on-overlay="!isSaving"
    @close="close"
  >
    <div class="investment-detail-modal-form">
      <label class="investment-detail-modal-field">
        <span>资产名称</span>
        <input
          class="investment-detail-field-control"
          :value="assetName || position.productName || ''"
          type="text"
          readonly
        />
      </label>

      <CommonSelect
        v-model="autoInvestFundingAccountId"
        label="扣款账户"
        :options="fundingAccountOptions"
      />

      <label class="investment-detail-modal-field">
        <span>定投周期</span>
        <SegmentedControl
          v-model="autoInvestFrequency"
          :options="autoInvestFrequencyOptions"
          label="定投周期"
        />
      </label>

      <div class="investment-detail-modal-row">
        <label class="investment-detail-modal-field">
          <span>定投金额</span>
          <input
            v-model="autoInvestAmount"
            class="investment-detail-field-control"
            type="number"
            inputmode="decimal"
            placeholder="请输入定投金额"
          />
        </label>

        <label class="investment-detail-modal-field">
          <span>下次执行日</span>
          <input
            v-model="autoInvestNextExecuteDate"
            class="investment-detail-field-control"
            type="date"
          />
        </label>
      </div>

      <label class="investment-detail-modal-field">
        <span>备注</span>
        <textarea
          v-model="autoInvestRemark"
          class="investment-detail-textarea-control"
          rows="1"
          maxlength="200"
          placeholder="选填"
        ></textarea>
      </label>

      <p class="investment-detail-description">
        到期后系统会自动按计划金额生成一笔基金申购，默认按 15 点前规则提交。
      </p>

      <p v-if="autoInvestError" class="investment-detail-modal-error">{{ autoInvestError }}</p>
    </div>

    <template #footer>
      <div class="investment-detail-modal-actions">
        <CommonButton variant="secondary" :disabled="isSaving" @click="close">
          取消
        </CommonButton>
        <CommonButton variant="primary" :disabled="isSaving" @click="saveAutoInvestPlan">
          {{ isSaving ? '保存中...' : '保存计划' }}
        </CommonButton>
      </div>
    </template>
  </CommonModal>
</template>
