import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

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
                // vendor 依赖与业务代码分离；echarts 体积大单独拆，便于浏览器缓存
                manualChunks(id: string) {
                    if (id.includes('node_modules')) {
                        if (id.includes('echarts') || id.includes('zrender')) {
                            return 'echarts'
                        }
                        return 'vendor'
                    }
                },
            },
        },
    },
    plugins: [vue()],
})
