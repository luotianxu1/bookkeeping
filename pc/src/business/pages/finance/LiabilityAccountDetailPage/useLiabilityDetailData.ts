// 负债账户详情页的数据加载：账户、汇总、月账单列表的拉取与竞态保护。
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  getAccount,
  getLiabilityAccountSummary,
  getLiabilityRecords,
  type Account,
  type LiabilityAccountSummary,
  type LiabilityRecord,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

export function useLiabilityDetailData() {
  const route = useRoute()

  const account = ref<Account | null>(null)
  const summary = ref<LiabilityAccountSummary>({
    totalAmount: 0,
    accountCount: 0,
    recordCount: 0,
  })
  const records = ref<LiabilityRecord[]>([])
  const isLoading = ref(false)
  const pageError = ref('')
  let requestVersion = 0

  const accountId = computed(() => Number(route.params.accountId))

  watch(
    accountId,
    () => {
      void loadDetail()
    },
    { immediate: true },
  )

  async function loadDetail() {
    const currentRequestVersion = ++requestVersion
    const currentUser = getStoredCurrentUser()

    account.value = null
    records.value = []
    pageError.value = ''

    if (!currentUser) {
      pageError.value = '请先登录后查看负债详情'
      return
    }

    if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
      pageError.value = '负债账户不存在'
      return
    }

    isLoading.value = true

    try {
      const [accountDetail, summaryData, recordList] = await Promise.all([
        getAccount(accountId.value),
        getLiabilityAccountSummary(currentUser.id, accountId.value),
        getLiabilityRecords({
          userId: currentUser.id,
          accountId: accountId.value,
        }),
      ])

      if (currentRequestVersion !== requestVersion) {
        return
      }

      if (accountDetail.accountTypeCode !== 'liability') {
        pageError.value = '当前账户不是负债账户'
        return
      }

      account.value = accountDetail
      summary.value = summaryData
      records.value = recordList
    } catch (error) {
      if (currentRequestVersion !== requestVersion) {
        return
      }
      pageError.value = getErrorMessage(error, '负债详情加载失败')
    } finally {
      if (currentRequestVersion === requestVersion) {
        isLoading.value = false
      }
    }
  }

  return {
    account,
    summary,
    records,
    isLoading,
    pageError,
    accountId,
    loadDetail,
  }
}
