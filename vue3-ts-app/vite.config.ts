import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

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
        allowedHosts: ['dvtr44606945.vicp.fun', '.trycloudflare.com'],
        proxy: {
            '/auth-api': {
                target: 'http://localhost:8081',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/auth-api/, ''),
            },
            '/finance-api': {
                target: 'http://localhost:8082',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/finance-api/, ''),
            },
            '/tool-api': {
                target: 'http://localhost:8083',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/tool-api/, ''),
            },
            '/food-api': {
                target: 'http://localhost:8084',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/food-api/, ''),
            },
            '/api-proxy': {
                target: 'http://localhost:8081',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api-proxy/, ''),
            },
        },
    },
    preview: {
        host: '0.0.0.0',
        port: 4173,
        strictPort: true,
    },
    plugins: [vue()],
})
