<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import { changePassword, generateRecoveryCode, getLoginSessions, getRecoveryCodeStatus, revokeLoginSession,
  revokeOtherLoginSessions, type LoginSession, type RecoveryCodeStatus } from '@/api/modules/auth'
import { getErrorMessage } from '@/api/request'
import { clearStoredCurrentUser } from '@/utils/current-user'
import { stopGoldPriceAutoRefresh } from '@/utils/gold-price-cache'
import { validateNewPassword } from '@/utils/password-validation'

const router = useRouter()
const sessions = ref<LoginSession[]>([])
const recovery = ref<RecoveryCodeStatus>({ configured: false, createdAt: null })
const loading = ref(true)
const pageError = ref('')
const busy = ref(false)
const modal = ref<'password' | 'recovery' | 'revoke' | ''>('')
const modalOpen = computed({ get: () => !!modal.value, set: (value) => { if (!value) close() } })
const modalTitle = computed(() => modal.value === 'password' ? '修改密码' : modal.value === 'recovery' ? '账号恢复码' : '退出登录设备')
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const modalError = ref('')
const recoveryCode = ref('')
const revokeTarget = ref<LoginSession | null>(null)
const feedback = ref(false)
const feedbackMessage = ref('')
const otherSessions = computed(() => sessions.value.filter(session => !session.current))

onMounted(load)
async function load() {
  loading.value = true
  pageError.value = ''
  const results = await Promise.allSettled([getLoginSessions(), getRecoveryCodeStatus()])
  if (results[0].status === 'fulfilled') sessions.value = results[0].value
  else pageError.value = getErrorMessage(results[0].reason)
  if (results[1].status === 'fulfilled') recovery.value = results[1].value
  else pageError.value = getErrorMessage(results[1].reason)
  loading.value = false
}
function open(kind: 'password' | 'recovery' | 'revoke', target: LoginSession | null = null) {
  modalError.value = ''; currentPassword.value = ''; newPassword.value = ''; confirmPassword.value = ''; recoveryCode.value = ''
  revokeTarget.value = target
  modal.value = kind
}
function close() {
  if (busy.value) return
  modal.value = ''; currentPassword.value = ''; newPassword.value = ''; confirmPassword.value = ''; recoveryCode.value = ''; modalError.value = ''
}
function notify(message: string) { feedbackMessage.value = message; feedback.value = true }
async function goLogin(passwordChanged = false) {
  stopGoldPriceAutoRefresh()
  clearStoredCurrentUser()
  await router.replace({ path: '/login', query: passwordChanged ? { passwordChanged: '1' } : {} })
}
async function submit() {
  if (busy.value) return
  modalError.value = ''
  if (modal.value === 'password') {
    modalError.value = validateNewPassword(newPassword.value, confirmPassword.value)
    if (!currentPassword.value) modalError.value = '请输入当前密码'
    if (modalError.value) return
  } else if (modal.value === 'recovery' && !currentPassword.value) {
    modalError.value = '请输入当前密码'; return
  }
  busy.value = true
  try {
    if (modal.value === 'password') {
      await changePassword({ currentPassword: currentPassword.value, newPassword: newPassword.value })
      await goLogin(true)
    } else if (modal.value === 'recovery') {
      const result = await generateRecoveryCode(currentPassword.value)
      recoveryCode.value = result.code
      currentPassword.value = ''
      recovery.value = { configured: true, createdAt: new Date().toISOString() }
    } else {
      if (revokeTarget.value) {
        await revokeLoginSession(revokeTarget.value.id)
        if (revokeTarget.value.current) { await goLogin(); return }
        sessions.value = sessions.value.filter(session => session.id !== revokeTarget.value?.id)
      } else {
        await revokeOtherLoginSessions()
        sessions.value = sessions.value.filter(session => session.current)
      }
      modal.value = ''
      notify('所选设备已退出登录')
    }
  } catch (e) { modalError.value = getErrorMessage(e) }
  finally { busy.value = false }
}
async function copyCode() {
  try { await navigator.clipboard.writeText(recoveryCode.value); notify('恢复码已复制') }
  catch { modalError.value = '复制失败，请长按恢复码手动复制' }
}
function date(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
</script>

<template>
  <section class="account-settings-page" aria-label="隐私与安全">
    <PageHeader title="隐私与安全" back-to="/profile" prefer-back-to>
      <template #right><CommonHeaderRefreshButton label="刷新安全设置" :loading="loading" @click="load" /></template>
    </PageHeader>
    <div class="settings-content">
      <p v-if="pageError" class="security-error" role="alert">{{ pageError }}</p>
      <section class="settings-card settings-row">
        <div><h2>登录密码</h2><p>修改后所有设备需重新登录</p></div>
        <CommonButton size="sm" variant="secondary" @click="open('password')">修改</CommonButton>
      </section>
      <section class="settings-card settings-row">
        <div><h2>账号恢复码</h2><p>{{ recovery.configured ? '已设置，可用于找回密码' : '未设置，忘记密码前请先保存恢复码' }}</p></div>
        <CommonButton size="sm" variant="secondary" :disabled="loading || !!pageError" @click="open('recovery')">{{ recovery.configured ? '重新生成' : '生成' }}</CommonButton>
      </section>
      <section class="settings-card">
        <div class="settings-heading">
          <h2>登录设备</h2>
          <CommonButton v-if="otherSessions.length" size="sm" variant="secondary" :disabled="loading" @click="open('revoke')">退出其他设备</CommonButton>
        </div>
        <CommonLoading v-if="loading" text="正在加载登录设备..." />
        <div v-else class="session-list">
          <article v-for="session in sessions" :key="session.id" class="settings-row session-row">
            <div>
              <strong class="session-title">{{ session.deviceName }}<span v-if="session.current" class="current-device">当前设备</span></strong>
              <p>最近活动 {{ date(session.lastSeenAt) }}</p>
              <p>登录于 {{ date(session.createdAt) }} · {{ session.ipAddress }}</p>
            </div>
            <CommonButton size="sm" variant="secondary" @click="open('revoke', session)">退出</CommonButton>
          </article>
          <p v-if="!sessions.length && !pageError" class="settings-muted">暂无有效登录设备</p>
        </div>
      </section>
    </div>

    <CommonModal v-model="modalOpen" :title="modalTitle" size="compact" :close-on-overlay="!busy && !recoveryCode" :show-close="!busy && !recoveryCode">
      <form id="security-form" class="security-modal-form" @submit.prevent="submit">
        <template v-if="modal === 'password'">
          <label class="security-field">当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" maxlength="64" :disabled="busy" /></label>
          <label class="security-field">新密码<input v-model="newPassword" type="password" autocomplete="new-password" maxlength="64" placeholder="6 到 64 位" :disabled="busy" /></label>
          <label class="security-field">确认新密码<input v-model="confirmPassword" type="password" autocomplete="new-password" maxlength="64" :disabled="busy" /></label>
        </template>
        <template v-else-if="modal === 'recovery'">
          <template v-if="recoveryCode">
            <p class="security-description">恢复码仅展示一次，请保存在安全的地方。它可直接重设密码，使用后失效，请勿分享给他人。</p>
            <code class="recovery-code">{{ recoveryCode }}</code>
          </template>
          <template v-else>
            <p class="security-description">{{ recovery.configured ? '重新生成会立即使旧恢复码失效。' : '忘记密码时，可凭账号和恢复码重设密码。' }}请先验证当前密码。</p>
            <label class="security-field">当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" maxlength="64" :disabled="busy" /></label>
          </template>
        </template>
        <p v-else class="security-description">{{ revokeTarget ? `确认退出 ${revokeTarget.deviceName}${revokeTarget.current ? '（当前设备）' : ''}？` : '确认退出除当前设备外的所有登录设备？' }}这些设备将需要重新登录。</p>
        <p v-if="modalError" class="security-error" role="alert">{{ modalError }}</p>
      </form>
      <template #footer>
        <div v-if="recoveryCode" class="security-actions">
          <CommonButton variant="secondary" @click="copyCode">复制恢复码</CommonButton>
          <CommonButton @click="close">已保存</CommonButton>
        </div>
        <div v-else class="security-actions">
          <CommonButton variant="secondary" :disabled="busy" @click="close">取消</CommonButton>
          <CommonButton type="submit" form="security-form" :disabled="busy">{{ busy ? '处理中...' : modal === 'revoke' ? '确认退出' : modal === 'recovery' ? '生成恢复码' : '保存' }}</CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonFeedback v-model="feedback" :message="feedbackMessage" type="success" />
  </section>
</template>

<style scoped lang="scss" src="../account-settings.scss"></style>
