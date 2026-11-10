import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './e2e',
  timeout: 40_000,
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    actionTimeout: 10_000,
    baseURL: 'http://127.0.0.1:4177',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 1000 },
      },
    },
  ],
  webServer: [
    {
      command: 'node e2e/start-backend.mjs',
      url: 'http://127.0.0.1:18883/actuator/health',
      timeout: 180_000,
      stdout: 'pipe',
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 15_000 },
    },
    {
      command:
        'pnpm build && pnpm exec vite preview --host 127.0.0.1 --port 4177',
      url: 'http://127.0.0.1:4177',
      timeout: 120_000,
      reuseExistingServer: false,
      env: {
        AUTH_PROXY_TARGET: 'http://127.0.0.1:18883',
        VITE_AUTH_API_BASE_URL: '/auth-api',
      },
    },
  ],
})
