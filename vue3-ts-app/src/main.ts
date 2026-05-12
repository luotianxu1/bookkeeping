import { createApp } from 'vue'
import './styles/base.css'
import App from './App/index.vue'
import { router } from './router'

const app = createApp(App)
app.use(router)

router.isReady().then(() => {
  app.mount('#app')
})
