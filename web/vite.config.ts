import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'

// preview 静态资源缓存头：带 hash 的 assets 长缓存（Cloudflare 边缘 + 浏览器），
// HTML 不缓存保证发版即时生效。文件名变化后旧缓存自然作废，无需清理。
const previewCacheHeaders = (): Plugin => ({
    name: 'preview-cache-headers',
    configurePreviewServer(server) {
        server.middlewares.use((req, res, next) => {
            // 项目未装 @types/node，req 被解析成不含 url 的 IncomingMessage 空壳，这里自行收窄
            const path = (req as { url?: string }).url ?? ''
            if (path.startsWith('/assets/')) {
                res.setHeader('Cache-Control', 'public, max-age=31536000, immutable')
            } else if (path === '/' || path.endsWith('.html')) {
                res.setHeader('Cache-Control', 'no-cache')
            }
            next()
        })
    },
})

const apiProxy = {
    '/auth-api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/auth-api/, ''),
    },
    '/finance-api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/finance-api/, ''),
    },
    '/tool-api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/tool-api/, ''),
    },
    '/food-api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/food-api/, ''),
    },
    '/market-api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/market-api/, ''),
    },
    '/api-proxy': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/api-proxy/, ''),
    },
}

export default defineConfig({
    resolve: {
        alias: {
            '@': '/src',
        },
    },
    server: {
        host: '0.0.0.0',
        port: 5173,
        strictPort: true,
        allowedHosts: ['luotianxu.dpdns.org'],
        proxy: apiProxy,
    },
    preview: {
        host: '0.0.0.0',
        port: 4173,
        strictPort: true,
        allowedHosts: ['luotianxu.dpdns.org'],
        proxy: apiProxy,
    },
    build: {
        rollupOptions: {
            output: {
                // ECharts 由动态入口自动拆分；不要把 K 线重新合并进公共包。
                manualChunks(id: string) {
                    if (id.includes('node_modules')) {
                        if (id.includes('echarts') || id.includes('zrender')) {
                            return undefined
                        }
                        return 'vendor'
                    }
                },
            },
        },
    },
    plugins: [vue(), previewCacheHeaders()],
})
