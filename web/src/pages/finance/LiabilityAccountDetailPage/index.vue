<script setup lang="ts">
import { computed } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import { useFeedback } from '@/composables/useFeedback'
import {
  formatCurrency,
  formatDate,
  formatInstallmentLabel,
  formatRecordAmount,
  formatRemainingLabel,
  formatRepaymentStatus,
} from './liabilityFormat'
import { formatRate } from './liabilityLoanPlan'
import { useLiabilityLoanPlan } from './useLiabilityLoanPlan'
import { useLiabilityDetailData } from './useLiabilityDetailData'
import { useLiabilityRecordActions } from './useLiabilityRecordActions'

const {
  account,
  summary,
  records,
  isLoading,
  pageError,
  loadDetail,
} = useLiabilityDetailData()
const { showFeedbackModal, feedbackMessage, feedbackType, showFeedback } = useFeedback()

const {
  hasLoanPlan,
  isSettled,
  totalRepaymentAmount,
  paidAmount,
  monthlyPaymentAmount,
  remainingInstallmentPeriods,
} = useLiabilityLoanPlan({ account, records, summary })

const {
  showRecordModal,
  showDeleteModal,
  showAccountDeleteModal,
  showRepayModal,
  showPrepayModal,
  isSavingRecord,
  isDeleting,
  isDeletingAccount,
  isRepaying,
  isPrepaying,
  recordFormError,
  deleteError,
  accountDeleteError,
  repayError,
  prepayError,
  editingRecord,
  repayingRecord,
  recordAmount,
  recordInstallmentTotalPeriods,
  recordInstallmentCurrentPeriod,
  recordOccurredAt,
  recordRemark,
  repayPaidAt,
  prepayPaidAt,
  prepayRemark,
  recordModalTitle,
  nextInstallmentPreview,
  nextDueText,
  openAddRecordModal,
  openEditRecordModal,
  closeRecordModal,
  openDeleteModal,
  closeDeleteModal,
  confirmDelete,
  openRepayModal,
  closeRepayModal,
  confirmRepay,
  openPrepayModal,
  closePrepayModal,
  confirmPrepay,
  openAccountDeleteModal,
  closeAccountDeleteModal,
  confirmDeleteAccount,
  saveRecord,
} = useLiabilityRecordActions({
  account,
  records,
  hasLoanPlan,
  isSettled,
  totalRepaymentAmount,
  loadDetail,
  showFeedback,
})

const detailName = computed(() => account.value?.name || '负债账户')
const detailNote = computed(() => account.value?.remark?.trim() || '')
const summaryAmountText = computed(() => formatCurrency(summary.value.totalAmount))
const loanInterestRateText = computed(() => formatRate(account.value))
</script>

<template>
  <section class="debt-account-detail-page" aria-label="负债详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="debt-detail-header">
      <PageHeader :title="`${detailName}账单`" back-to="/finance/accounts/liability" back-label="返回负债账户">
        <template #right>
          <button
            type="button"
            class="debt-detail-delete-button"
            :disabled="isDeletingAccount || !account"
            @click="openAccountDeleteModal"
          >
            删除账户
          </button>
        </template>
      </PageHeader>
    </header>

    <p v-if="pageError" class="debt-detail-message debt-detail-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="debt-detail-summary-card" aria-label="负债详情总览">
        <div class="debt-detail-summary-top">
          <div class="debt-detail-summary-title">
            <strong>贷款合同</strong>
            <span>{{ detailName }} · 每月 {{ account?.loanRepaymentDay ?? '--' }} 日还款</span>
          </div>
          <span class="debt-detail-summary-badge">{{ isSettled ? '已结清' : '还款中' }}</span>
        </div>

        <AmountText tag="p" class="debt-detail-summary-amount" :value="summaryAmountText" tone="inherit" />
        <p class="debt-detail-summary-sub">下期账单 {{ nextDueText }}</p>
        <p v-if="detailNote" class="debt-detail-summary-note">{{ detailNote }}</p>

        <div class="debt-detail-metrics">
          <div class="debt-detail-metric">
            <span>合同总额</span>
            <strong>{{ formatCurrency(totalRepaymentAmount) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>已还金额</span>
            <strong>{{ formatCurrency(paidAmount) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>月供参考</span>
            <strong>{{ formatCurrency(monthlyPaymentAmount) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>贷款本金</span>
            <strong>{{ formatCurrency(Number(account?.loanTotalAmount ?? 0)) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>贷款利率</span>
            <strong>{{ loanInterestRateText }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>剩余期数</span>
            <strong>{{ remainingInstallmentPeriods }} 期</strong>
          </div>
        </div>

        <div class="debt-detail-summary-actions">
          <CommonButton variant="secondary" :disabled="isSettled" @click="openAddRecordModal">
            新增月账单
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSettled" @click="openPrepayModal">
            提前还款
          </CommonButton>
        </div>
      </section>

      <section class="debt-record-history" aria-label="还款记录">
        <div class="debt-record-history-head">
          <div class="debt-record-history-title">
            <strong>月账单列表</strong>
            <span>支持按月还款、修改账单，也支持整笔提前结清</span>
          </div>
          <span class="debt-record-history-badge">{{ records.length }} 条</span>
        </div>

        <div class="debt-record-history-list">
          <p v-if="records.length === 0" class="debt-record-empty">
            暂无月账单
          </p>

          <article
            v-for="record in records"
            v-else
            :key="record.id"
            class="debt-record-card"
          >
            <div class="debt-record-card-main">
              <div class="debt-record-card-top">
                <span class="debt-record-chip" :class="{ 'is-payable': record.repaymentStatus !== 'paid' }">
                  {{ formatRepaymentStatus(record.repaymentStatus, record.repaymentType) }}
                </span>
                <span class="debt-record-date">{{ formatDate(record.occurredAt) }}</span>
              </div>
              <p class="debt-record-funding-account">
                {{ formatInstallmentLabel(record) }}
                · {{ record.repaymentStatus === 'paid' && record.paidAt ? `还款于 ${formatDate(record.paidAt)}` : formatRemainingLabel(record) }}
              </p>
              <p class="debt-record-remark">{{ record.remark || '未填写还款说明' }}</p>
            </div>

            <div class="debt-record-card-side">
              <strong class="debt-record-amount" :class="{ 'is-negative': record.repaymentStatus !== 'paid' }">
                {{ formatRecordAmount(record) }}
              </strong>
              <div v-if="record.repaymentType !== 'prepayment'" class="debt-record-actions">
                <button
                  v-if="record.repaymentStatus !== 'paid'"
                  type="button"
                  class="debt-record-action"
                  @click="openRepayModal(record)"
                >
                  去还款
                </button>
                <button type="button" class="debt-record-action" @click="openEditRecordModal(record)">
                  修改
                </button>
                <button type="button" class="debt-record-action is-danger" @click="openDeleteModal(record)">
                  删除
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <FloatingAddButton
        v-if="!isSettled"
        aria-label="新增月账单"
        storage-key="liability-record-create"
        @click="openAddRecordModal"
      />
    </template>

    <CommonModal
      v-model="showRepayModal"
      title="确认月供还款"
    >
      <div class="debt-record-form">
        <CommonInput
          :model-value="repayingRecord ? repayingRecord.accountName || detailName : detailName"
          label="贷款账户"
          :disabled="true"
        />
        <CommonInput
          :model-value="repayingRecord ? formatInstallmentLabel(repayingRecord) : ''"
          label="当前账单"
          :disabled="true"
        />
        <CommonInput
          :model-value="repayingRecord ? formatRecordAmount(repayingRecord) : ''"
          label="本期金额"
          :disabled="true"
        />
        <CommonInput
          v-model="repayPaidAt"
          label="还款时间"
          input-type="datetime-local"
        />
        <p v-if="repayError" class="debt-record-form-error">
          {{ repayError }}
        </p>
      </div>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isRepaying" @click="closeRepayModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isRepaying" @click="confirmRepay">
            {{ isRepaying ? '还款中...' : '确认还款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showPrepayModal"
      title="提前还款"
    >
      <div class="debt-record-form">
        <CommonInput :model-value="detailName" label="贷款账户" :disabled="true" />
        <CommonInput :model-value="summaryAmountText" label="剩余应还" :disabled="true" />
        <CommonInput
          v-model="prepayPaidAt"
          label="结清时间"
          input-type="datetime-local"
        />
        <CommonInput v-model="prepayRemark" label="备注" placeholder="例如：提前结清房贷" />
        <p v-if="prepayError" class="debt-record-form-error">
          {{ prepayError }}
        </p>
      </div>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isPrepaying" @click="closePrepayModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isPrepaying" @click="confirmPrepay">
            {{ isPrepaying ? '结清中...' : '确认提前还款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showRecordModal"
      :title="recordModalTitle"
    >
      <div class="debt-record-form">
        <template v-if="hasLoanPlan">
          <CommonInput
            v-if="!editingRecord && nextInstallmentPreview"
            :model-value="`第 ${nextInstallmentPreview.currentPeriod}/${nextInstallmentPreview.totalPeriods} 期`"
            label="计划账期"
            :disabled="true"
          />
          <CommonInput
            v-else-if="editingRecord"
            :model-value="formatInstallmentLabel(editingRecord)"
            label="计划账期"
            :disabled="true"
          />
          <CommonInput
            v-model="recordOccurredAt"
            label="账单时间"
            input-type="datetime-local"
          />
          <CommonInput
            v-model="recordAmount"
            label="本期金额"
            placeholder="允许按实际月供调整"
            input-type="number"
            input-mode="decimal"
          />
          <p class="debt-record-form-hint">
            月账单会按贷款合同顺序生成，默认带出下期账期、还款日和参考月供。
          </p>
          <p v-if="nextInstallmentPreview?.error && !editingRecord" class="debt-record-form-error">
            {{ nextInstallmentPreview.error }}
          </p>
        </template>
        <template v-else>
          <CommonInput
            v-model="recordAmount"
            label="本期待还金额"
            placeholder="输入本期应还金额"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="recordInstallmentTotalPeriods"
            label="贷款总期数"
            placeholder="可选，例如 360"
            input-type="number"
            input-mode="numeric"
          />
          <CommonInput
            v-model="recordInstallmentCurrentPeriod"
            label="当前还款期数"
            placeholder="可选，默认第1期"
            input-type="number"
            input-mode="numeric"
          />
          <CommonInput
            v-model="recordOccurredAt"
            label="账单时间"
            input-type="datetime-local"
          />
          <p class="debt-record-form-hint">
            当前账户未配置完整贷款合同，暂按手工账单模式处理。
          </p>
        </template>
        <CommonInput v-model="recordRemark" label="备注" placeholder="输入本期房贷、车贷说明" />
        <p v-if="recordFormError" class="debt-record-form-error">
          {{ recordFormError }}
        </p>
      </div>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isSavingRecord" @click="closeRecordModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSavingRecord" @click="saveRecord">
            {{ editingRecord ? '保存修改' : '保存账单' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showAccountDeleteModal"
      title="删除负债账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-record-delete-message">
        删除后该账户下的全部月账单会一并移除，确认继续吗？
      </p>
      <p v-if="accountDeleteError" class="debt-record-delete-error">
        {{ accountDeleteError }}
      </p>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeletingAccount" @click="closeAccountDeleteModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeletingAccount" @click="confirmDeleteAccount">
            {{ isDeletingAccount ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteModal"
      title="删除月账单"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-record-delete-message">
        删除后无法恢复，确认删除这条月账单吗？
      </p>
      <p v-if="deleteError" class="debt-record-delete-error">
        {{ deleteError }}
      </p>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeleting" @click="confirmDelete">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
