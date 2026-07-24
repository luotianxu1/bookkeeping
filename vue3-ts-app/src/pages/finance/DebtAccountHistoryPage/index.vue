<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  getAccounts,
  getDebtRecords,
  type Account,
  type DebtRecord,
} from '@/api/modules/finance'
import { getContacts, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type HistoryRecord = Pick<DebtRecord, 'id' | 'direction' | 'recordType' | 'amount' | 'remark' | 'occurredAt'>

type SettledDebtGroup = {
  accountId: number
  name: string
  avatarText: string
  avatarClass: string
  records: HistoryRecord[]
}

const DEBT_ACCOUNT_CODES = new Set(['debt'])

const accounts = ref<Account[]>([])
const contacts = ref<Contact[]>([])
const debtRecords = ref<DebtRecord[]>([])
const isLoading = ref(false)
const pageError = ref('')

const contactMap = computed(() => new Map(contacts.value.map((contact) => [contact.id, contact])))
const settledGroups = computed<SettledDebtGroup[]>(() => {
  const recordsByAccountId = new Map<number, DebtRecord[]>()
  for (const record of debtRecords.value) {
    const records = recordsByAccountId.get(record.accountId) ?? []
    records.push(record)
    recordsByAccountId.set(record.accountId, records)
  }

  return accounts.value
    .filter((account) => DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? ''))
    .map((account, index) => {
      const records = (recordsByAccountId.get(account.id) ?? []).slice().sort((left, right) => (
        new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime()
      ))
      const contact = account.contactId ? contactMap.value.get(account.contactId) ?? null : null
      const name = contact?.name?.trim() || account.name

      return {
        accountId: account.id,
        name,
        avatarText: (name || '债').slice(0, 1),
        avatarClass: `debt-history-avatar-${index % 4}`,
        records,
      }
    })
    .filter((group) => group.records.length > 0 && Math.round(sumDebtBalance(group.records) * 100) === 0)
})

const settledRecordCount = computed(() => settledGroups.value.reduce((total, group) => total + group.records.length, 0))

onMounted(() => {
  void loadHistory()
})

async function loadHistory() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看已结清明细'
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
    pageError.value = error instanceof Error ? error.message : '已结清明细加载失败'
  } finally {
    isLoading.value = false
  }
}

function sumDebtBalance(records: Array<Pick<DebtRecord, 'direction' | 'recordType' | 'amount'>>) {
  return records.reduce((total, record) => total + getDebtRecordBalanceDelta(record), 0)
}

function getDebtRecordBalanceDelta(record: Pick<DebtRecord, 'direction' | 'recordType' | 'amount'>) {
  const amount = Number(record.amount ?? 0)
  if (!Number.isFinite(amount) || amount <= 0) {
    return 0
  }
  if (record.direction === 'receivable') {
    return record.recordType === 'repayment' ? -amount : amount
  }
  return record.recordType === 'repayment' ? amount : -amount
}

function formatRecordAction(record: Pick<DebtRecord, 'direction' | 'recordType'>) {
  if (record.direction === 'receivable') {
    return record.recordType === 'repayment' ? '收款' : '借出'
  }
  return record.recordType === 'repayment' ? '还款' : '借入'
}

function formatRecordAmount(record: Pick<DebtRecord, 'direction' | 'recordType' | 'amount'>) {
  const delta = getDebtRecordBalanceDelta(record)
  const sign = delta > 0 ? '+' : delta < 0 ? '-' : ''
  return `${sign}¥${formatNumber(Math.abs(delta))}`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <section class="debt-history-page" aria-label="已结清债务明细">
    <header class="debt-history-header">
      <PageHeader title="已结清明细" back-to="/finance/accounts/debt" back-label="返回债务账户" />
    </header>

    <p v-if="pageError" class="debt-history-message debt-history-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" text="已结清明细加载中..." />

    <template v-else>
      <div class="debt-history-summary">
        <strong>{{ settledGroups.length }} 个已结清账户</strong>
        <span>{{ settledRecordCount }} 条历史记录</span>
      </div>

      <p v-if="settledGroups.length === 0" class="debt-history-empty">
        暂无已结清债务历史
      </p>

      <section v-else class="debt-history-list" aria-label="已结清账户历史">
        <article v-for="group in settledGroups" :key="group.accountId" class="debt-history-card">
          <div class="debt-history-card-head">
            <div class="debt-history-person">
              <span class="debt-history-avatar" :class="group.avatarClass">{{ group.avatarText }}</span>
              <strong>{{ group.name }}</strong>
            </div>
            <span class="debt-history-settled-badge">已结清</span>
          </div>

          <div class="debt-history-records">
            <div v-for="record in group.records" :key="record.id" class="debt-history-record">
              <div class="debt-history-record-main">
                <div class="debt-history-record-top">
                  <span
                    class="debt-history-record-action"
                    :class="{
                      'is-collect': record.direction === 'receivable' && record.recordType === 'repayment',
                      'is-lend': record.direction === 'receivable' && record.recordType !== 'repayment',
                    }"
                  >
                    {{ formatRecordAction(record) }}
                  </span>
                  <span class="debt-history-record-date">{{ formatDate(record.occurredAt) }}</span>
                </div>
                <p v-if="record.remark?.trim()" class="debt-history-record-remark">
                  <span class="debt-history-record-remark-label">备注：</span>{{ record.remark.trim() }}
                </p>
              </div>
              <AmountText
                tag="strong"
                class="debt-history-record-amount"
                :class="{ 'is-negative': getDebtRecordBalanceDelta(record) < 0 }"
                :value="formatRecordAmount(record)"
                tone="inherit"
              />
            </div>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
