<script setup lang="ts">
import DesktopInput from '@/business/components/DesktopInput.vue'

import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import { resetPassword } from '@/business/api/modules/auth'
import { getErrorMessage } from '@/business/api/request'
import { clearStoredCurrentUser } from '@/business/utils/current-user'
import { stopGoldPriceAutoRefresh } from '@/business/utils/gold-price-cache'
import { validateNewPassword } from '@/business/utils/password-validation'

const route = useRoute()
const router = useRouter()
const username = ref(
  typeof route.query.account === 'string' ? route.query.account : '',
)
const recoveryCode = ref('')
const password = ref('')
const confirmation = ref('')
const busy = ref(false)
const error = ref('')
async function submit() {
  if (busy.value) return
  error.value = validateNewPassword(password.value, confirmation.value)
  if (!username.value.trim() || !recoveryCode.value.trim())
    error.value = '请输入账号和恢复码'
  if (error.value) return
  busy.value = true
  try {
    await resetPassword({
      username: username.value.trim(),
      recoveryCode: recoveryCode.value.trim(),
      newPassword: password.value,
    })
    clearStoredCurrentUser()
    stopGoldPriceAutoRefresh()
    await router.replace({
      path: '/login',
      query: { account: username.value.trim(), passwordReset: '1' },
    })
  } catch (e) {
    error.value = getErrorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <el-card shadow="never" class="pc-auth-card">
    <h1>找回密码</h1>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="账号" for="username">
        <el-input
          id="username"
          v-model="username"
          type="text"
          :disabled="busy"
        />
      </el-form-item>
      <el-form-item label="恢复码" for="recoveryCode">
        <el-input
          id="recoveryCode"
          v-model="recoveryCode"
          type="text"
          :disabled="busy"
        />
      </el-form-item>
      <el-form-item label="新密码" for="password">
        <el-input
          id="password"
          v-model="password"
          type="password"
          show-password
          autocomplete="new-password"
          maxlength="64"
          :disabled="busy"
        />
      </el-form-item>
      <el-form-item label="确认新密码" for="confirmation">
        <el-input
          id="confirmation"
          v-model="confirmation"
          type="password"
          show-password
          autocomplete="new-password"
          maxlength="64"
          :disabled="busy"
        />
      </el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
        class="full-width"
      >
        重设密码
      </el-button>
    </el-form>
    <RouterLink to="/login">返回登录</RouterLink>
  </el-card>
</template>
