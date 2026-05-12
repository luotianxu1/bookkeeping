<script setup lang="ts">
// 我的页：展示用户信息和个人设置入口。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentUser, type CurrentUser } from '@/api/modules/auth'
import { clearStoredToken } from '@/utils/auth-token'
import {
  clearStoredCurrentUser,
  getStoredCurrentUser,
  setStoredCurrentUser,
} from '@/utils/current-user'

type ProfileMenuItem = {
  label: string
}

const router = useRouter()
const currentUser = ref<CurrentUser | null>(getStoredCurrentUser())
const displayName = computed(() => currentUser.value?.displayName || currentUser.value?.username || '未登录用户')
const avatarText = computed(() => getAvatarText(displayName.value))

const profileMenus: ProfileMenuItem[] = [
  { label: '隐私与安全' },
  { label: '消息通知' },
  { label: '关于我们' },
]

onMounted(() => {
  void refreshCurrentUser()
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

function getAvatarText(name: string) {
  const normalizedName = name.trim()
  if (!normalizedName) {
    return '--'
  }
  return normalizedName.slice(0, 2).toUpperCase()
}

async function logout() {
  clearStoredToken()
  clearStoredCurrentUser()
  await router.push('/login')
}
</script>

<template>
  <section class="profile-page" aria-label="我的页面">
    <section class="profile-user-card" aria-label="用户信息">
      <div class="avatar">{{ avatarText }}</div>
      <h1>{{ displayName }}</h1>
    </section>

    <section class="profile-menu-card" aria-label="我的功能列表">
      <button
        v-for="(item, index) in profileMenus"
        :key="item.label"
        class="menu-item"
        type="button"
      >
        <span>{{ item.label }}</span>
        <span class="arrow">></span>
        <span v-if="index < profileMenus.length - 1" class="divider" aria-hidden="true"></span>
      </button>
    </section>

    <button class="logout-button" type="button" @click="logout">退出登录</button>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
