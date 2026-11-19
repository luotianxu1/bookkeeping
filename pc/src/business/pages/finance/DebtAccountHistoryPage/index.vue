<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getAccounts,
  getDebtRecords,
  type Account,
  type DebtRecord,
} from '@/business/api/modules/finance'
import { getContacts, type Contact } from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type DebtHistoryGroup = {
  accountId: number
  name: string
  records: DebtRecord[]
  balance: number
  settled: boolean
}

const DEBT_ACCOUNT_CODES = new Set(['debt'])

const router = useRouter()
const accounts = ref<Account[]>([])
const contacts = ref<Contact[]>([])
const debtRecords = ref<DebtRecord[]>([])
const isLoading = ref(true)
const pageError = ref('')

const contactMap = computed(
  () => new Map(contacts.value.map((contact) => [contact.id, contact])),
)
const debtGroups = computed<DebtHistoryGroup[]>(() => {
  const recordsByAccountId = new Map<number, DebtRecord[]>()
  for (const record of debtRecords.value) {
    const records = recordsByAccountId.get(record.accountId) ?? []
    records.push(record)
    recordsByAccountId.set(record.accountId, records)
  }

  return accounts.value
    .filter((account) => DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? ''))
    .map((account) => {
      const records = (recordsByAccountId.get(account.id) ?? [])
        .slice()
        .sort(
          (left, right) =>
            new Date(right.occurredAt).getTime() -
            new Date(left.occurredAt).getTime(),
        )
      const contact = account.contactId
        ? (contactMap.value.get(account.contactId) ?? null)
        : null
      const name = contact?.name?.trim() || account.name
      const balance = sumDebtBalance(records)

      return {
        accountId: account.id,
        name,
        records,
        balance,
        settled: Math.round(balance * 100) === 0,
      }
    })
    .filter((group) => group.records.length > 0 && group.settled)
})

const settledAccountCount = computed(() => debtGroups.value.length)

function openDebtDetail(accountId: number) {
  router.push(`/finance/accounts/debt/${accountId}`)
}

onMounted(() => {
  void loadHistory()
})

async function loadHistory() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看债务明细'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, contactList, recordList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getContacts({ userId: currentUser.id, status: 'active' }),
      getDebtRecords({ userId: currentUser.id }),
    ])
    accounts.value = accountList
    contacts.value = contactList
    debtRecords.value = recordList
  } catch (error) {
    pageError.value = getErrorMessage(error, '债务明细加载失败')
  } finally {
    isLoading.value = false
  }
}

function sumDebtBalance(
  records: Array<Pick<DebtRecord, 'direction' | 'recordType' | 'amount'>>,
) {
  return records.reduce(
    (total, record) => total + getDebtRecordBalanceDelta(record),
    0,
  )
}

function getDebtRecordBalanceDelta(
  record: Pick<DebtRecord, 'direction' | 'recordType' | 'amount'>,
) {
  const amount = Number(record.amount ?? 0)
  if (!Number.isFinite(amount) || amount <= 0) {
    return 0
  }
  if (record.direction === 'receivable') {
    return record.recordType === 'repayment' ? -amount : amount
  }
  return record.recordType === 'repayment' ? amount : -amount
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

function formatRecordLabel(record: DebtRecord) {
  if (record.direction === 'receivable') {
    return record.recordType === 'repayment' ? '收款' : '借出'
  }
  return record.recordType === 'repayment' ? '还款' : '借入'
}

// 收款/借出（红）与还款/借入（绿）在时间线上用方向色区分。
function formatRecordAmountText(record: DebtRecord) {
  const amount = formatCurrency(Number(record.amount ?? 0))
  return `${record.direction === 'receivable' ? '+' : '-'}${amount}`
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

type DesktopRow = (typeof debtGroups.value)[number]
</script>
<template>
  <section class="desktop-workspace stagger-in">
    <PageHeader title="已结清账户"><template #right></template></PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[{ label: '已结清账户', value: settledAccountCount }]"
    />
    <DesktopRecordList
      :rows="debtGroups"
      :loading="isLoading"
      title="已结清账户"
      search-placeholder="账户 / 联系人"
      :search-keys="['name']"
      row-key="accountId"
      empty-title="暂无已结清账户"
      empty-hint="往来全部结清的债权账户会自动归档到这里"
    >
      <template #default="{ row }: { row: DesktopRow }">
        <article class="record-card debt-history-card">
          <div class="record-card-head">
            <div class="record-card-title">
              <span class="debt-history-avatar" aria-hidden="true">{{
                row.name.slice(0, 1)
              }}</span>
              <span>{{ row.name }}</span>
            </div>
            <div class="record-card-aside">
              <el-tag size="small" type="success" effect="light">已结清</el-tag>
            </div>
          </div>
          <ol class="debt-history-timeline">
            <li
              v-for="record in row.records.slice(0, 5)"
              :key="record.id"
              :class="record.direction === 'receivable' ? 'is-receivable' : 'is-payable'"
            >
              <span class="debt-history-dot" aria-hidden="true"></span>
              <span class="debt-history-date">{{ formatDate(record.occurredAt) }}</span>
              <span class="debt-history-label">{{ formatRecordLabel(record) }}</span>
              <strong class="debt-history-amount">{{
                formatRecordAmountText(record)
              }}</strong>
            </li>
          </ol>
          <p v-if="row.records.length > 5" class="debt-history-more">
            共 {{ row.records.length }} 笔往来，仅展示最近 5 笔
          </p>
          <div class="record-card-foot">
            <span class="debt-history-count">共 {{ row.records.length }} 笔往来</span>
            <el-button link type="primary" @click="openDebtDetail(row.accountId)">
              查看往来
            </el-button>
          </div>
        </article>
      </template>
    </DesktopRecordList>
  </section>
</template>
<style scoped>
/* 已结清账户卡：账户名 + 内嵌最近往来的迷你时间线，方向色区分收/支。 */
.debt-history-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 50%;
  font-size: 14px;
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}
.debt-history-timeline {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0 0 0 14px;
  list-style: none;
  border-left: 2px solid var(--el-border-color-lighter);
}
.debt-history-timeline li {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  font-size: 13px;
}
.debt-history-dot {
  position: absolute;
  top: 0.55em;
  left: -20px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}
.debt-history-timeline li.is-receivable .debt-history-dot {
  background: var(--el-color-danger);
}
.debt-history-timeline li.is-payable .debt-history-dot {
  background: var(--el-color-success);
}
.debt-history-date {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.debt-history-label {
  flex-shrink: 0;
}
.debt-history-amount {
  margin-left: auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.debt-history-timeline li.is-receivable .debt-history-amount {
  color: var(--el-color-danger);
}
.debt-history-timeline li.is-payable .debt-history-amount {
  color: var(--el-color-success);
}
.debt-history-more {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.debt-history-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
