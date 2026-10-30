import { computed, ref } from 'vue'
import { getFamilyOverview, type FamilyMember } from '@/api/modules/auth'
import { getStoredCurrentUser } from '@/utils/current-user'

export type FinanceFamilyViewOption = {
  value: string
  label: string
  userId?: number
  kind: 'self' | 'member' | 'total'
}

export function useFinanceFamilyView() {
  const familyMembers = ref<FamilyMember[]>([])
  const familyView = ref('self')
  const currentUser = computed(() => getStoredCurrentUser())

  const familyRoster = computed(() => {
    const roster = new Map<number, { userId: number; displayName: string }>()
    const me = currentUser.value

    if (me) {
      roster.set(me.id, {
        userId: me.id,
        displayName: me.displayName?.trim() || me.username || '我',
      })
    }

    familyMembers.value.forEach((member) => {
      roster.set(member.userId, {
        userId: member.userId,
        displayName: member.displayName?.trim() || `成员${member.userId}`,
      })
    })

    return Array.from(roster.values())
  })

  const familyViewOptions = computed<FinanceFamilyViewOption[]>(() => {
    const me = currentUser.value
    if (!me) {
      return []
    }

    const options: FinanceFamilyViewOption[] = [
      { value: 'self', label: '我的数据', userId: me.id, kind: 'self' },
    ]

    if (familyRoster.value.length > 1) {
      options.unshift({ value: 'total', label: '家庭总计', kind: 'total' })
    }

    familyRoster.value
      .filter((member) => member.userId !== me.id)
      .forEach((member) => {
        options.push({
          value: `member-${member.userId}`,
          label: member.displayName,
          userId: member.userId,
          kind: 'member',
        })
      })

    return options
  })

  const selectedFamilyView = computed<FinanceFamilyViewOption>(() =>
    familyViewOptions.value.find((option) => option.value === familyView.value)
    ?? familyViewOptions.value[0]
    ?? { value: 'self', label: '我的数据', kind: 'self' },
  )

  const canSwitchFamilyView = computed(() => familyViewOptions.value.length > 1)
  const isSelfView = computed(() => selectedFamilyView.value.kind === 'self')
  const isReadOnlyFamilyView = computed(() => selectedFamilyView.value.kind !== 'self')

  const selectedViewerUserIds = computed(() => {
    if (selectedFamilyView.value.kind === 'total') {
      return familyRoster.value.map((member) => member.userId)
    }

    if (selectedFamilyView.value.userId) {
      return [selectedFamilyView.value.userId]
    }

    return currentUser.value ? [currentUser.value.id] : []
  })

  const viewerNameByUserId = computed(() => new Map(
    familyRoster.value.map((member) => [member.userId, member.displayName]),
  ))

  async function loadFamilyMembers() {
    const me = currentUser.value
    if (!me) {
      familyMembers.value = []
      familyView.value = 'self'
      return
    }

    try {
      const familyOverview = await getFamilyOverview()
      familyMembers.value = familyOverview.hasFamily ? familyOverview.members : []
    } catch {
      familyMembers.value = []
    }

    const validValues = new Set(familyViewOptions.value.map((option) => option.value))
    if (!validValues.has(familyView.value)) {
      familyView.value = 'self'
    }
  }

  return {
    currentUser,
    familyMembers,
    familyView,
    familyRoster,
    familyViewOptions,
    selectedFamilyView,
    canSwitchFamilyView,
    isSelfView,
    isReadOnlyFamilyView,
    selectedViewerUserIds,
    viewerNameByUserId,
    loadFamilyMembers,
  }
}
