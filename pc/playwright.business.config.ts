import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './e2e-business',
  timeout: 45_000,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-business-report', open: 'never' }],
  ],
  use: {
    baseURL: 'http://127.0.0.1:4178',
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
  webServer: {
    command:
      'pnpm build && pnpm exec vite preview --host 127.0.0.1 --port 4178',
    url: 'http://127.0.0.1:4178',
    timeout: 180_000,
    reuseExistingServer: false,
  },
})
