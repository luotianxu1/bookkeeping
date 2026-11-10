<script setup lang="ts">
import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/business/api/modules/auth'
import { ApiError } from '@/business/api/request'

const router = useRouter()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)

const canSubmit = computed(() => !isSubmitting.value)

async function submitRegister() {
  if (!canSubmit.value) {
    return
  }

  const trimmedUsername = username.value.trim()

  errorMessage.value = ''

  // 密码不做 trim：空格属于有效密码字符
  if (!trimmedUsername || !password.value || !confirmPassword.value) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  if (password.value.length < 6) {
    errorMessage.value = '密码长度不能少于 6 位'
    return
  }

  if (password.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  isSubmitting.value = true
  try {
    await register({
      username: trimmedUsername,
      password: password.value,
    })

    await router.push({
      path: '/login',
      query: {
        account: trimmedUsername,
        registered: '1',
      },
    })
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '注册失败，请稍后再试'
  } finally {
    isSubmitting.value = false
  }
}
</script>
<template>
  <el-card shadow="never" class="pc-auth-card">
    <h1>注册账号</h1>
    <el-form label-position="top" @submit.prevent="submitRegister">
      <el-form-item label="用户名" for="username">
        <el-input
          id="username"
          v-model="username"
          type="text"
          :disabled="isSubmitting"
        />
      </el-form-item>
      <el-form-item label="密码" for="password">
        <el-input
          id="password"
          v-model="password"
          type="password"
          show-password
          autocomplete="new-password"
          maxlength="64"
          :disabled="isSubmitting"
        />
      </el-form-item>
      <el-form-item label="确认密码" for="confirmPassword">
        <el-input
          id="confirmPassword"
          v-model="confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          maxlength="64"
          :disabled="isSubmitting"
        />
      </el-form-item>
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
      />
      <el-button
        type="primary"
        native-type="submit"
        :loading="isSubmitting"
        class="full-width"
      >
        创建账号
      </el-button>
    </el-form>
    <RouterLink to="/login">返回登录</RouterLink>
  </el-card>
</template>
