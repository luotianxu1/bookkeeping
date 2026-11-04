import { createApp } from 'vue'
import App from './App.vue'
import './styles/base.css'
import './styles/shared.scss'
import { router } from './router'
import { getStoredCurrentUser } from './utils/current-user'
import { initializeGoldPriceCache } from './utils/gold-price-cache'
import { setupRem } from './utils/rem'
import { initTheme } from './utils/theme'

/**
 * Some browser-injected Web Vitals clients call `reportAllChanges` with an
 * empty layout-shift entry. Their callback then dereferences `startTime` on
 * an undefined entry and surfaces an uncaught error in the host page. Ignore
 * only that known third-party failure; application errors must still bubble.
 */
window.addEventListener('error', (event) => {
  const error = event.error as Error | undefined
  const message = error?.message ?? event.message ?? ''
  const stack = error?.stack ?? ''

  if (
    message.includes("Cannot read properties of undefined (reading 'startTime')")
    && stack.includes('reportAllChanges')
  ) {
    event.preventDefault()
    event.stopImmediatePropagation()
  }
})

initTheme()

const app = createApp(App)
app.use(router)
setupRem()

async function bootstrapApp() {
  await router.isReady()
  if (getStoredCurrentUser()) {
    // 后台预热金价缓存，不阻塞 mount——登录后行情接口慢时页面照常渲染
    void initializeGoldPriceCache()
  }
  app.mount('#app')
}

void bootstrapApp()
