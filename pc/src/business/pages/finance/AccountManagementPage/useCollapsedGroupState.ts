// 账户分组折叠状态：本地持久化各账户分组的展开/折叠状态。
import { ref } from 'vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import type { AccountGroup } from '@/business/types/account'

const ACCOUNT_GROUP_COLLAPSE_STORAGE_KEY =
  'finance-account-management-group-collapse'

export function useCollapsedGroupState() {
  const collapsedGroupState = ref<Record<string, boolean>>({})

  function getCollapsedGroupStorageId() {
    const currentUser = getStoredCurrentUser()
    return `${ACCOUNT_GROUP_COLLAPSE_STORAGE_KEY}-${currentUser?.id ?? 'guest'}`
  }

  function restoreCollapsedGroupState() {
    if (typeof window === 'undefined') {
      return
    }
    const raw = window.localStorage.getItem(getCollapsedGroupStorageId())
    if (!raw) {
      collapsedGroupState.value = {}
      return
    }
    try {
      const parsed = JSON.parse(raw) as Record<string, boolean>
      collapsedGroupState.value = Object.entries(parsed).reduce<
        Record<string, boolean>
      >((result, [key, value]) => {
        if (typeof value === 'boolean') {
          result[key] = value
        }
        return result
      }, {})
    } catch {
      window.localStorage.removeItem(getCollapsedGroupStorageId())
      collapsedGroupState.value = {}
    }
  }

  function persistCollapsedGroupState() {
    if (typeof window === 'undefined') {
      return
    }
    window.localStorage.setItem(
      getCollapsedGroupStorageId(),
      JSON.stringify(collapsedGroupState.value),
    )
  }

  function toggleAccountGroup(group: AccountGroup) {
    const groupStorageKey = group.storageKey
    if (!groupStorageKey) {
      return
    }
    collapsedGroupState.value = {
      ...collapsedGroupState.value,
      [groupStorageKey]: !(collapsedGroupState.value[groupStorageKey] ?? false),
    }
    persistCollapsedGroupState()
  }

  return {
    collapsedGroupState,
    getCollapsedGroupStorageId,
    restoreCollapsedGroupState,
    persistCollapsedGroupState,
    toggleAccountGroup,
  }
}
