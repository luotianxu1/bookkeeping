<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

import {
  getAccount,
  getDebtRecords,
  type Account,
  type DebtRecord,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const account = ref<Account | null>(null)
const principalRecord = ref<DebtRecord | null>(null)
const repaymentRecords = ref<DebtRecord[]>([])
const isLoading = ref(true)
const pageError = ref('')
let requestVersion = 0

const accountId = computed(() => Number(route.params.accountId))
const recordId = computed(() => Number(route.params.recordId))
const isReceivable = computed(
  () => principalRecord.value?.direction === 'receivable',
)
const actionLabel = computed(() => (isReceivable.value ? '收款' : '还款'))
const principalAmount = computed(() =>
  Number(principalRecord.value?.amount ?? 0),
)
const settledAmount = computed(() =>
  repaymentRecords.value.reduce(
    (total, record) => total + Number(record.amount ?? 0),
    0,
  ),
)
const remainingAmount = computed(() =>
  Math.max(0, principalAmount.value - settledAmount.value),
)
const isSettled = computed(() => remainingAmount.value <= 0)
const progressPercent = computed(() =>
  principalAmount.value > 0
    ? Math.min(100, (settledAmount.value / principalAmount.value) * 100)
    : 0,
)
const detailTitle = computed(() =>
  isReceivable.value ? '借出详情' : '借入详情',
)

watch(
  [accountId, recordId],
  () => {
    void loadDetail()
  },
  { immediate: true },
)

async function loadDetail() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()
  account.value = null
  principalRecord.value = null
  repaymentRecords.value = []
  pageError.value = ''

  if (!currentUser) {
    pageError.value = '请先登录后查看债务项详情'
    isLoading.value = false
    return
  }
  if (
    !Number.isInteger(accountId.value) ||
    accountId.value <= 0 ||
    !Number.isInteger(recordId.value) ||
    recordId.value <= 0
  ) {
    pageError.value = '债务项不存在'
    isLoading.value = false
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
    if (
      !principal ||
      principal.recordType === 'repayment' ||
      principal.parentRecordId
    ) {
      pageError.value = '当前债务项不存在或不是借入/借出主记录'
      return
    }
    account.value = accountDetail
    principalRecord.value = principal
    repaymentRecords.value = recordList
      .filter(
        (record) =>
          record.parentRecordId === principal.id && record.status === 'active',
      )
      .sort(
        (left, right) =>
          new Date(right.occurredAt).getTime() -
          new Date(left.occurredAt).getTime(),
      )
  } catch (error) {
    if (currentRequestVersion === requestVersion) {
      pageError.value = getErrorMessage(error, '债务项详情加载失败')
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

type DesktopRow = (typeof repaymentRecords.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="detailTitle"><template #right></template></PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="principalRecord">
      <MetricStrip
        :items="[
          { label: '本金', value: formatCurrency(principalAmount) },
          { label: '已结算', value: formatCurrency(settledAmount) },
          { label: '待结算', value: formatCurrency(remainingAmount) },
          { label: '还款记录', value: repaymentRecords.length },
        ]"
      />
      <PagePanel title="结清进度">
        <el-progress
          :format="formatPercentage"
          :percentage="progressPercent"
          :status="isSettled ? 'success' : undefined"
        />
      </PagePanel>
      <DesktopTable
        :rows="repaymentRecords"
        :loading="isLoading"
        title="结算记录"
        search-placeholder="搜索账户、备注"
        searchable
      >
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column label="发生时间" min-width="180">
          <template #default="{ row }: { row: DesktopRow }">
            {{ formatDate(row.occurredAt) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column label="金额" min-width="150">
          <template #default="{ row }: { row: DesktopRow }">
            {{ formatRecordAmount(row) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="fundingAccountName"
          label="现金账户"
          min-width="200"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="remark"
          label="备注"
          min-width="220"
          sortable="custom"
          show-overflow-tooltip
        />
      </DesktopTable>
    </template>
  </section>
</template>
