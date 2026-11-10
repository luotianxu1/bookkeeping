import { test, expect, type Page } from '@playwright/test'

const password = 'pc-test-password'
async function login(page: Page, username = 'pc-admin', secret = password) {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(secret)
  const response = page.waitForResponse((response) =>
    response.url().endsWith('/api/auth/login'),
  )
  await page.getByRole('button', { name: '登录工作台' }).click()
  if ((await (await response).json()).code === 0)
    await expect(page).toHaveURL(/\/(overview|forbidden)$/)
}

test('login uses the existing cookie session and ordinary users cannot access the console', async ({
  page,
  context,
}) => {
  await page.goto('/users')
  await expect(page).toHaveURL(/\/login/)
  await login(page, 'pc-admin', 'incorrect-password')
  await expect(page.getByRole('alert')).toContainText('用户名或密码错误')
  await login(page, 'pc-user')
  await expect(
    page.getByRole('heading', { name: '暂无访问权限' }),
  ).toBeVisible()
  await page.getByRole('button', { name: '切换账号' }).click()
  await login(page)
  await expect(page).toHaveURL(/\/overview$/)
  const cookie = (await context.cookies()).find(
    (cookie) => cookie.name === 'access_token',
  )
  expect(cookie?.httpOnly).toBe(true)
  expect(cookie?.sameSite).toBe('Lax')
  expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([])
  expect(await page.evaluate(() => document.cookie)).not.toContain(
    'access_token',
  )
  await page.getByLabel('搜索项目功能').fill('旅行')
  await expect(page.locator('.feature-card')).toHaveCount(1)
  await expect(page.locator('.feature-card')).toContainText('生活工具')
})

test('create and edit a user, configure a role, enforce grants, revoke access and delete an unused role', async ({
  page,
  browser,
}) => {
  test.setTimeout(90_000)
  const suffix = Date.now().toString()
  const roleName = `用户协管员 ${suffix}`
  const username = `member_${suffix}`
  await login(page)
  await page.getByRole('menuitem', { name: '角色管理', exact: true }).click()
  await page.getByRole('button', { name: '新建角色', exact: true }).click()
  let dialog = page.getByRole('dialog')
  await dialog.getByLabel('角色名称').fill(roleName)
  await dialog.getByLabel('角色编码').fill(`manager_${suffix}`)
  await dialog.getByLabel('描述').fill('负责用户资料维护')
  await dialog.getByRole('button', { name: '创建角色', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  const roleRow = page.getByRole('row').filter({ hasText: roleName })
  await roleRow.getByRole('button', { name: '编辑', exact: true }).click()
  await dialog.getByLabel('描述').fill('维护用户资料及角色授权')
  await dialog.getByRole('button', { name: '保存修改', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(roleRow).toContainText('维护用户资料及角色授权')
  await roleRow.getByRole('button', { name: '配置权限', exact: true }).click()
  await expect(page.locator('.permission-header')).toContainText(roleName)
  await page.getByText('编辑用户', { exact: true }).click()
  await expect(
    page.getByRole('checkbox', { name: '访问管理端', exact: true }),
  ).toBeChecked()
  await expect(
    page.getByRole('checkbox', { name: '查看用户', exact: true }),
  ).toBeChecked()
  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await expect(
    page.getByText('当前权限配置尚未保存，确定放弃修改？'),
  ).toBeVisible()
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(page).toHaveURL(/\/permissions\?role=/)
  await expect(
    page.getByRole('menuitem', { name: '权限配置', exact: true }),
  ).toHaveClass(/is-active/)
  await expect(
    page.getByRole('checkbox', { name: '编辑用户', exact: true }),
  ).toBeChecked()
  await page.getByRole('button', { name: '保存权限', exact: true }).click()
  await expect(page.getByText('当前权限配置已同步')).toBeVisible()
  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await page.getByRole('button', { name: '新建用户', exact: true }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByLabel('用户名', { exact: true }).fill(username)
  await dialog.getByLabel('初始密码').fill(password)
  await dialog.getByLabel('昵称').fill('林小满')
  await dialog.getByLabel('邮箱').fill(`${username}@example.test`)
  await dialog.getByRole('button', { name: '创建用户', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await page.getByLabel('搜索用户', { exact: true }).fill(username)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const userRow = page.getByRole('row').filter({ hasText: username })
  await expect(userRow).toContainText('普通用户')
  await userRow.getByRole('button', { name: '编辑', exact: true }).click()
  await dialog.getByLabel('昵称').fill('林小满 · 用户协管')
  await dialog.getByRole('button', { name: '保存修改', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(userRow).toContainText('林小满 · 用户协管')
  await userRow.getByRole('button', { name: '分配角色', exact: true }).click()
  await dialog.locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: roleName, exact: true }).click()
  await dialog.getByRole('button', { name: '保存角色', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(userRow).toContainText(roleName)

  const memberContext = await browser.newContext({
    baseURL: 'http://127.0.0.1:4177',
  })
  const member = await memberContext.newPage()
  await login(member, username)
  await expect(member).toHaveURL(/\/overview$/)
  await expect(
    member.getByRole('menuitem', { name: '角色管理', exact: true }),
  ).toHaveCount(0)
  await member.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await expect(
    member.getByRole('button', { name: '新建用户', exact: true }),
  ).toHaveCount(0)
  await expect(
    member
      .getByRole('row')
      .filter({ hasText: username })
      .getByRole('button', { name: '编辑', exact: true }),
  ).toBeVisible()
  const forbidden = await memberContext.request.post(
    '/auth-api/api/admin/roles',
    { data: { code: 'forged', name: '越权', description: '' } },
  )
  expect(forbidden.status()).toBe(403)
  await member.goto('/roles')
  await expect(member).toHaveURL(/\/forbidden$/)

  await page.getByRole('menuitem', { name: '权限配置', exact: true }).click()
  await page.locator('.role-choice').filter({ hasText: roleName }).click()
  await page.getByText('编辑用户', { exact: true }).click()
  await page.getByRole('button', { name: '保存权限', exact: true }).click()
  await expect(page.getByText('当前权限配置已同步')).toBeVisible()
  await member.goto('/users')
  await expect(
    member.getByRole('button', { name: '编辑', exact: true }),
  ).toHaveCount(0)

  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await userRow.getByRole('button', { name: '停用', exact: true }).click()
  await page.getByRole('button', { name: '确认停用', exact: true }).click()
  await expect(userRow).toContainText('已停用')
  expect(
    (await memberContext.request.get('/auth-api/api/admin/session')).status(),
  ).toBe(401)
  await member.reload()
  await expect(member).toHaveURL(/\/login/)
  await userRow.getByRole('button', { name: '启用', exact: true }).click()
  await page.getByRole('button', { name: '确认启用', exact: true }).click()
  await expect(userRow).toContainText('正常')
  expect(
    (await memberContext.request.get('/auth-api/api/admin/session')).status(),
  ).toBe(401)
  await memberContext.close()

  await page.getByRole('menuitem', { name: '角色管理', exact: true }).click()
  await expect(
    roleRow.getByRole('button', { name: '删除', exact: true }),
  ).toBeDisabled()
  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await userRow.getByRole('button', { name: '分配角色', exact: true }).click()
  await page.getByRole('dialog').locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '普通用户', exact: true }).click()
  await page
    .getByRole('dialog')
    .getByRole('button', { name: '保存角色', exact: true })
    .click()
  await expect(page.getByRole('dialog')).not.toBeVisible()
  await page.getByRole('menuitem', { name: '角色管理', exact: true }).click()
  await roleRow.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  await expect(roleRow).toHaveCount(0)
})

test('clearing role and status filters restores matching users', async ({ page }) => {
  await login(page)
  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  const adminRow = page.getByRole('row').filter({ hasText: '@pc-admin' })
  const memberRow = page.getByRole('row').filter({ hasText: '@pc-user' })
  await expect(adminRow).toBeVisible()
  await page.getByLabel('搜索用户', { exact: true }).fill('pc-')

  async function search() {
    const pending = page.waitForResponse((response) =>
      new URL(response.url()).pathname.endsWith('/api/admin/users'),
    )
    await page.getByRole('button', { name: '查询', exact: true }).click()
    const response = await pending
    expect(response.ok()).toBe(true)
    expect((await response.json()).code).toBe(0)
    return new URL(response.url()).searchParams
  }

  for (const filter of [
    { label: '筛选角色', option: '普通用户', key: 'roleCode', value: 'user' },
    { label: '用户状态', option: '已锁定', key: 'status', value: 'locked' },
  ]) {
    const input = page.getByRole('combobox', { name: filter.label, exact: true })
    const select = page.locator('.filter-bar .el-select').filter({ has: input })
    await select.locator('.el-select__wrapper').click()
    await page.getByRole('option', { name: filter.option, exact: true }).click()
    expect((await search()).get(filter.key)).toBe(filter.value)
    await expect(adminRow).toHaveCount(0)
    if (filter.key === 'roleCode') await expect(memberRow).toBeVisible()
    else await expect(page.getByText('没有找到符合条件的用户')).toBeVisible()

    await select.hover()
    await select.locator('.el-select__clear').click()
    const params = await search()
    expect(params.has(filter.key)).toBe(false)
    expect(params.get('keyword')).toBe('pc-')
    expect(params.get('page')).toBe('1')
    await expect(adminRow).toBeVisible()
    await expect(memberRow).toBeVisible()
  }
  await page.getByRole('menuitem', { name: '角色管理', exact: true }).click()
  const roleStatus = page.locator('.el-select').filter({
    has: page.getByRole('combobox', { name: '角色状态', exact: true }),
  })
  await roleStatus.locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '已停用', exact: true }).click()
  const superRole = page.getByRole('row').filter({ hasText: 'super_admin' })
  await expect(superRole).toHaveCount(0)
  await roleStatus.hover()
  await roleStatus.locator('.el-select__clear').click()
  await expect(superRole).toBeVisible()
})

test('empty filters, refresh recovery and a service outage keep the browser session intact', async ({
  page,
  context,
}) => {
  await login(page)
  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await page.getByLabel('搜索用户', { exact: true }).fill('no-such-user-xyz')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('没有找到符合条件的用户')).toBeVisible()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(
    page.getByRole('row').filter({ hasText: '@pc-admin' }),
  ).toBeVisible()
  const cookie = (await context.cookies()).find(
    (cookie) => cookie.name === 'access_token',
  )?.value
  await page.route('**/api/admin/session', (route) =>
    route.fulfill({
      status: 503,
      json: { code: 503, message: '测试暂不可用' },
    }),
  )
  await page.reload()
  await expect(
    page.getByRole('heading', { name: '暂时无法连接服务' }),
  ).toBeVisible()
  expect(
    (await context.cookies()).find((cookie) => cookie.name === 'access_token')
      ?.value,
  ).toBe(cookie)
  await page.unroute('**/api/admin/session')
  await page.getByRole('button', { name: '重新连接' }).click()
  await expect(page).toHaveURL(/\/users$/)
  await expect(
    page.getByRole('row').filter({ hasText: '@pc-admin' }),
  ).toBeVisible()
})

test('desktop pages render without console errors or horizontal page overflow', async ({
  page,
}, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await page.screenshot({
    path: testInfo.outputPath('login.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await login(page)
  for (const path of ['overview', 'users', 'roles', 'permissions']) {
    await page.goto(`/${path}`)
    await expect(page.locator('.page-heading h1')).toBeVisible()
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true)
    await page.screenshot({
      path: testInfo.outputPath(`${path}.png`),
      fullPage: true,
      animations: 'disabled',
    })
  }
  await page.setViewportSize({ width: 1024, height: 768 })
  await page.goto('/users')
  await expect(
    page.getByRole('row').filter({ hasText: '@pc-admin' }),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
  await page.screenshot({
    path: testInfo.outputPath('users-1024.png'),
    fullPage: true,
    animations: 'disabled',
  })
  expect(errors).toEqual([])
})

test('a slow earlier search cannot overwrite the latest results', async ({
  page,
}) => {
  await login(page)
  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await expect(
    page.getByRole('row').filter({ hasText: '@pc-admin' }),
  ).toBeVisible()
  let release!: () => void
  let received!: () => void
  const hold = new Promise<void>((resolve) => {
    release = resolve
  })
  const ready = new Promise<void>((resolve) => {
    received = resolve
  })
  await page.route('**/api/admin/users?**', async (route) => {
    if (
      new URL(route.request().url()).searchParams.get('keyword') !== 'pc-admin'
    ) {
      await route.continue()
      return
    }
    const response = await route.fetch()
    received()
    await hold
    await route.fulfill({ response })
  })
  try {
    await page.getByLabel('搜索用户', { exact: true }).fill('pc-admin')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await ready
    await page.getByLabel('搜索用户', { exact: true }).fill('pc-user')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(
      page.getByRole('row').filter({ hasText: '@pc-user' }),
    ).toBeVisible()
    await expect(
      page.getByRole('row').filter({ hasText: '@pc-admin' }),
    ).toHaveCount(0)
    const oldResponse = page.waitForResponse((response) =>
      response.url().includes('keyword=pc-admin'),
    )
    release()
    await oldResponse
    await expect(
      page.getByRole('row').filter({ hasText: '@pc-user' }),
    ).toBeVisible()
    await expect(
      page.getByRole('row').filter({ hasText: '@pc-admin' }),
    ).toHaveCount(0)
  } finally {
    release()
  }
})

test('reactivation bypasses a cached disabled status but never restores old sessions', async ({
  page,
  browser,
}) => {
  await login(page)
  const suffix = Date.now().toString()
  const admin = page.context().request
  const userResponse = await admin.post('/auth-api/api/admin/users', {
    data: {
      username: `reactivate_${suffix}`,
      password,
      displayName: '启停验证用户',
      phone: '',
      email: '',
    },
  })
  expect(userResponse.ok()).toBe(true)
  const user = (await userResponse.json()).data
  const roleResponse = await admin.post('/auth-api/api/admin/roles', {
    data: { code: `access_${suffix}`, name: '工作台访问者', description: '' },
  })
  const role = (await roleResponse.json()).data
  expect(
    (
      await admin.put(`/auth-api/api/admin/roles/${role.id}/permissions`, {
        data: { permissions: ['pc.access'] },
      })
    ).ok(),
  ).toBe(true)
  expect(
    (
      await admin.put(`/auth-api/api/admin/users/${user.id}/role`, {
        data: { roleCode: role.code },
      })
    ).ok(),
  ).toBe(true)
  const member = await browser.newContext({ baseURL: 'http://127.0.0.1:4177' })
  try {
    // Login does not query the protected-status cache. The first protected request below sees a disabled user.
    expect(
      (
        await (
          await member.request.post('/auth-api/api/auth/login', {
            data: { username: user.username, password },
          })
        ).json()
      ).code,
    ).toBe(0)
    expect(
      (
        await admin.put(`/auth-api/api/admin/users/${user.id}/status`, {
          data: { status: 'disabled' },
        })
      ).ok(),
    ).toBe(true)
    expect(
      (await member.request.get('/auth-api/api/admin/session')).status(),
    ).toBe(401)
    expect(
      (
        await admin.put(`/auth-api/api/admin/users/${user.id}/status`, {
          data: { status: 'active' },
        })
      ).ok(),
    ).toBe(true)
    expect(
      (await member.request.get('/auth-api/api/admin/session')).status(),
    ).toBe(401)
    expect(
      (
        await (
          await member.request.post('/auth-api/api/auth/login', {
            data: { username: user.username, password },
          })
        ).json()
      ).code,
    ).toBe(0)
    expect(
      (await member.request.get('/auth-api/api/admin/session')).status(),
    ).toBe(200)
  } finally {
    await member.close()
  }
})

test('menu and browser history only replace the content and preserve the shell', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1024, height: 600 })
  await login(page)
  const shell = await page.locator('.admin-shell').elementHandle()
  const sidebar = await page.locator('.sidebar').elementHandle()
  const header = await page.locator('.topbar').elementHandle()
  const content = await page
    .getByRole('main', { name: '功能区' })
    .elementHandle()
  const timeOrigin = await page.evaluate(() => performance.timeOrigin)
  const documentRequests: string[] = []
  page.on('request', (request) => {
    if (request.isNavigationRequest() && request.frame() === page.mainFrame())
      documentRequests.push(request.url())
  })
  await page.getByRole('button', { name: '收起侧栏', exact: true }).click()
  for (const [title, path] of [
    ['用户管理', 'users'],
    ['角色管理', 'roles'],
    ['权限配置', 'permissions'],
  ]) {
    await page
      .locator('.el-sub-menu__title')
      .filter({ hasText: '权限管理' })
      .hover()
    await page.getByRole('menuitem', { name: title, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/${path}$`))
    await expect(page.locator('.page-heading h1')).toContainText(title!)
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    expect(
      await shell!.evaluate(
        (node) => node === document.querySelector('.admin-shell'),
      ),
    ).toBe(true)
    expect(
      await sidebar!.evaluate(
        (node) => node === document.querySelector('.sidebar'),
      ),
    ).toBe(true)
    expect(
      await header!.evaluate(
        (node) => node === document.querySelector('.topbar'),
      ),
    ).toBe(true)
    expect(
      await content!.evaluate(
        (node) => node === document.querySelector('.page-container'),
      ),
    ).toBe(true)
    await expect(page.locator('.admin-shell')).toHaveClass(/is-collapsed/)
  }
  // The permission list scrolls within the main area; both navigation regions stay stationary.
  await page.getByRole('button', { name: '展开侧栏', exact: true }).click()
  await page
    .locator('.page-container')
    .evaluate((node) => node.scrollTo({ top: node.scrollHeight }))
  expect(
    await page.locator('.page-container').evaluate((node) => node.scrollTop),
  ).toBeGreaterThan(0)
  expect((await page.locator('.topbar').boundingBox())?.y).toBe(0)
  expect((await page.locator('.sidebar').boundingBox())?.y).toBe(0)
  expect(await page.evaluate(() => window.scrollY)).toBe(0)
  await page.goBack()
  await expect(page).toHaveURL(/\/roles$/)
  await expect
    .poll(() =>
      page.locator('.page-container').evaluate((node) => node.scrollTop),
    )
    .toBe(0)
  await page.goForward()
  await expect(page).toHaveURL(/\/permissions$/)
  expect(
    await shell!.evaluate(
      (node) => node === document.querySelector('.admin-shell'),
    ),
  ).toBe(true)
  expect(await page.evaluate(() => performance.timeOrigin)).toBe(timeOrigin)
  expect(documentRequests).toEqual([])
})

test('user deletion checks permissions, supports cancellation and revokes active sessions', async ({
  page,
  browser,
}) => {
  await login(page)
  const suffix = Date.now().toString()
  const admin = page.context().request
  const role = (
    await (
      await admin.post('/auth-api/api/admin/roles', {
        data: {
          code: `delete_${suffix}`,
          name: `删除验证 ${suffix}`,
          description: '',
        },
      })
    ).json()
  ).data
  expect(
    (
      await admin.put(`/auth-api/api/admin/roles/${role.id}/permissions`, {
        data: { permissions: ['pc.access', 'user.read'] },
      })
    ).ok(),
  ).toBe(true)
  const user = (
    await (
      await admin.post('/auth-api/api/admin/users', {
        data: {
          username: `delete_${suffix}`,
          password,
          displayName: '删除验证用户',
          phone: '',
          email: '',
        },
      })
    ).json()
  ).data
  expect(
    (
      await admin.put(`/auth-api/api/admin/users/${user.id}/role`, {
        data: { roleCode: role.code },
      })
    ).ok(),
  ).toBe(true)
  const memberContext = await browser.newContext({
    baseURL: 'http://127.0.0.1:4177',
  })
  try {
    const member = await memberContext.newPage()
    await login(member, user.username)
    const family = await (
      await memberContext.request.post('/auth-api/api/auth/family/create')
    ).json()
    expect(family.code).toBe(0)
    expect(
      (await (await admin.post('/auth-api/api/auth/family/join', {
        data: { inviteCode: family.data.inviteCode },
      })).json()).code,
    ).toBe(0)
    await member.goto('/users')
    await expect(
      member.getByRole('button', { name: '删除', exact: true }),
    ).toHaveCount(0)
    expect(
      (
        await memberContext.request.delete(
          `/auth-api/api/admin/users/${user.id}`,
        )
      ).status(),
    ).toBe(403)

    await page.goto('/users')
    await expect(
      page
        .getByRole('row')
        .filter({ hasText: '@pc-admin' })
        .getByRole('button', { name: '删除', exact: true }),
    ).toHaveCount(0)
    await page.getByLabel('搜索用户', { exact: true }).fill(user.username)
    await page.getByRole('button', { name: '查询', exact: true }).click()
    const row = page.getByRole('row').filter({ hasText: user.username })
    await row.getByRole('button', { name: '删除', exact: true }).click()
    await expect(page.getByText('个人数据及登录会话将一并永久删除', { exact: false })).toBeVisible()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await expect(row).toBeVisible()
    expect(
      (await memberContext.request.get('/auth-api/api/admin/session')).status(),
    ).toBe(200)

    await row.getByRole('button', { name: '删除', exact: true }).click()
    await page.getByRole('button', { name: '确认删除', exact: true }).click()
    await expect(row).toHaveCount(0)
    expect(
      (await memberContext.request.get('/auth-api/api/admin/session')).status(),
    ).toBe(401)
    const remainingFamily = await (await admin.get('/auth-api/api/auth/family')).json()
    expect(remainingFamily.code).toBe(0)
    expect(remainingFamily.data.inviteCode).toBe(family.data.inviteCode)
    expect(remainingFamily.data.members).toHaveLength(1)
    expect(remainingFamily.data.members[0].role).toBe('管理员')
    const loginResult = await memberContext.request.post(
      '/auth-api/api/auth/login',
      {
        data: { username: user.username, password },
      },
    )
    expect((await loginResult.json()).code).not.toBe(0)
    await page.getByRole('menuitem', { name: '角色管理', exact: true }).click()
    const roleRow = page.getByRole('row').filter({ hasText: role.name })
    await expect(
      roleRow.getByRole('button', { name: '删除', exact: true }),
    ).toBeEnabled()
    await roleRow.getByRole('button', { name: '删除', exact: true }).click()
    await page.getByRole('button', { name: '确认删除', exact: true }).click()
    await expect(roleRow).toHaveCount(0)
  } finally {
    await memberContext.close()
  }
})
