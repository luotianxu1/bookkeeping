<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, PanelsTopLeft } from '@lucide/vue'
import { api } from '@/api/admin'
import { clearSession, loadSession, session } from '@/stores/session'
import { safeRedirect } from '@/utils/permissions'
const form = reactive({ username: '', password: '' })
const busy = ref(false)
const error = ref('')
const router = useRouter()
const route = useRoute()
async function submit() {
  if (busy.value) return
  error.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请输入账号和密码'
    return
  }
  busy.value = true
  try {
    await api.login(form.username.trim(), form.password)
    clearSession()
    await loadSession()
    await router.replace(
      session.denied
        ? '/forbidden'
        : session.data
          ? safeRedirect(route.query.redirect)
          : '/login',
    )
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-form-card" shadow="never">
      <div class="login-brand">
        <span class="brand-symbol"><PanelsTopLeft :size="22" /></span>
        <span class="brand-text">Life Hub</span>
        <el-tag disable-transitions type="info" size="small">PC 管理端</el-tag>
      </div>
      <h1>欢迎回来</h1>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
          class="spaced-alert"
        />
        <el-form-item label="账号" for="username">
          <el-input
            id="username"
            v-model="form.username"
            placeholder="用户名 / 手机号 / 邮箱"
            autocomplete="username"
            :maxlength="255"
            :disabled="busy"
          />
        </el-form-item>
        <el-form-item label="密码" for="password">
          <el-input
            id="password"
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            :maxlength="64"
            :disabled="busy"
          />
        </el-form-item>
        <el-button
          class="login-submit"
          native-type="submit"
          type="primary"
          :loading="busy"
        >
          登录工作台
          <ArrowRight :size="17" />
        </el-button>
      </el-form>
      <div class="login-links">
        <RouterLink to="/register">注册账号</RouterLink>
        <RouterLink to="/forgot-password">忘记密码</RouterLink>
      </div>
    </el-card>
  </main>
</template>
