<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

// 我的页：展示用户信息和个人设置入口。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'

import {
  deleteCurrentAccount,
  getCurrentUser,
  getFamilyOverview,
  logout as logoutApi,
  type CurrentUser,
} from '@/business/api/modules/auth'
import { stopGoldPriceAutoRefresh } from '@/business/utils/gold-price-cache'
import {
  clearStoredCurrentUser,
  getStoredCurrentUser,
  setStoredCurrentUser,
} from '@/business/utils/current-user'
import { useTheme } from '@/business/utils/theme'
import { getErrorMessage } from '@/business/api/request'

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
const displayName = computed(
  () =>
    currentUser.value?.displayName ||
    currentUser.value?.username ||
    '未登录用户',
)
const avatarText = computed(() => getAvatarText(displayName.value))
const familyMeta = computed(() => {
  if (familyMemberCount.value === null) {
    return '去管理'
  }
  return familyMemberCount.value > 0
    ? `${familyMemberCount.value}人已绑定`
    : '去绑定'
})
const profileMenus = computed<ProfileMenuItem[]>(() => [
  {
    label: '家庭成员',
    path: '/profile/family-members',
    meta: familyMeta.value,
  },
  ...(['admin', 'super_admin'].includes(currentUser.value?.roleName ?? '')
    ? [
        {
          label: '任务管理',
          path: '/profile/task-management',
          meta: '查看今日任务',
        },
      ]
    : []),
  {
    label: '统一提醒',
    path: '/profile/reminders',
    meta: '待办、账单与重要日子',
  },
  { label: '图标管理', path: '/profile/icon-management', meta: '查看系统图标' },
  { label: '隐私与安全', path: '/profile/security' },
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
  } catch (error) {
    showFeedback(getErrorMessage(error, '个人资料加载失败'), 'error')
  }
}

async function refreshFamilyOverview() {
  try {
    const familyOverview = await getFamilyOverview()
    familyMemberCount.value = familyOverview.hasFamily
      ? familyOverview.memberCount
      : 0
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
  stopGoldPriceAutoRefresh()
  try {
    // 让服务端清除 httpOnly Cookie；失败也照常清理本地状态
    await logoutApi()
  } catch {
    // 忽略登出接口异常
  }
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
    stopGoldPriceAutoRefresh()
    clearStoredCurrentUser()
    showDeleteAccountModal.value = false
    showFeedback('账号已注销', 'success')
    await router.push('/login')
  } catch (error) {
    const message = getErrorMessage(error, '注销失败')
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
  <section class="desktop-workspace">
    <PageHeader title="账号设置">
      <template #right>
        <el-button type="primary" @click="goMenu('/profile/edit')">
          编辑资料
        </el-button>
      </template>
    </PageHeader>
    <div class="desktop-main-aside">
      <PagePanel title="个人资料">
        <div class="desktop-profile-identity">
          <el-avatar :src="currentUser?.avatarUrl || undefined" :size="56">
            {{ avatarText }}
          </el-avatar>
          <div>
            <h2>{{ displayName }}</h2>
            <p class="desktop-note">{{ currentUser?.username }}</p>
          </div>
        </div>
        <el-descriptions :column="3" border class="profile-details">
          <el-descriptions-item label="手机号">
            {{ currentUser?.phone || '未设置' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ currentUser?.email || '未设置' }}
          </el-descriptions-item>
          <el-descriptions-item label="家庭成员">
            {{ familyMeta }}
          </el-descriptions-item>
        </el-descriptions>
      </PagePanel>
      <PagePanel title="偏好与会话">
        <div class="desktop-setting-row">
          <span>深色模式</span>
          <el-switch v-model="darkModeEnabled" aria-label="深色模式" />
        </div>
        <div class="desktop-bottom-actions">
          <el-button @click="logout">退出登录</el-button>
          <el-button type="danger" plain @click="openDeleteAccountModal">
            注销账号
          </el-button>
        </div>
      </PagePanel>
    </div>
    <PagePanel title="账号管理">
      <div class="desktop-settings-links">
        <el-button
          v-for="menu in profileMenus.filter((item) => item.path)"
          :key="menu.path"
          @click="goMenu(menu.path)"
        >
          {{ menu.label }}
        </el-button>
      </div>
    </PagePanel>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
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
      <p v-if="deleteAccountError" class="delete-account-error">
        {{ deleteAccountError }}
      </p>
      <template #footer>
        <div class="pc-toolbar delete-account-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeletingAccount"
            @click="closeDeleteAccountModal"
          >
            取消
          </CommonButton>
          <CommonButton
            :disabled="isDeletingAccount"
            @click="confirmDeleteAccount"
          >
            {{ isDeletingAccount ? '注销中...' : '确认注销' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
