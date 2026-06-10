<script setup lang="ts">
// 我的页：展示用户信息和个人设置入口。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import { deleteCurrentAccount, getCurrentUser, getFamilyOverview, type CurrentUser } from '@/api/modules/auth'
import { clearStoredToken } from '@/utils/auth-token'
import {
  clearStoredCurrentUser,
  getStoredCurrentUser,
  setStoredCurrentUser,
} from '@/utils/current-user'
import { useTheme } from '@/utils/theme'

type ProfileMenuItem = {
  label: string
  path?: string
  meta?: string
}

const router = useRouter()
const { isDark, setThemeMode } = useTheme()
const currentUser = ref<CurrentUser | null>(getStoredCurrentUser())
const familyMemberCount = ref<number | null>(null)
const showDeleteAccountModal = ref(false)
const isDeletingAccount = ref(false)
const deleteAccountError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const displayName = computed(() => currentUser.value?.displayName || currentUser.value?.username || '未登录用户')
const avatarText = computed(() => getAvatarText(displayName.value))
const familyMeta = computed(() => {
  if (familyMemberCount.value === null) {
    return '去管理'
  }
  return familyMemberCount.value > 0 ? `${familyMemberCount.value}人已绑定` : '去绑定'
})
const profileMenus = computed<ProfileMenuItem[]>(() => [
  { label: '家庭成员', path: '/profile/family-members', meta: familyMeta.value },
  { label: '隐私与安全' },
  { label: '消息通知' },
  { label: '关于我们' },
])
const darkModeEnabled = computed({
  get: () => isDark.value,
  set: (value: boolean) => {
    setThemeMode(value ? 'dark' : 'light')
  },
})

onMounted(() => {
  void refreshCurrentUser()
  void refreshFamilyOverview()
})

async function refreshCurrentUser() {
  try {
    const user = await getCurrentUser()
    currentUser.value = user
    setStoredCurrentUser(user)
  } catch {
    currentUser.value = null
    clearStoredCurrentUser()
  }
}

async function refreshFamilyOverview() {
  try {
    const familyOverview = await getFamilyOverview()
    familyMemberCount.value = familyOverview.hasFamily ? familyOverview.memberCount : 0
  } catch {
    familyMemberCount.value = null
  }
}

function getAvatarText(name: string) {
  const normalizedName = name.trim()
  if (!normalizedName) {
    return '--'
  }
  return normalizedName.slice(0, 2).toUpperCase()
}

async function goMenu(path?: string) {
  if (!path) {
    return
  }

  await router.push(path)
}

async function logout() {
  clearStoredToken()
  clearStoredCurrentUser()
  await router.push('/login')
}

function openDeleteAccountModal() {
  deleteAccountError.value = ''
  showDeleteAccountModal.value = true
}

function closeDeleteAccountModal() {
  if (isDeletingAccount.value) {
    return
  }

  showDeleteAccountModal.value = false
  deleteAccountError.value = ''
}

async function confirmDeleteAccount() {
  if (isDeletingAccount.value) {
    return
  }

  isDeletingAccount.value = true
  deleteAccountError.value = ''

  try {
    await deleteCurrentAccount()
    clearStoredToken()
    clearStoredCurrentUser()
    showDeleteAccountModal.value = false
    showFeedback('账号已注销', 'success')
    await router.push('/login')
  } catch (error) {
    const message = error instanceof Error ? error.message : '注销失败'
    deleteAccountError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingAccount.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="profile-page" aria-label="我的页面">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <section class="profile-user-card" aria-label="用户信息">
      <div class="avatar">{{ avatarText }}</div>
      <h2>{{ displayName }}</h2>
    </section>

    <section class="profile-menu-card" aria-label="我的功能列表">
      <button
        v-for="(item, index) in profileMenus"
        :key="item.label"
        class="menu-item"
        type="button"
        @click="goMenu(item.path)"
      >
        <span>{{ item.label }}</span>
        <span class="menu-right">
          <span v-if="item.meta" class="menu-meta">{{ item.meta }}</span>
          <span class="arrow">></span>
        </span>
        <span v-if="index < profileMenus.length - 1" class="divider" aria-hidden="true"></span>
      </button>
    </section>

    <section class="profile-theme-card" aria-label="显示设置">
      <CommonSwitch v-model="darkModeEnabled" label="暗黑模式" />
    </section>

    <div class="profile-action-group">
      <button class="logout-button" type="button" @click="logout">退出登录</button>
      <button class="delete-account-button" type="button" @click="openDeleteAccountModal">注销账号</button>
    </div>

    <CommonModal
      v-model="showDeleteAccountModal"
      title="确认注销账号"
      size="compact"
      :close-on-overlay="!isDeletingAccount"
      @close="closeDeleteAccountModal"
    >
      <p class="delete-account-text">
        注销后将停用当前账号，且无法继续使用当前账号登录，确认继续吗？
      </p>
      <p v-if="deleteAccountError" class="delete-account-error">{{ deleteAccountError }}</p>

      <template #footer>
        <div class="delete-account-actions">
          <CommonButton variant="secondary" :disabled="isDeletingAccount" @click="closeDeleteAccountModal">
            取消
          </CommonButton>
          <CommonButton :disabled="isDeletingAccount" @click="confirmDeleteAccount">
            {{ isDeletingAccount ? '注销中...' : '确认注销' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
