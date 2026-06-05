<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getCurrentUser, login } from '@/api/modules/auth'
import { ApiError } from '@/api/request'
import { clearStoredToken, setStoredToken } from '@/utils/auth-token'
import { clearStoredCurrentUser, setStoredCurrentUser } from '@/utils/current-user'

const router = useRouter()
const route = useRoute()
const account = ref('')
const password = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const isSubmitting = ref(false)

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
    <div class="login-header">
      <PageHeader title="登录" :show-back="false" />
      <p>继续记录你们的账单、菜单、纪念日和每一天的小事。</p>
    </div>

    <div class="login-form-wrap">
      <section class="login-form-card">
        <form class="login-fields" @submit.prevent="submitLogin">
          <label class="field">
            <span>账号</span>
            <input v-model="account" type="text" autocomplete="username" placeholder="请输入账号" />
          </label>

          <div class="field">
            <span>密码</span>
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </div>

          <p v-if="successMessage" class="login-success">{{ successMessage }}</p>
          <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
        </form>
      </section>

      <div class="login-actions">
        <button type="button" class="login-btn" :disabled="isSubmitting" @click="submitLogin">
          {{ isSubmitting ? '登录中...' : '登录并继续' }}
        </button>
      </div>
      <p class="register-entry">
        还没有账号？
        <RouterLink class="register-link" to="/register">立即注册</RouterLink>
      </p>
      <p class="agreement">登录即表示你已阅读并同意《用户协议》和《隐私政策》</p>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
