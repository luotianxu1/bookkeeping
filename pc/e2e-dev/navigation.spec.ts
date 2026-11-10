import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect } from '@playwright/test'
import { createServer, type ViteDevServer } from 'vite'
import { modules } from '../src/data/modules'
import { setup } from '../e2e-business/fixtures'

let server: ViteDevServer | undefined
let cacheDir: string
let origin: string

test.beforeAll(async () => {
  const root = fileURLToPath(new URL('../', import.meta.url))
  // A new cache is essential: a warm optimizer hides reloads on first visits.
  cacheDir = await mkdtemp(join(tmpdir(), 'life-hub-pc-navigation-'))
  server = await createServer({
    root,
    configFile: join(root, 'vite.config.ts'),
    cacheDir,
    logLevel: 'warn',
    server: { host: '127.0.0.1', port: 0, strictPort: true, open: false },
  })
  await server.listen()
  const address = server.httpServer!.address()
  if (!address || typeof address === 'string') throw new Error('No Vite port')
  origin = `http://127.0.0.1:${address.port}`
})

test.afterAll(async () => {
  await server?.close()
  if (cacheDir) await rm(cacheDir, { recursive: true, force: true })
})

test('cold development server keeps the document and shell across every sidebar entry', async ({
  page,
}) => {
  await setup(page, true)
  const documents: string[] = []
  const reloads: string[] = []
  page.on('request', (request) => {
    if (request.isNavigationRequest() && request.frame() === page.mainFrame())
      documents.push(request.url())
  })
  page.on('websocket', (socket) =>
    socket.on('framereceived', ({ payload }) => {
      const message = JSON.parse(String(payload))
      if (message.type === 'full-reload') reloads.push(String(payload))
    }),
  )
  await page.goto(`${origin}/overview`)
  await expect(page.getByRole('heading', { name: /功能总览/ })).toBeVisible()
  const timeOrigin = await page.evaluate(() => performance.timeOrigin)
  const shell = await page.locator('.admin-shell').elementHandle()
  const sidebar = await page.locator('.sidebar').elementHandle()
  const topbar = await page.locator('.topbar').elementHandle()

  async function assertPersistentLayout() {
    await server!.waitForRequestsIdle()
    expect(reloads).toEqual([])
    expect(documents).toEqual([`${origin}/overview`])
    expect(await page.evaluate(() => performance.timeOrigin)).toBe(timeOrigin)
    for (const [element, selector] of [
      [shell, '.admin-shell'],
      [sidebar, '.sidebar'],
      [topbar, '.topbar'],
    ] as const) {
      expect(
        await element!.evaluate(
          (node, selector) => node === document.querySelector(selector),
          selector,
        ),
      ).toBe(true)
    }
  }

  for (const module of modules) {
    const title = page
      .locator('.el-sub-menu__title')
      .filter({ hasText: module.title })
    if (
      !(await title.locator('..').getAttribute('class'))?.includes('is-opened')
    )
      await title.click()
    for (const entry of module.entries) {
      await test.step(`${module.title} / ${entry.title}`, async () => {
        await page
          .getByRole('menuitem', { name: entry.title, exact: true })
          .click()
        await expect(page).toHaveURL(`${origin}${entry.path}`)
        await expect(page.locator('.topbar')).toContainText(module.title)
        await assertPersistentLayout()
      })
    }
  }

  // Successful data also loads the chart modules after the page has mounted.
  await setup(page)
  await page
    .locator('.el-sub-menu__title')
    .filter({ hasText: '生活工具' })
    .click()
  await page.getByRole('menuitem', { name: '体重记录', exact: true }).click()
  await expect(page.locator('.record-chart canvas')).toHaveCount(1)
  await assertPersistentLayout()
  await page.goBack()
  await expect(page).toHaveURL(`${origin}/profile/task-management`)
  await assertPersistentLayout()
})
