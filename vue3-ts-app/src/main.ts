import { createApp } from 'vue'
import App from './App.vue'
import './styles/base.css'
import { router } from './router'
import { getStoredToken } from './utils/auth-token'
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
  if (getStoredToken()) {
    await initializeGoldPriceCache()
  }
  app.mount('#app')
}

void bootstrapApp()
