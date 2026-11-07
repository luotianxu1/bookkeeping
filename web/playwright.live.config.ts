import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e-live',
  timeout: 30_000,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-live-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4176',
    trace: 'retain-on-failure', screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium-live', use: { ...devices['Desktop Chrome'], viewport: { width: 430, height: 932 } } }],
  webServer: [
    {
      command: 'node e2e-live/start-backend.mjs',
      url: 'http://127.0.0.1:18881/actuator/health',
      timeout: 180_000,
      stdout: 'pipe',
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 15_000 },
    },
    {
      command: 'pnpm exec vue-tsc -b && pnpm exec vite build --config vite.live.config.ts && pnpm exec vite preview --config vite.live.config.ts',
      url: 'http://127.0.0.1:4176',
      timeout: 120_000,
      stdout: 'pipe',
      reuseExistingServer: false,
      env: {
        VITE_API_BASE_URL: '/auth-api', VITE_AUTH_API_BASE_URL: '/auth-api',
        VITE_FINANCE_API_BASE_URL: '/finance-api', VITE_TOOL_API_BASE_URL: '/tool-api',
        VITE_FOOD_API_BASE_URL: '/food-api', VITE_MARKET_API_BASE_URL: '/market-api',
      },
    },
  ],
})
