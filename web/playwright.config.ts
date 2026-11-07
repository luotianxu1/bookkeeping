import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4175',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 430, height: 932 } } }],
  webServer: {
    command: 'pnpm build && pnpm exec vite preview --host 127.0.0.1 --port 4175',
    timeout: 120_000,
    env: { VITE_AUTH_API_BASE_URL: '/auth-api', VITE_FINANCE_API_BASE_URL: '/finance-api' },
    url: 'http://127.0.0.1:4175',
    reuseExistingServer: false,
  },
})
