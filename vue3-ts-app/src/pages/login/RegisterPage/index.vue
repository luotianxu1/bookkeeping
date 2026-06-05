<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/modules/auth'
import { ApiError } from '@/api/request'

const router = useRouter()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

const canSubmit = computed(() => !isSubmitting.value)

async function submitRegister() {
  if (!canSubmit.value) {
    return
  }

  const trimmedUsername = username.value.trim()
  const trimmedPassword = password.value.trim()
  const trimmedConfirmPassword = confirmPassword.value.trim()

  errorMessage.value = ''

  if (!trimmedUsername || !trimmedPassword || !trimmedConfirmPassword) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  if (trimmedPassword.length < 6) {
    errorMessage.value = '密码长度不能少于 6 位'
    return
  }

  if (trimmedPassword !== trimmedConfirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  isSubmitting.value = true
  try {
    await register({
      username: trimmedUsername,
      password: trimmedPassword,
    })

    await router.push({
      path: '/login',
      query: {
        account: trimmedUsername,
        registered: '1',
      },
    })
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '注册失败，请稍后再试'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <section class="register-page" aria-label="注册">
    <div class="register-header">
      <h1>注册</h1>
      <p>使用用户名和密码创建新账号。</p>
    </div>

    <div class="register-form-wrap">
      <section class="register-form-card">
        <form class="register-fields" @submit.prevent="submitRegister">
          <label class="field">
            <span>用户名</span>
            <input v-model="username" type="text" autocomplete="username" placeholder="请输入用户名" />
          </label>

          <label class="field">
            <span>密码</span>
            <input
              v-model="password"
              type="password"
              autocomplete="new-password"
              placeholder="请输入密码，至少 6 位"
            />
          </label>

          <label class="field">
            <span>确认密码</span>
            <input
              v-model="confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="请再次输入密码"
            />
          </label>

          <p v-if="errorMessage" class="register-error">{{ errorMessage }}</p>
        </form>
      </section>

      <div class="register-actions">
        <button type="button" class="register-btn" :disabled="isSubmitting" @click="submitRegister">
          {{ isSubmitting ? '注册中...' : '使用用户名注册' }}
        </button>
      </div>

      <p class="register-link-text">
        已有账号？
        <RouterLink class="register-link" to="/login">去登录</RouterLink>
      </p>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
