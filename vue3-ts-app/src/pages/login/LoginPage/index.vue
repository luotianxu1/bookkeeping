<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/modules/auth'
import { ApiError } from '@/api/request'
import { setStoredToken } from '@/utils/auth-token'

const router = useRouter()
const phone = ref('')
const code = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

async function submitLogin() {
  if (isSubmitting.value) {
    return
  }

  errorMessage.value = ''
  if (!phone.value.trim() || !code.value.trim()) {
    errorMessage.value = '请输入手机号和验证码'
    return
  }

  isSubmitting.value = true
  try {
    const result = await login({
      username: phone.value.trim(),
      password: code.value.trim(),
    })
    setStoredToken({
      accessToken: result.accessToken,
      tokenType: result.tokenType,
    })
    await router.push('/finance')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '登录失败，请稍后再试'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <section class="login-page" aria-label="登录">
    <header class="login-header">
      <h1>登录</h1>
      <p>继续记录你们的账单、菜单、纪念日和每一天的小事。</p>
    </header>

    <div class="login-form-wrap">
      <section class="login-form-card">
        <form class="login-fields" @submit.prevent="submitLogin">
          <label class="field">
            <span>手机号</span>
            <input v-model="phone" type="tel" inputmode="numeric" placeholder="请输入手机号" />
          </label>

          <div class="field">
            <span>验证码</span>
            <div class="code-line">
              <input v-model="code" type="password" placeholder="输入验证码或密码" />
              <button type="button" class="code-btn">获取验证码</button>
            </div>
          </div>

          <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
        </form>
      </section>

      <div class="login-actions">
        <button type="button" class="login-btn" :disabled="isSubmitting" @click="submitLogin">
          {{ isSubmitting ? '登录中...' : '登录并继续' }}
        </button>
        <button type="button" class="wx-btn">微信快捷登录</button>
      </div>
      <p class="agreement">登录即表示你已阅读并同意《用户协议》和《隐私政策》</p>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
