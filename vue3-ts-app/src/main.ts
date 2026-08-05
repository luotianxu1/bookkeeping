import { createApp } from 'vue'
import App from './App.vue'
import './styles/base.css'
import { router } from './router'
import { getStoredToken } from './utils/auth-token'
import { initializeGoldPriceCache } from './utils/gold-price-cache'
import { setupRem } from './utils/rem'
import { initTheme } from './utils/theme'

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
