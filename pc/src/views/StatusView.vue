<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ShieldAlert, CloudOff, FileQuestion } from '@lucide/vue'
import { api } from '@/api/admin'
import { clearSession } from '@/stores/session'
import { safeRedirect } from '@/utils/permissions'
import { showError } from '@/utils/feedback'
const route = useRoute()
const router = useRouter()
const busy = ref(false)
const state = computed(() =>
  route.path === '/forbidden'
    ? {
        icon: ShieldAlert,
        code: '403',
        title: '暂无访问权限',
        text: '当前账号没有访问此页面的权限，请联系管理员分配角色。',
      }
    : route.path === '/unavailable'
      ? {
          icon: CloudOff,
          code: 'OFFLINE',
          title: '暂时无法连接服务',
          text: '请确认认证服务已启动，或稍后重新尝试。你的登录状态会被保留。',
        }
      : {
          icon: FileQuestion,
          code: '404',
          title: '页面不存在',
          text: '这个页面可能已经移动，返回工作台继续使用。',
        },
)
async function switchAccount() {
  busy.value = true
  try {
    await api.logout()
    clearSession()
    await router.replace('/login')
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="status-page">
    <component :is="state.icon" :size="48" />
    <span class="eyebrow">{{ state.code }}</span>
    <h1>{{ state.title }}</h1>
    <p>{{ state.text }}</p>
    <div>
      <el-button
        v-if="route.path === '/unavailable'"
        type="primary"
        @click="router.replace(safeRedirect(route.query.redirect))"
      >
        重新连接
      </el-button>
      <el-button v-else type="primary" @click="router.replace('/overview')">
        返回工作台
      </el-button>
      <el-button :loading="busy" @click="switchAccount">切换账号</el-button>
    </div>
  </div>
</template>
