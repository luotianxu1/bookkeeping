<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'

import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

// 联系人管理：搜索、排序、分页与增删改查对话框。
import { onMounted, ref } from 'vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  createContact,
  deleteContact,
  getContacts,
  updateContact,
  type Contact,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const contacts = ref<Contact[]>([])
const isLoading = ref(true)
const isSaving = ref(false)
const isDeleting = ref(false)
const pageError = ref('')
const formError = ref('')

const showContactModal = ref(false)
const showDeleteModal = ref(false)
const editingContact = ref<Contact | null>(null)
const deletingContact = ref<Contact | null>(null)
const formName = ref('')
const formPhone = ref('')
const formRemark = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

onMounted(() => {
  void loadContacts()
})

function openCreateModal() {
  editingContact.value = null
  resetForm()
  showContactModal.value = true
}

function openEditModal(contact: Contact) {
  editingContact.value = contact
  formName.value = contact.name
  formPhone.value = contact.phone ?? ''
  formRemark.value = contact.remark ?? ''
  formError.value = ''
  showContactModal.value = true
}

function closeContactModal(force = false) {
  if (isSaving.value && !force) {
    return
  }

  showContactModal.value = false
  editingContact.value = null
  resetForm()
}

function resetForm() {
  formName.value = ''
  formPhone.value = ''
  formRemark.value = ''
  formError.value = ''
}

function openDeleteModal(contact: Contact) {
  deletingContact.value = contact
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }

  showDeleteModal.value = false
  deletingContact.value = null
}

async function loadContacts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看联系人'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    contacts.value = await getContacts({ userId: currentUser.id })
  } catch (error) {
    pageError.value = getErrorMessage(error, '联系人加载失败')
  } finally {
    isLoading.value = false
  }
}

async function saveContact() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const trimmedName = formName.value.trim()
  const trimmedPhone = formPhone.value.trim()
  const trimmedRemark = formRemark.value.trim()
  const isEditing = Boolean(editingContact.value)

  if (!currentUser) {
    formError.value = '请先登录后再保存联系人'
    return
  }

  if (!trimmedName) {
    formError.value = '请输入联系人姓名'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      name: trimmedName,
      phone: trimmedPhone || null,
      remark: trimmedRemark || null,
      sortOrder: editingContact.value?.sortOrder ?? getNextSortOrder(),
      status: editingContact.value?.status ?? 'active',
    }

    if (editingContact.value) {
      await updateContact(editingContact.value.id, payload)
    } else {
      await createContact(payload)
    }

    closeContactModal(true)
    showFeedback(isEditing ? '修改成功' : '新增成功', 'success')
    await loadContacts()
  } catch (error) {
    const message = getErrorMessage(error, '联系人保存失败')
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function confirmDeleteContact() {
  const contact = deletingContact.value
  if (!contact) {
    return
  }

  isDeleting.value = true

  try {
    await deleteContact(contact.id)
    closeDeleteModal(true)
    showFeedback('删除成功', 'success')
    await loadContacts()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function getNextSortOrder() {
  return (
    contacts.value.reduce(
      (maxOrder, contact) => Math.max(maxOrder, contact.sortOrder ?? 0),
      0,
    ) + 10
  )
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

type DesktopRow = (typeof contacts.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="联系人">
      <template #right>
        <el-button type="primary" @click="openCreateModal">
          新增联系人
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <MetricStrip
      :items="[
        { label: '联系人', value: contacts.length },
        {
          label: '已填写电话',
          value: contacts.filter((item) => item.phone).length,
        },
        {
          label: '有备注',
          value: contacts.filter((item) => item.remark).length,
        },
      ]"
    />
    <DesktopTable
      title="通讯录"
      searchable
      search-placeholder="姓名 / 电话 / 备注"
      :rows="contacts"
      :loading="isLoading"
    >
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="姓名"
        prop="name"
        sortable="custom"
        min-width="160"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="电话"
        prop="phone"
        sortable="custom"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="remark"
        sortable="custom"
        min-width="260"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button link type="danger" @click="openDeleteModal(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showContactModal"
      :title="editingContact ? '编辑联系人' : '新增联系人'"
    >
      <div class="contact-modal">
        <CommonInput
          v-model="formName"
          label="姓名"
          placeholder="输入联系人姓名"
        />
        <CommonInput
          v-model="formPhone"
          label="手机号"
          placeholder="输入手机号"
        />
        <label class="pc-field contact-modal-field">
          <span>备注</span>
          <DesktopInput
            v-model="formRemark"
            rows="4"
            placeholder="可填写称呼、关系或提醒信息"
            type="textarea"
          ></DesktopInput>
        </label>
        <p v-if="formError" class="contact-form-error">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar contact-modal-actions">
          <el-button
            native-type="button"
            class="contact-modal-button contact-modal-cancel"
            @click="closeContactModal()"
          >
            取消
          </el-button>
          <el-button
            native-type="button"
            class="contact-modal-button contact-modal-save"
            @click="saveContact"
            type="primary"
          >
            {{ editingContact ? '保存修改' : '保存联系人' }}
          </el-button>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteModal"
      title="删除联系人"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="contact-delete-message">
        确认删除“{{ deletingContact?.name }}”吗？删除后无法恢复。
      </p>
      <template #footer>
        <div class="pc-toolbar contact-modal-actions">
          <el-button
            native-type="button"
            class="contact-modal-button contact-modal-cancel"
            @click="closeDeleteModal()"
          >
            取消
          </el-button>
          <el-button
            native-type="button"
            class="contact-modal-button contact-modal-delete"
            @click="confirmDeleteContact"
            type="danger"
            plain
          >
            确认删除
          </el-button>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
