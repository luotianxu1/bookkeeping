import { fileURLToPath, URL } from 'node:url'
import { existsSync, readdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const elementPlusComponents = join(
  dirname(createRequire(import.meta.url).resolve('element-plus/package.json')),
  'es/components',
)
const elementPlusStyles = readdirSync(elementPlusComponents)
  .filter((name) =>
    existsSync(join(elementPlusComponents, name, 'style/css.mjs')),
  )
  .map((name) => `element-plus/es/components/${name}/style/css`)

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = {
    ...Object.fromEntries(
      [
        ['finance', 'APP'],
        ['tool', 'APP'],
        ['food', 'APP'],
        ['market', 'MARKET'],
      ].map(([prefix, service]) => [
        `/${prefix}-api`,
        {
          target:
            env[`${service}_PROXY_TARGET`] ||
            `http://127.0.0.1:${service === 'MARKET' ? '8083' : '8082'}`,
          changeOrigin: true,
          rewrite: (path: string) => path.replace(`/${prefix}-api`, ''),
        },
      ]),
    ),
    '/auth-api': {
      target: env.AUTH_PROXY_TARGET || 'http://127.0.0.1:8081',
      changeOrigin: true,
      rewrite: (path: string) => path.replace(/^\/auth-api/, ''),
    },
  }
  return {
    plugins: [
      vue(),
      Components({ resolvers: [ElementPlusResolver()], dts: false }),
    ],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    optimizeDeps: {
      // The component resolver injects these after Vite's initial scan. Include
      // every style entry now so visiting a lazy route never restarts the bundle.
      include: ['element-plus/es', ...elementPlusStyles],
    },
    server: { port: 5174, strictPort: true, proxy },
    preview: { port: 4174, strictPort: true, proxy },
  }
})
