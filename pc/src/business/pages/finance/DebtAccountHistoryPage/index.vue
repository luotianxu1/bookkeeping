<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

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
  avatarText: string
  avatarClass: string
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
    .map((account, index) => {
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
        avatarText: (name || '债').slice(0, 1),
        avatarClass: `debt-history-avatar-${index % 4}`,
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

type DesktopRow = (typeof debtGroups.value)[number]
</script>
<template>
  <section class="desktop-workspace">
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
    <DesktopTable
      :rows="debtGroups"
      :loading="isLoading"
      title="已结清账户"
      search-placeholder="搜索账户或联系人"
      searchable
    >
      <el-table-column
        prop="name"
        label="账户 / 联系人"
        min-width="240"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="状态" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          <el-tag type="success">已结清</el-tag>
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column fixed="right" label="操作" width="150">
        <template #default="{ row }: { row: DesktopRow }">
          <el-button link type="primary" @click="openDebtDetail(row.accountId)">
            查看往来
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
  </section>
</template>
