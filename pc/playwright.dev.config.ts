import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e-dev',
  timeout: 180_000,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    viewport: { width: 1440, height: 1000 },
    actionTimeout: 10_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
