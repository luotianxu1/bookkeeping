<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed } from 'vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

import { useFeedback } from '@/business/composables/useFeedback'
import {
  formatCurrency,
  formatDate,
  formatInstallmentLabel,
  formatRecordAmount,
  formatRepaymentStatus,
} from './liabilityFormat'
import { formatRate } from './liabilityLoanPlan'
import { useLiabilityLoanPlan } from './useLiabilityLoanPlan'
import { useLiabilityDetailData } from './useLiabilityDetailData'
import { useLiabilityRecordActions } from './useLiabilityRecordActions'

const { account, summary, records, isLoading, pageError, loadDetail } =
  useLiabilityDetailData()
const { showFeedbackModal, feedbackMessage, feedbackType, showFeedback } =
  useFeedback()

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
const summaryAmountText = computed(() =>
  formatCurrency(summary.value.totalAmount),
)
const loanInterestRateText = computed(() => formatRate(account.value))

type DetailTableRow = (typeof records.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="detailName">
      <template #right>
        <el-button
          type="danger"
          plain
          :disabled="isDeletingAccount || !account"
          @click="openAccountDeleteModal"
        >
          删除账户
        </el-button>
        <el-button :disabled="isSettled" @click="openAddRecordModal">
          新增月账单
        </el-button>
        <el-button
          type="primary"
          :disabled="isSettled"
          @click="openPrepayModal"
        >
          提前还款
        </el-button>
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
        { label: '待还金额', value: summaryAmountText },
        { label: '累计还款', value: formatCurrency(paidAmount) },
        { label: '月供参考', value: formatCurrency(monthlyPaymentAmount) },
        { label: '剩余期数', value: remainingInstallmentPeriods },
      ]"
    />
    <div class="desktop-main-aside">
      <DesktopTable :rows="records" title="还款账单" searchable>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="日期" min-width="140">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatDate(row.occurredAt) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="期数" min-width="120">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatInstallmentLabel(row) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatRepaymentStatus(row.repaymentStatus, row.repaymentType) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="金额" min-width="140">
          <template #default="{ row }: { row: DetailTableRow }">
            {{ formatRecordAmount(row) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column
          label="备注"
          prop="remark"
          min-width="240"
          show-overflow-tooltip
        />
        <!-- @vue-generic {DetailTableRow} -->
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }: { row: DetailTableRow }">
            <div class="pc-row-actions">
              <template v-if="row.repaymentType !== 'prepayment'">
                <el-button
                  link
                  type="primary"
                  v-if="row.repaymentStatus !== 'paid'"
                  @click="openRepayModal(row)"
                >
                  还款
                </el-button>
                <el-button
                  link
                  type="primary"
                  @click="openEditRecordModal(row)"
                >
                  修改
                </el-button>
                <el-button link type="danger" @click="openDeleteModal(row)">
                  删除
                </el-button>
              </template>
            </div>
          </template>
        </el-table-column>
      </DesktopTable>
      <PagePanel title="贷款合同">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="状态">
            {{ isSettled ? '已结清' : '还款中' }}
          </el-descriptions-item>
          <el-descriptions-item label="本金">
            {{ formatCurrency(Number(account?.loanTotalAmount ?? 0)) }}
          </el-descriptions-item>
          <el-descriptions-item label="合同总额">
            {{ formatCurrency(totalRepaymentAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="利率">
            {{ loanInterestRateText }}
          </el-descriptions-item>
          <el-descriptions-item label="还款日">
            {{ account?.loanRepaymentDay ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="下期账单">
            {{ nextDueText }}
          </el-descriptions-item>
          <el-descriptions-item label="备注">
            {{ detailNote || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </PagePanel>
    </div>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal v-model="showRepayModal" title="确认月供还款">
      <div class="pc-form-grid debt-record-form">
        <CommonInput
          :model-value="
            repayingRecord
              ? repayingRecord.accountName || detailName
              : detailName
          "
          label="贷款账户"
          :disabled="true"
        />
        <CommonInput
          :model-value="
            repayingRecord ? formatInstallmentLabel(repayingRecord) : ''
          "
          label="当前账单"
          :disabled="true"
        />
        <CommonInput
          :model-value="
            repayingRecord ? formatRecordAmount(repayingRecord) : ''
          "
          label="本期金额"
          :disabled="true"
        />
        <CommonInput
          v-model="repayPaidAt"
          label="还款时间"
          input-type="datetime-local"
        />
        <p v-if="repayError" class="debt-record-form-error">{{ repayError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar debt-record-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isRepaying"
            @click="closeRepayModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isRepaying"
            @click="confirmRepay"
          >
            {{ isRepaying ? '还款中...' : '确认还款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal v-model="showPrepayModal" title="提前还款">
      <div class="pc-form-grid debt-record-form">
        <CommonInput
          :model-value="detailName"
          label="贷款账户"
          :disabled="true"
        />
        <CommonInput
          :model-value="summaryAmountText"
          label="剩余应还"
          :disabled="true"
        />
        <CommonInput
          v-model="prepayPaidAt"
          label="结清时间"
          input-type="datetime-local"
        />
        <CommonInput
          v-model="prepayRemark"
          label="备注"
          placeholder="例如：提前结清房贷"
        />
        <p v-if="prepayError" class="debt-record-form-error">
          {{ prepayError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar debt-record-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isPrepaying"
            @click="closePrepayModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isPrepaying"
            @click="confirmPrepay"
          >
            {{ isPrepaying ? '结清中...' : '确认提前还款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal v-model="showRecordModal" :title="recordModalTitle">
      <div class="pc-form-grid debt-record-form">
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
          <p
            v-if="nextInstallmentPreview?.error && !editingRecord"
            class="debt-record-form-error"
          >
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
        <CommonInput
          v-model="recordRemark"
          label="备注"
          placeholder="输入本期房贷、车贷说明"
        />
        <p v-if="recordFormError" class="debt-record-form-error">
          {{ recordFormError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar debt-record-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingRecord"
            @click="closeRecordModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingRecord"
            @click="saveRecord"
          >
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
        <div class="pc-toolbar debt-record-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeletingAccount"
            @click="closeAccountDeleteModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeletingAccount"
            @click="confirmDeleteAccount"
          >
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
        <div class="pc-toolbar debt-record-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeleting"
            @click="confirmDelete"
          >
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
