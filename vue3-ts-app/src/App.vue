<script setup lang="ts">
// 应用装配入口：负责把当前页面放入统一的应用布局中。
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppLayout from '@/components/app-shell/AppLayout/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import { mainNavItems } from '@/data/navigation'
import type { AppSection } from '@/types/navigation'
import { authPromptRedirect, authPromptVisible, hideAuthPrompt } from '@/utils/auth-prompt'

const route = useRoute()
const router = useRouter()
const activeSection = computed<AppSection>(() => route.meta.section ?? 'finance')
const screenLabel = computed<string>(() => route.meta.title ?? '财务首页')
const bottomNavVisiblePaths = new Set(['/finance', '/food', '/tools', '/profile'])
const showBottomNav = computed<boolean>(() => bottomNavVisiblePaths.has(route.path))

async function goLogin() {
  const redirect = authPromptRedirect.value
  hideAuthPrompt()
  if (route.name === 'login') {
    return
  }

  await router.push({
    path: '/login',
    query: redirect && redirect !== '/login' ? { redirect } : undefined,
  })
}
</script>

<template>
  <main class="stage">
    <AppLayout
      :active-section="activeSection"
      :nav-items="mainNavItems"
      :screen-label="screenLabel"
      :show-bottom-nav="showBottomNav"
    >
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade">
          <component :is="Component" v-if="Component" />
        </Transition>
      </RouterView>
    </AppLayout>

    <CommonModal
      v-model="authPromptVisible"
      title="请先登录"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="auth-prompt-message">登录后可以继续使用当前内容。</p>

      <template #footer>
        <CommonButton class="auth-prompt-button" @click="goLogin">确认</CommonButton>
      </template>
    </CommonModal>
  </main>
</template>

<style scoped lang="scss" src="./style.scss"></style>
