<script setup lang="ts">
// 联系人管理页：按最新画板还原为纯列表页，并保留增删改查弹窗。
import { computed, onMounted, ref } from 'vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { createContact, deleteContact, getContacts, updateContact, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type ContactCardPreset = {
  variant: string
  topMeta: string
  phoneText: string
  statusLabel?: string
  bottomNote?: string
}

type ContactDisplayCard = Contact & ContactCardPreset & {
  avatarLetter: string
}

const contacts = ref<Contact[]>([])
const isLoading = ref(false)
const isSaving = ref(false)
const isDeleting = ref(false)
const pageError = ref('')
const formError = ref('')
const isManageMode = ref(false)
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

const contactPresets: Record<string, ContactCardPreset> = {
  王琳: {
    variant: 'wanglin',
    topMeta: '备注：母亲 · 生日 6/18',
    phoneText: '138****2001',
  },
  陈叙: {
    variant: 'chenxu',
    topMeta: '微信已绑 · 最近联系 2 天前',
    phoneText: '186****6620',
    statusLabel: '已同步',
    bottomNote: '备注：球友',
  },
  李阿姨: {
    variant: 'liayi',
    topMeta: '装修尾款 · 逾期 5 天',
    phoneText: '177****9054',
  },
}

const displayContacts = computed<ContactDisplayCard[]>(() =>
  [...contacts.value].sort((left, right) => {
    const sortDiff = (left.sortOrder ?? 0) - (right.sortOrder ?? 0)
    if (sortDiff !== 0) {
      return sortDiff
    }

    return left.id - right.id
  }).map((contact) => {
    const preset = getPreset(contact)
    return {
      ...contact,
      ...preset,
      avatarLetter: contact.name.trim().slice(0, 1) || '联',
    }
  }),
)

onMounted(() => {
  void loadContacts()
})

function getPreset(contact: Contact): ContactCardPreset {
  const preset = contactPresets[contact.name.trim()]
  if (preset) {
    return preset
  }

  const tone = getFallbackTone(contact)
  return {
    variant: tone,
    topMeta: contact.remark ? `备注：${contact.remark}` : '备注：暂无',
    phoneText: maskPhone(contact.phone),
  }
}

function getFallbackTone(contact: Contact) {
  const content = `${contact.name}${contact.remark ?? ''}`
  if (/(朋友|球友|同学|同事|闺蜜|哥们)/.test(content)) {
    return 'friend' as const
  }
  if (/(尾款|逾期|待收|商户|客户)/.test(content)) {
    return 'merchant' as const
  }
  return 'family' as const
}

function maskPhone(phone?: string | null) {
  if (!phone) {
    return ''
  }

  const digits = phone.replace(/\s+/g, '')
  if (digits.length <= 4) {
    return digits
  }

  return `${digits.slice(0, 3)}****${digits.slice(-4)}`
}

function openCreateModal() {
  editingContact.value = null
  resetForm()
  showContactModal.value = true
}

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function showContactFooter(contact: ContactDisplayCard) {
  return Boolean(contact.bottomNote) || isManageMode.value
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
    pageError.value = error instanceof Error ? error.message : '联系人加载失败'
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
    const message = error instanceof Error ? error.message : '联系人保存失败'
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
    const message = error instanceof Error ? error.message : '删除失败'
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function getNextSortOrder() {
  return contacts.value.reduce((maxOrder, contact) => Math.max(maxOrder, contact.sortOrder ?? 0), 0) + 10
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

</script>

<template>
  <CommonFeedback
    v-model="showFeedbackModal"
    :message="feedbackMessage"
    :type="feedbackType"
  />

  <section class="contacts-page" aria-label="联系人管理">
    <PageHeader title="联系人管理" back-to="/tools" back-label="返回工具页" prefer-back-to>
      <button type="button" class="contacts-manage-button" @click="toggleManageMode">
        {{ isManageMode ? '完成' : '编辑' }}
      </button>
    </PageHeader>

    <p v-if="pageError" class="contacts-message contacts-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />
    <p v-else-if="displayContacts.length === 0" class="contacts-message">
      暂无联系人
    </p>

    <section v-else class="contacts-list" aria-label="联系人卡片列表">
      <article
        v-for="contact in displayContacts"
        :key="contact.id"
        :class="['contact-card', `variant-${contact.variant}`]"
      >
        <div class="contact-card-top">
          <div class="contact-card-person">
            <span class="contact-avatar">
              {{ contact.avatarLetter }}
            </span>
            <div class="contact-card-info">
              <div class="contact-card-name-row">
                <strong>{{ contact.name }}</strong>
                <p>{{ contact.topMeta }}</p>
              </div>
            </div>
          </div>
          <div class="contact-card-meta">
            <strong v-if="contact.phoneText">
              {{ contact.phoneText }}
            </strong>
            <span v-if="contact.statusLabel" class="contact-status-pill">
              {{ contact.statusLabel }}
            </span>
          </div>
        </div>

        <div v-if="showContactFooter(contact)" class="contact-card-bottom">
          <span v-if="contact.bottomNote" class="contact-card-note">{{ contact.bottomNote }}</span>
          <div v-if="isManageMode" class="contact-card-actions">
            <button type="button" class="contact-action contact-action-edit" @click="openEditModal(contact)">
              编辑
            </button>
            <button type="button" class="contact-action contact-action-delete" @click="openDeleteModal(contact)">
              删除
            </button>
          </div>
        </div>
      </article>
    </section>

    <FloatingAddButton aria-label="新增联系人" @click="openCreateModal" />
  </section>

  <CommonModal
    v-model="showContactModal"
    :title="editingContact ? '编辑联系人' : '新增联系人'"
  >
    <div class="contact-modal">
      <CommonInput v-model="formName" label="姓名" placeholder="输入联系人姓名" />
      <CommonInput v-model="formPhone" label="手机号" placeholder="输入手机号" />
      <label class="contact-modal-field">
        <span>备注</span>
        <textarea
          v-model="formRemark"
          rows="4"
          placeholder="可填写称呼、关系或提醒信息"
        ></textarea>
      </label>
      <p v-if="formError" class="contact-form-error">
        {{ formError }}
      </p>
    </div>

    <template #footer>
      <div class="contact-modal-actions">
        <button type="button" class="contact-modal-button contact-modal-cancel" @click="closeContactModal()">
          取消
        </button>
        <button type="button" class="contact-modal-button contact-modal-save" @click="saveContact">
          {{ editingContact ? '保存修改' : '保存联系人' }}
        </button>
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
      <div class="contact-modal-actions">
        <button type="button" class="contact-modal-button contact-modal-cancel" @click="closeDeleteModal()">
          取消
        </button>
        <button type="button" class="contact-modal-button contact-modal-delete" @click="confirmDeleteContact">
          确认删除
        </button>
      </div>
    </template>
  </CommonModal>
</template>

<style scoped lang="scss" src="./style.scss"></style>
