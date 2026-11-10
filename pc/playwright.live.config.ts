import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './e2e-live',
  timeout: 60_000,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-live-report', open: 'never' }],
  ],
  use: {
    baseURL: 'http://127.0.0.1:4179',
    actionTimeout: 10_000,
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
      command: 'node e2e-live/start-backend.mjs',
      url: 'http://127.0.0.1:18881/actuator/health',
      timeout: 180_000,
      stdout: 'pipe',
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 15_000 },
    },
    {
      command:
        'pnpm build && pnpm exec vite preview --host 127.0.0.1 --port 4179',
      url: 'http://127.0.0.1:4179',
      timeout: 180_000,
      reuseExistingServer: false,
      env: {
        AUTH_PROXY_TARGET: 'http://127.0.0.1:18881',
        APP_PROXY_TARGET: 'http://127.0.0.1:18882',
        MARKET_PROXY_TARGET: 'http://127.0.0.1:18882',
      },
    },
  ],
})
