<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import { resetPassword } from '@/api/modules/auth'
import { getErrorMessage } from '@/api/request'
import { clearStoredCurrentUser } from '@/utils/current-user'
import { stopGoldPriceAutoRefresh } from '@/utils/gold-price-cache'
import { validateNewPassword } from '@/utils/password-validation'

const route = useRoute()
const router = useRouter()
const username = ref(typeof route.query.account === 'string' ? route.query.account : '')
const recoveryCode = ref('')
const password = ref('')
const confirmation = ref('')
const busy = ref(false)
const error = ref('')
async function submit() {
  if (busy.value) return
  error.value = validateNewPassword(password.value, confirmation.value)
  if (!username.value.trim() || !recoveryCode.value.trim()) error.value = '请输入账号和恢复码'
  if (error.value) return
  busy.value = true
  try {
    await resetPassword({ username: username.value.trim(), recoveryCode: recoveryCode.value.trim(), newPassword: password.value })
    clearStoredCurrentUser()
    stopGoldPriceAutoRefresh()
    await router.replace({ path: '/login', query: { account: username.value.trim(), passwordReset: '1' } })
  } catch (e) { error.value = getErrorMessage(e) }
  finally { busy.value = false }
}
</script>

<template>
  <section class="password-recovery-page" aria-label="找回密码">
    <RouterLink to="/login" class="back-link">‹ 返回登录</RouterLink>
    <div class="recovery-panel">
      <h1>找回密码</h1>
      <p class="security-description">使用在“隐私与安全”中保存的恢复码。重设成功后，恢复码失效，所有设备需重新登录。</p>
      <form class="security-modal-form" @submit.prevent="submit">
        <label class="security-field">账号<input v-model="username" type="text" autocomplete="username" maxlength="64" :disabled="busy" /></label>
        <label class="security-field">恢复码<input v-model="recoveryCode" type="text" autocomplete="off" autocapitalize="characters" spellcheck="false" maxlength="100" :disabled="busy" /></label>
        <label class="security-field">新密码<input v-model="password" type="password" autocomplete="new-password" maxlength="64" placeholder="6 到 64 位" :disabled="busy" /></label>
        <label class="security-field">确认新密码<input v-model="confirmation" type="password" autocomplete="new-password" maxlength="64" :disabled="busy" /></label>
        <p v-if="error" class="security-error" role="alert">{{ error }}</p>
        <CommonButton type="submit" :disabled="busy">{{ busy ? '重设中...' : '重设密码' }}</CommonButton>
      </form>
      <p class="security-description">请在记得当前密码时提前生成并保存恢复码。若密码和恢复码均已遗失，请联系系统管理员协助处理。</p>
    </div>
  </section>
</template>

<style scoped lang="scss" src="../../profile/account-settings.scss"></style>
<style scoped lang="scss">
.password-recovery-page { height: 100%; overflow-y: auto; box-sizing: border-box; width: 100%; max-width: 440px; margin: auto; padding: var(--space-20); color: var(--color-text-primary); }
.back-link { display: inline-block; margin-bottom: var(--space-20); color: var(--color-text-secondary); font-size: var(--font-size-14); text-decoration: none; }
.recovery-panel { display: grid; gap: var(--space-16); }
h1 { margin: 0; font-size: var(--font-size-24); }
</style>
