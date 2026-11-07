import { defineConfig, mergeConfig } from 'vite'
import base from './vite.config'

// 联通测试使用独立端口，所有代理显式指向临时服务，不能落回本机开发后端。
export default mergeConfig(base, defineConfig({
  build: { outDir: 'dist-live' },
  preview: {
    host: '127.0.0.1', port: 4176,
    proxy: {
      '/auth-api': { target: 'http://127.0.0.1:18881' },
      '/api-proxy': { target: 'http://127.0.0.1:18881' },
      '/finance-api': { target: 'http://127.0.0.1:18882' },
      '/tool-api': { target: 'http://127.0.0.1:18882' },
      '/food-api': { target: 'http://127.0.0.1:18882' },
      // 本套件不启动行情抓取；金价预热收到本地 404，登录按现有逻辑继续。
      '/market-api': { target: 'http://127.0.0.1:18882' },
    },
  },
}))
