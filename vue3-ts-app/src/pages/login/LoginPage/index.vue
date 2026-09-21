<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCurrentUser, login } from '@/api/modules/auth'
import { ApiError } from '@/api/request'
import { clearStoredToken, setStoredToken } from '@/utils/auth-token'
import { clearStoredCurrentUser, setStoredCurrentUser } from '@/utils/current-user'
import { initializeGoldPriceCache } from '@/utils/gold-price-cache'

const router = useRouter()
const route = useRoute()
const account = ref('')
const password = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const isSubmitting = ref(false)
const showPassword = ref(false)

onMounted(() => {
  const routeAccount = typeof route.query.account === 'string' ? route.query.account : ''
  const registered = route.query.registered === '1'

  if (routeAccount) {
    account.value = routeAccount
  }

  if (registered) {
    successMessage.value = '注册成功，请登录后继续'
  }
})

async function submitLogin() {
  if (isSubmitting.value) {
    return
  }

  errorMessage.value = ''
  successMessage.value = ''
  if (!account.value.trim() || !password.value.trim()) {
    errorMessage.value = '请输入账号和密码'
    return
  }

  isSubmitting.value = true
  try {
    const result = await login({
      username: account.value.trim(),
      password: password.value.trim(),
    })
    setStoredToken({
      accessToken: result.accessToken,
      tokenType: result.tokenType,
    })
    const currentUser = await getCurrentUser()
    setStoredCurrentUser(currentUser)
    await initializeGoldPriceCache()
    await router.push('/')
  } catch (error) {
    clearStoredToken()
    clearStoredCurrentUser()
    errorMessage.value = error instanceof ApiError ? error.message : '登录失败，请稍后再试'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <section class="login-page" aria-label="登录">
    <aside class="login-brand-panel" aria-label="产品介绍">
      <div class="brand-topline">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none">
            <path d="M8 9.5 16 5l8 4.5v13L16 27l-8-4.5v-13Z" stroke="currentColor" stroke-width="2" />
            <path d="M8.5 10 16 14l7.5-4M16 14v12" stroke="currentColor" stroke-width="2" />
            <path d="m12.5 19 2.3 2.3 4.7-5" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
          </svg>
        </span>
        <span class="brand-name">一页账本</span>
      </div>

      <div class="brand-copy">
        <span class="brand-eyebrow">YOUR HOME, IN SYNC</span>
        <h1>把日子过明白，<br /><em>把每笔都记得。</em></h1>
        <p>收好家庭的收支、菜单和重要日子，让生活的每一面都井然有序。</p>
      </div>

      <div class="ledger-preview" aria-label="账本预览">
        <div class="preview-heading">
          <span>本月结余</span>
          <span class="preview-status"><i></i> 稳定</span>
        </div>
        <strong>¥ 8,420.00</strong>
        <div class="preview-line"><span>收入</span><b>¥ 18,600</b></div>
        <div class="preview-line"><span>支出</span><b>¥ 10,180</b></div>
        <div class="preview-track"><span></span></div>
      </div>

      <p class="brand-footnote"><span class="brand-footnote-mark" aria-hidden="true"></span> 只为你和家人保留的生活空间</p>
    </aside>

    <div class="login-content">
      <div class="login-form-wrap">
        <div class="login-header">
          <span class="login-kicker">欢迎回来</span>
          <h2>登录你的账本</h2>
          <p>继续管理你们的每个重要时刻。</p>
        </div>

        <form class="login-form" @submit.prevent="submitLogin">
          <label class="field">
            <span>账号</span>
            <span class="input-shell">
              <svg class="field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M20 21a8 8 0 0 0-16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
              </svg>
              <input v-model="account" type="text" autocomplete="username" placeholder="请输入用户名" />
            </span>
          </label>

          <label class="field">
            <span>密码</span>
            <span class="input-shell">
              <svg class="field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <rect x="5" y="10" width="14" height="11" rx="2" stroke="currentColor" stroke-width="1.8" />
                <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" stroke="currentColor" stroke-linecap="round" stroke-width="1.8" />
              </svg>
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 4.4A10.8 10.8 0 0 1 12 4c5 0 8.5 4 9.5 6a11.6 11.6 0 0 1-3.2 3.7M6.2 6.2A11.5 11.5 0 0 0 2.5 10c1 2 4.5 6 9.5 6 1 0 1.9-.2 2.8-.5" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.7" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" stroke="currentColor" stroke-linejoin="round" stroke-width="1.7" />
                  <circle cx="12" cy="12" r="2.5" stroke="currentColor" stroke-width="1.7" />
                </svg>
              </button>
            </span>
          </label>

          <p v-if="successMessage" class="form-message form-message--success" role="status">
            <span aria-hidden="true">✓</span> {{ successMessage }}
          </p>
          <p v-if="errorMessage" class="form-message form-message--error" role="alert">
            <span aria-hidden="true">!</span> {{ errorMessage }}
          </p>

          <button type="submit" class="login-btn" :disabled="isSubmitting">
            <span>{{ isSubmitting ? '正在登录...' : '登录并继续' }}</span>
            <svg v-if="!isSubmitting" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 12h13M13 6l6 6-6 6" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
            </svg>
            <span v-else class="button-spinner" aria-hidden="true"></span>
          </button>
        </form>

        <div class="register-entry">
          <span>还没有账号？</span>
          <RouterLink class="register-link" to="/register">
            创建新账号
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 12h13M13 6l6 6-6 6" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
            </svg>
          </RouterLink>
        </div>

        <p class="agreement">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M6 10V8a6 6 0 0 1 12 0v2M5 10h14v10H5V10Z" stroke="currentColor" stroke-linejoin="round" stroke-width="1.6" />
          </svg>
          你的数据只属于你的家庭空间
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
