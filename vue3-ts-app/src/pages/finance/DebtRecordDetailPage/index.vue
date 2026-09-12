<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  getAccount,
  getDebtRecords,
  type Account,
  type DebtRecord,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const route = useRoute()
const account = ref<Account | null>(null)
const principalRecord = ref<DebtRecord | null>(null)
const repaymentRecords = ref<DebtRecord[]>([])
const isLoading = ref(false)
const pageError = ref('')
let requestVersion = 0

const accountId = computed(() => Number(route.params.accountId))
const recordId = computed(() => Number(route.params.recordId))
const isReceivable = computed(() => principalRecord.value?.direction === 'receivable')
const actionLabel = computed(() => isReceivable.value ? '收款' : '还款')
const principalAmount = computed(() => Number(principalRecord.value?.amount ?? 0))
const settledAmount = computed(() => repaymentRecords.value.reduce((total, record) => total + Number(record.amount ?? 0), 0))
const remainingAmount = computed(() => Math.max(0, principalAmount.value - settledAmount.value))
const isSettled = computed(() => remainingAmount.value <= 0)
const progressPercent = computed(() => principalAmount.value > 0
  ? Math.min(100, Math.round((settledAmount.value / principalAmount.value) * 100))
  : 0)
const detailTitle = computed(() => isReceivable.value ? '借出详情' : '借入详情')
const detailSubtitle = computed(() => principalRecord.value ? formatDate(principalRecord.value.occurredAt) : '')

watch([accountId, recordId], () => {
  void loadDetail()
}, { immediate: true })

async function loadDetail() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()
  account.value = null
  principalRecord.value = null
  repaymentRecords.value = []
  pageError.value = ''

  if (!currentUser) {
    pageError.value = '请先登录后查看债务项详情'
    return
  }
  if (!Number.isInteger(accountId.value) || accountId.value <= 0 || !Number.isInteger(recordId.value) || recordId.value <= 0) {
    pageError.value = '债务项不存在'
    return
  }

  isLoading.value = true
  try {
    const [accountDetail, recordList] = await Promise.all([
      getAccount(accountId.value),
      getDebtRecords({ userId: currentUser.id, accountId: accountId.value }),
    ])
    if (currentRequestVersion !== requestVersion) {
      return
    }
    if (accountDetail.accountTypeCode !== 'debt') {
      pageError.value = '当前账户不是债务账户'
      return
    }
    const principal = recordList.find((record) => record.id === recordId.value)
    if (!principal || principal.recordType === 'repayment' || principal.parentRecordId) {
      pageError.value = '当前债务项不存在或不是借入/借出主记录'
      return
    }
    account.value = accountDetail
    principalRecord.value = principal
    repaymentRecords.value = recordList
      .filter((record) => record.parentRecordId === principal.id && record.status === 'active')
      .sort((left, right) => new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime())
  } catch (error) {
    if (currentRequestVersion === requestVersion) {
      pageError.value = error instanceof Error ? error.message : '债务项详情加载失败'
    }
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value))
}

function formatCurrency(value: number) {
  return `¥${formatNumber(value)}`
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function formatRecordAmount(record: DebtRecord) {
  return `${actionLabel.value} ${formatCurrency(Number(record.amount ?? 0))}`
}
</script>

<template>
  <section class="debt-record-detail-page" aria-label="债务项详情">
    <header class="debt-record-detail-header">
      <PageHeader :title="detailTitle" :back-to="`/finance/accounts/debt/${accountId}`" :back-label="`返回债务详情`" />
    </header>

    <p v-if="pageError" class="debt-record-detail-message debt-record-detail-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else-if="principalRecord">
      <section class="debt-record-detail-summary" aria-label="债务项汇总">
        <div class="debt-record-detail-summary-top">
          <div>
            <h2>{{ account?.name || '债务账户' }}</h2>
            <p>{{ detailSubtitle }}</p>
          </div>
          <span class="debt-record-detail-status" :class="{ 'is-settled': isSettled }">
            {{ isSettled ? '已结清' : `待${actionLabel}` }}
          </span>
        </div>

        <AmountText tag="p" class="debt-record-detail-remaining" :value="formatCurrency(remainingAmount)" tone="inherit" />
        <p class="debt-record-detail-caption">{{ isSettled ? '本笔债务已全部结清' : `还可${actionLabel} ${formatCurrency(remainingAmount)}` }}</p>

        <div class="debt-record-detail-metrics">
          <div>
            <span>总金额</span>
            <strong>{{ formatCurrency(principalAmount) }}</strong>
          </div>
          <div>
            <span>已{{ actionLabel }}</span>
            <strong>{{ formatCurrency(settledAmount) }}</strong>
          </div>
          <div>
            <span>记录数</span>
            <strong>{{ repaymentRecords.length }} 笔</strong>
          </div>
        </div>

        <div class="debt-record-detail-progress" aria-label="结清进度">
          <div class="debt-record-detail-progress-track">
            <span :style="{ width: `${progressPercent}%` }"></span>
          </div>
          <span>{{ progressPercent }}% 已结清</span>
        </div>
      </section>

      <section class="debt-record-detail-list" aria-label="还款收款记录">
        <div class="debt-record-detail-list-head">
          <strong>全部{{ actionLabel }}记录</strong>
          <span>{{ repaymentRecords.length }} 笔</span>
        </div>
        <p v-if="repaymentRecords.length === 0" class="debt-record-detail-empty">暂无{{ actionLabel }}记录</p>
        <article v-for="record in repaymentRecords" v-else :key="record.id" class="debt-record-detail-item">
          <div>
            <strong>{{ actionLabel }}</strong>
            <span>{{ formatDate(record.occurredAt) }}</span>
            <p v-if="record.remark?.trim()">{{ record.remark.trim() }}</p>
            <small>现金账户：{{ record.fundingAccountName?.trim() || '未关联现金账户' }}</small>
          </div>
          <strong class="debt-record-detail-item-amount">{{ formatRecordAmount(record) }}</strong>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
