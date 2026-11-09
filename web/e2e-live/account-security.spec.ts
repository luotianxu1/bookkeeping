import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'

const auth = '/auth-api/api/auth'
const finance = '/finance-api/api/finance'
async function login(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录并继续' }).click()
  await expect(page).toHaveURL(/\/finance$/)
}

test('昵称、登录设备撤销、改密和一次性恢复码通过真实服务完整运行', async ({ page, playwright }, testInfo) => {
  const username = `security-${randomUUID().slice(0, 8)}`
  const oldPassword = 'original password '
  expect((await page.request.post(`${auth}/register`, { data: { username, password: oldPassword } })).status()).toBe(200)
  await login(page, username, oldPassword)
  await page.goto('/profile')
  await page.getByRole('button', { name: '编辑个人资料' }).click()
  await page.getByLabel('昵称', { exact: true }).fill('我的新昵称')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('个人资料已保存')).toBeVisible()
  expect((await (await page.request.get(`${auth}/me`)).json()).data.displayName).toBe('我的新昵称')
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: '我的新昵称' })).toBeVisible()
  await page.getByRole('button', { name: /隐私与安全/ }).click()

  const other = await playwright.request.newContext({ baseURL: 'http://127.0.0.1:4176' })
  try {
    expect((await other.post(`${auth}/login`, { data: { username, password: oldPassword } })).status()).toBe(200)
    await page.getByRole('button', { name: '刷新安全设置' }).click()
    await expect(page.locator('.session-row')).toHaveCount(2)
    await page.screenshot({ path: testInfo.outputPath('account-security-mobile.png'), animations: 'disabled' })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: '生成', exact: true }).click()
    await page.getByLabel('当前密码', { exact: true }).fill(oldPassword)
    await page.getByRole('button', { name: '生成恢复码' }).click()
    await expect(page.locator('.recovery-code')).toBeVisible()
    const code = (await page.locator('.recovery-code').textContent())!.trim()
    expect(code).toMatch(/^[A-F0-9]{8}(?:-[A-F0-9]{8}){4}$/)
    await page.getByRole('button', { name: '已保存' }).click()

    await page.getByRole('button', { name: '退出其他设备' }).click()
    await page.getByRole('button', { name: '确认退出' }).click()
    await expect(page.locator('.session-row')).toHaveCount(1)
    expect((await other.get(`${finance}/accounts`)).status()).toBe(401)
    expect((await other.get(`${auth}/me`)).status()).toBe(401)
    expect((await page.request.get(`${finance}/accounts`)).status()).toBe(200)
    const oldCookie = (await page.context().cookies()).find(cookie => cookie.name === 'access_token')!.value
    await page.getByRole('button', { name: '修改', exact: true }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByLabel('当前密码', { exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('password-modal-mobile.png'), animations: 'disabled' })
    await page.getByLabel('当前密码', { exact: true }).fill(oldPassword)
    await page.getByLabel('新密码', { exact: true }).fill('changed password')
    await page.getByLabel('确认新密码', { exact: true }).fill('changed password')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page).toHaveURL(/passwordChanged=1/)
    expect((await other.get(`${finance}/accounts`, { headers: { Authorization: `Bearer ${oldCookie}` } })).status()).toBe(401)
    expect((await other.get(`${auth}/me`, { headers: { Authorization: `Bearer ${oldCookie}` } })).status()).toBe(401)
    await login(page, username, 'changed password')
    const beforeReset = (await page.context().cookies()).find(cookie => cookie.name === 'access_token')!.value
    await page.goto(`/forgot-password?account=${username}`)
    await page.getByLabel('恢复码', { exact: true }).fill(code)
    await page.getByLabel('新密码', { exact: true }).fill('recovered password')
    await page.getByLabel('确认新密码', { exact: true }).fill('recovered password')
    await page.getByRole('button', { name: '重设密码' }).click()
    await expect(page).toHaveURL(/passwordReset=1/)
    expect((await other.get(`${finance}/accounts`, { headers: { Authorization: `Bearer ${beforeReset}` } })).status()).toBe(401)
    expect((await other.post(`${auth}/password/reset`, { data: { username, recoveryCode: code, newPassword: 'bad replay password' } })).status()).toBe(400)
    await login(page, username, 'recovered password')
    expect((await (await page.request.get(`${auth}/recovery-code`)).json()).data.configured).toBe(false)
    const logoutToken = (await page.context().cookies()).find(cookie => cookie.name === 'access_token')!.value
    expect((await page.request.post(`${auth}/logout`)).status()).toBe(200)
    expect((await other.get(`${finance}/accounts`, { headers: { Authorization: `Bearer ${logoutToken}` } })).status()).toBe(401)
  } finally { await other.dispose() }
})
