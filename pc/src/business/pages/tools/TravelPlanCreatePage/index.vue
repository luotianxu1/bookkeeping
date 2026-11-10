<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSkeleton from '@/business/components/common/CommonSkeleton/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  createTravelPlan,
  createTravelPlanCompanion,
  getContacts,
  getTravelPlan,
  type Contact,
  updateTravelPlan,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

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
const today = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
}).format(new Date())
const formStartDate = ref(today)
const formEndDate = ref(today)
const selectedCompanionIds = ref<string[]>([])
const companionOptions = ref<CompanionOption[]>([])
const remark = ref('')
const isSaving = ref(false)
const saveError = ref('')
const loadError = ref('')
const showCompanionModal = ref(false)
const companionKeyword = ref('')
const existingCompanions = ref<ExistingCompanion[]>([])
// 编辑模式需按 planId 加载旅行详情，加载期间显示骨架屏；新建模式不加载
const isLoadingDetail = ref(isEditMode.value)

const selectedCompanions = computed(() =>
  companionOptions.value.filter((item) =>
    selectedCompanionIds.value.includes(item.value),
  ),
)

const filteredCompanions = computed(() => {
  const keyword = companionKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return companionOptions.value
  }

  return companionOptions.value.filter((item) =>
    item.label.toLowerCase().includes(keyword),
  )
})

onMounted(() => {
  void initializePage()
})

async function initializePage() {
  await loadContacts()
  if (isEditMode.value) {
    await loadPlanDetail()
  }
  isLoadingDetail.value = false
}

async function loadContacts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    loadError.value = `请先登录后再${isEditMode.value ? '修改' : '创建'}旅行`
    return
  }

  try {
    const contacts = await getContacts({
      userId: currentUser.id,
      status: 'active',
    })
    companionOptions.value = contacts.map((contact: Contact) => ({
      value: String(contact.id),
      label: contact.name,
    }))
  } catch (error) {
    companionOptions.value = []
    loadError.value = getErrorMessage(error, '联系人加载失败')
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
    selectedCompanionIds.value = detail.companions.map((item) =>
      String(item.contactId),
    )
    existingCompanions.value = detail.companions.map((item) => ({
      id: item.id,
      contactId: item.contactId,
    }))
  } catch (error) {
    loadError.value = getErrorMessage(error, '旅行详情加载失败')
  }
}

function addCompanion(option: CompanionOption) {
  if (selectedCompanionIds.value.includes(option.value)) {
    return
  }
  selectedCompanionIds.value = [...selectedCompanionIds.value, option.value]
}

function removeCompanion(companionId: string) {
  selectedCompanionIds.value = selectedCompanionIds.value.filter(
    (item) => item !== companionId,
  )
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

    const currentExistingContactIds = new Set(
      existingCompanions.value.map((item) => String(item.contactId)),
    )
    const companionsToCreate = selectedCompanionIds.value.filter(
      (contactId) => !currentExistingContactIds.has(contactId),
    )

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
    saveError.value = getErrorMessage(
      error,
      `${isEditMode.value ? '修改' : '创建'}旅行失败`,
    )
  } finally {
    isSaving.value = false
  }
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="isEditMode ? '修改旅行' : '新增旅行'">
      <template #right>
        <el-button
          type="primary"
          :loading="isSaving"
          :disabled="isLoadingDetail"
          @click="submitCreate"
        >
          保存旅行
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="saveError"
      :title="saveError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <CommonSkeleton v-if="isLoadingDetail" />
    <div v-else class="desktop-main-aside">
      <PagePanel title="基本信息">
        <div class="desktop-form-grid travel-form">
          <label class="pc-field travel-field">
            <span class="travel-field-label">名称</span>
            <DesktopInput
              v-model="formName"
              class="travel-input"
              type="text"
              placeholder="给这趟出行起个名字"
            />
          </label>
          <div class="travel-field">
            <span class="travel-field-label">旅行日期</span>
            <div class="travel-date-row">
              <DesktopInput
                v-model="formStartDate"
                class="travel-input"
                type="date"
              />
              <span class="date-separator">-</span>
              <DesktopInput
                v-model="formEndDate"
                class="travel-input"
                type="date"
              />
            </div>
          </div>
          <div class="travel-field">
            <div class="travel-field-head">
              <span class="travel-field-label">同行人</span>
              <el-button
                class="travel-link-button"
                native-type="button"
                @click="openCompanionModal"
              >
                + 新增同行人
              </el-button>
            </div>
            <div class="companion-chip-row">
              <el-button
                v-for="companion in selectedCompanions"
                :key="companion.value"
                class="companion-chip"
                native-type="button"
                @click="removeCompanion(companion.value)"
              >
                <span class="companion-avatar">
                  {{ companion.label.slice(0, 1) }}
                </span>
                <span>{{ companion.label }}</span>
              </el-button>
            </div>
            <p v-if="companionOptions.length === 0" class="travel-tip">
              {{ loadError || '还没有可关联的联系人，先去联系人页面新增。' }}
            </p>
          </div>
          <label class="pc-field travel-field">
            <span class="travel-field-label">备注</span>
            <DesktopInput
              v-model="remark"
              class="travel-textarea"
              rows="4"
              type="textarea"
            ></DesktopInput>
          </label>
        </div>
      </PagePanel>
      <PagePanel title="同行人">
        <el-tag
          v-for="companion in selectedCompanions"
          :key="companion.value"
          closable
          @close="removeCompanion(companion.value)"
        >
          {{ companion.label }}
        </el-tag>
        <el-button @click="openCompanionModal">添加同行人</el-button>
      </PagePanel>
    </div>
    <CommonModal v-model="showCompanionModal" title="新增同行人" size="compact">
      <div class="companion-modal-body">
        <DesktopInput
          v-model="companionKeyword"
          class="travel-input modal-search-input"
          type="text"
          placeholder="搜索联系人"
        />
        <div
          v-if="filteredCompanions.length > 0"
          class="pc-grid companion-search-list"
        >
          <el-button
            v-for="option in filteredCompanions"
            :key="option.value"
            class="pc-content-button companion-search-item"
            native-type="button"
            @click="selectCompanion(option)"
          >
            <span class="companion-search-avatar">
              {{ option.label.slice(0, 1) }}
            </span>
            <span class="companion-search-name">{{ option.label }}</span>
          </el-button>
        </div>
        <p v-else class="travel-tip">没有搜索到匹配联系人。</p>
      </div>
    </CommonModal>
  </section>
</template>
