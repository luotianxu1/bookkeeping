<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createTravelPlan,
  createTravelPlanCompanion,
  getContacts,
  getTravelPlan,
  type Contact,
  updateTravelPlan,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type CompanionOption = {
  value: string
  label: string
}

type ExistingCompanion = {
  id: number
  contactId: number
}

const route = useRoute()
const router = useRouter()
const planId = computed(() => {
  const raw = route.params.planId
  return typeof raw === 'string' ? Number(raw) : NaN
})
const isEditMode = computed(() => Number.isFinite(planId.value))

const formName = ref('')
const formStartDate = ref('2026-05-28')
const formEndDate = ref('2026-06-05')
const selectedCompanionIds = ref<string[]>([])
const companionOptions = ref<CompanionOption[]>([])
const remark = ref('')
const isSaving = ref(false)
const saveError = ref('')
const loadError = ref('')
const showCompanionModal = ref(false)
const companionKeyword = ref('')
const existingCompanions = ref<ExistingCompanion[]>([])

const selectedCompanions = computed(() =>
  companionOptions.value.filter((item) => selectedCompanionIds.value.includes(item.value)),
)

const filteredCompanions = computed(() => {
  const keyword = companionKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return companionOptions.value
  }

  return companionOptions.value.filter((item) => item.label.toLowerCase().includes(keyword))
})

onMounted(() => {
  void initializePage()
})

async function initializePage() {
  await loadContacts()
  if (isEditMode.value) {
    await loadPlanDetail()
  }
}

async function loadContacts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    loadError.value = `请先登录后再${isEditMode.value ? '修改' : '创建'}旅行`
    return
  }

  try {
    const contacts = await getContacts({ userId: currentUser.id, status: 'active' })
    companionOptions.value = contacts.map((contact: Contact) => ({
      value: String(contact.id),
      label: contact.name,
    }))
  } catch (error) {
    companionOptions.value = []
    loadError.value = error instanceof Error ? error.message : '联系人加载失败'
  }
}

async function loadPlanDetail() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !isEditMode.value) {
    return
  }

  try {
    const detail = await getTravelPlan(planId.value)
    formName.value = detail.name ?? ''
    formStartDate.value = detail.startDate ?? ''
    formEndDate.value = detail.endDate ?? ''
    remark.value = detail.remark ?? ''
    selectedCompanionIds.value = detail.companions.map((item) => String(item.contactId))
    existingCompanions.value = detail.companions.map((item) => ({
      id: item.id,
      contactId: item.contactId,
    }))
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '旅行详情加载失败'
  }
}

function addCompanion(option: CompanionOption) {
  if (selectedCompanionIds.value.includes(option.value)) {
    return
  }
  selectedCompanionIds.value = [...selectedCompanionIds.value, option.value]
}

function removeCompanion(companionId: string) {
  selectedCompanionIds.value = selectedCompanionIds.value.filter((item) => item !== companionId)
}

function openCompanionModal() {
  showCompanionModal.value = true
  companionKeyword.value = ''
}

function selectCompanion(option: CompanionOption) {
  addCompanion(option)
  showCompanionModal.value = false
}

async function submitCreate() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    saveError.value = '请先登录后再创建旅行'
    return
  }
  if (!formName.value.trim()) {
    saveError.value = '请填写旅行名称'
    return
  }
  if (isEditMode.value && !Number.isFinite(planId.value)) {
    saveError.value = '旅行参数不正确'
    return
  }

  isSaving.value = true
  saveError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      name: formName.value.trim(),
      startDate: formStartDate.value,
      endDate: formEndDate.value,
      remark: remark.value.trim(),
      status: 'active',
    }

    const saved = isEditMode.value
      ? await updateTravelPlan(planId.value, payload)
      : await createTravelPlan(payload)

    const currentExistingContactIds = new Set(existingCompanions.value.map((item) => String(item.contactId)))
    const companionsToCreate = selectedCompanionIds.value.filter((contactId) => !currentExistingContactIds.has(contactId))

    if (companionsToCreate.length > 0) {
      await Promise.all(
        companionsToCreate.map((contactId, index) =>
          createTravelPlanCompanion(saved.id, {
            userId: currentUser.id,
            contactId: Number(contactId),
            sortOrder: index + 1,
          }),
        ),
      )
    }

    void router.push(`/tools/travel-plans/${saved.id}`)
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : `${isEditMode.value ? '修改' : '创建'}旅行失败`
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <section class="travel-create-page">
    <PageHeader :title="isEditMode ? '修改旅行' : '新增旅行'" back-to="/tools/travel-plans" back-label="返回旅行列表" />

    <div class="travel-form">
      <label class="travel-field">
        <span class="travel-field-label">名称</span>
        <input v-model="formName" class="travel-input" type="text" placeholder="给这趟出行起个名字" />
      </label>

      <div class="travel-field">
        <span class="travel-field-label">旅行日期</span>
        <div class="travel-date-row">
          <input v-model="formStartDate" class="travel-input" type="date" />
          <span class="date-separator">-</span>
          <input v-model="formEndDate" class="travel-input" type="date" />
        </div>
      </div>

      <div class="travel-field">
        <div class="travel-field-head">
          <span class="travel-field-label">同行人</span>
          <button class="travel-link-button" type="button" @click="openCompanionModal">+ 新增同行人</button>
        </div>

        <div class="companion-chip-row">
          <button
            v-for="companion in selectedCompanions"
            :key="companion.value"
            class="companion-chip"
            type="button"
            @click="removeCompanion(companion.value)"
          >
            <span class="companion-avatar">{{ companion.label.slice(0, 1) }}</span>
            <span>{{ companion.label }}</span>
          </button>
        </div>

        <p v-if="selectedCompanions.length === 0" class="travel-tip">
          还没有添加同行人，点击右侧按钮从联系人里搜索并关联。
        </p>

        <p v-if="companionOptions.length === 0" class="travel-tip">
          {{ loadError || '还没有可关联的联系人，先去联系人页面新增。' }}
        </p>
      </div>

      <label class="travel-field">
        <span class="travel-field-label">备注</span>
        <textarea v-model="remark" class="travel-textarea" rows="4"></textarea>
      </label>
    </div>

    <p v-if="saveError" class="travel-error">{{ saveError }}</p>

    <button class="travel-submit" type="button" :disabled="isSaving" @click="submitCreate">
      {{ isSaving ? (isEditMode ? '保存中...' : '创建中...') : (isEditMode ? '保存修改' : '创建旅行') }}
    </button>

    <CommonModal v-model="showCompanionModal" title="新增同行人" size="compact">
      <div class="companion-modal-body">
        <input
          v-model="companionKeyword"
          class="travel-input modal-search-input"
          type="text"
          placeholder="搜索联系人"
        />

        <div v-if="filteredCompanions.length > 0" class="companion-search-list">
          <button
            v-for="option in filteredCompanions"
            :key="option.value"
            class="companion-search-item"
            type="button"
            @click="selectCompanion(option)"
          >
            <span class="companion-search-avatar">{{ option.label.slice(0, 1) }}</span>
            <span class="companion-search-name">{{ option.label }}</span>
          </button>
        </div>

        <p v-else class="travel-tip">没有搜索到匹配联系人。</p>
      </div>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
