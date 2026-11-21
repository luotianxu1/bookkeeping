import { expect, test, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { setup } from './fixtures'

const colors = [
  '#ef4444',
  '#22c55e',
  '#3b82f6',
  '#f59e0b',
  '#8b5cf6',
  '#06b6d4',
  '#ec4899',
  '#64748b',
  '#14b8a6',
]

async function photos(page: Page) {
  const data = await page.evaluate(
    (palette) =>
      palette.map((color) => {
        const canvas = document.createElement('canvas')
        canvas.width = 120
        canvas.height = 80
        const context = canvas.getContext('2d')!
        context.fillStyle = color
        context.fillRect(0, 0, 120, 80)
        return canvas.toDataURL('image/png').split(',')[1]
      }),
    colors,
  )
  return data.map((base64, index) => ({
    name: `photo-${index + 1}.png`,
    mimeType: 'image/png',
    buffer: Buffer.from(base64, 'base64'),
  }))
}

async function downloadPixels(page: Page, points: number[][]) {
  const pending = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载九宫格拼图' }).click()
  const download = await pending
  const bytes = await readFile((await download.path())!)
  const result = await page.evaluate(
    async ({ base64, points }) => {
      const image = new Image()
      image.src = `data:image/${base64.startsWith('iVBOR') ? 'png' : 'jpeg'};base64,${base64}`
      await image.decode()
      const canvas = document.createElement('canvas')
      canvas.width = image.naturalWidth
      canvas.height = image.naturalHeight
      const context = canvas.getContext('2d')!
      context.drawImage(image, 0, 0)
      return {
        width: canvas.width,
        height: canvas.height,
        pixels: points.map(([x, y]) =>
          Array.from(context.getImageData(x, y, 1, 1).data),
        ),
      }
    },
    { base64: bytes.toString('base64'), points },
  )
  return { ...result, filename: download.suggestedFilename(), bytes }
}

function rgba(hex: string) {
  return [1, 3, 5]
    .map((offset) => parseInt(hex.slice(offset, offset + 2), 16))
    .concat(255)
}

test('nine photos can be added, reordered, replaced and downloaded as one correctly arranged PNG', async ({
  page,
}, info) => {
  const { calls } = await setup(page)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/tools')
  await page.getByRole('link', { name: /九宫格拼图/ }).click()
  await expect(page).toHaveURL(/\/tools\/image-grid$/)
  await expect(
    page.getByRole('menuitem', { name: '九宫格拼图', exact: true }),
  ).toBeVisible()
  const input = page.getByLabel('选择拼图图片')
  const button = page.getByRole('button', { name: '下载九宫格拼图' })
  await expect(button).toBeDisabled()
  await page.screenshot({
    path: info.outputPath('image-grid-empty.png'),
    fullPage: true,
  })
  const files = await photos(page)
  await input.setInputFiles(files.slice(0, 8))
  await expect(page.getByText('已添加 8 / 9 张')).toBeVisible()
  await expect(button).toBeDisabled()
  await input.setInputFiles(files.slice(8))
  await expect(button).toBeEnabled()
  await page
    .getByRole('button', { name: '后移第 1 张图片', exact: true })
    .click()
  const picker = page.waitForEvent('filechooser')
  await page
    .getByRole('button', { name: '替换第 3 张图片', exact: true })
    .click()
  await (await picker).setFiles(files[8])
  await expect(
    page
      .getByRole('button', { name: '替换第 3 张图片', exact: true })
      .getByRole('img'),
  ).toHaveAttribute('alt', 'photo-9.png')
  await page
    .getByRole('button', { name: '移除第 9 张图片', exact: true })
    .click()
  await expect(button).toBeDisabled()
  const appendPicker = page.waitForEvent('filechooser')
  await page.getByRole('button', { name: '选择图片', exact: true }).click()
  await (await appendPicker).setFiles(files[8])
  await expect(button).toBeEnabled()

  for (const [width, height] of [
    [1920, 1080],
    [1440, 900],
    [1366, 768],
    [1280, 720],
    [1024, 768],
    [1024, 600],
    [760, 600],
  ]) {
    await page.setViewportSize({ width, height })
    const uploadButton = page.getByRole('button', {
      name: '选择图片',
      exact: true,
    })
    await expect(uploadButton).toBeInViewport({ ratio: 1 })
    await uploadButton.click({ trial: true })
    expect(
      await uploadButton.evaluate((button) => {
        const rect = button.getBoundingClientRect()
        const childrenFit = Array.from(button.children).every((child) => {
          const box = child.getBoundingClientRect()
          return (
            box.left >= rect.left &&
            box.right <= rect.right &&
            box.top >= rect.top &&
            box.bottom <= rect.bottom
          )
        })
        const points = [
          [rect.left + 4, rect.top + 4],
          [rect.right - 4, rect.bottom - 4],
          [rect.left + rect.width / 2, rect.top + rect.height / 2],
        ]
        return (
          childrenFit &&
          points.every(([x, y]) =>
            button.contains(document.elementFromPoint(x, y)),
          )
        )
      }),
      `upload button at ${width}×${height}`,
    ).toBe(true)
    for (const ratio of ['1:1', '9:16', '16:9']) {
      await page.getByLabel('导出比例').selectOption(ratio)
      await expect(button).toBeInViewport({ ratio: 1 })
      await expect(page.getByLabel('九宫格预览')).toBeInViewport({ ratio: 1 })
      await expect(page.locator('.session-note')).toBeInViewport({ ratio: 1 })
      expect(
        await page.getByRole('main', { name: '功能区' }).evaluate((main) => ({
          horizontal: main.scrollWidth > main.clientWidth,
          vertical: main.scrollHeight > main.clientHeight,
          documentOverflow: document.documentElement.scrollWidth > innerWidth,
        })),
        `${width}×${height}, ${ratio}`,
      ).toEqual({ horizontal: false, vertical: false, documentOverflow: false })
    }
    await page.screenshot({
      path: info.outputPath(`image-grid-${width}-${height}.png`),
      fullPage: true,
    })
  }
  await page.getByLabel('导出比例').selectOption('1:1')
  const output = await downloadPixels(
    page,
    colors.map((_, index) => [
      (index % 3) * 1000 + 500,
      Math.floor(index / 3) * 1000 + 500,
    ]),
  )
  expect(output.filename).toBe('九宫格拼图-3000x3000.png')
  expect([output.width, output.height]).toEqual([3000, 3000])
  expect(output.pixels).toEqual(
    [colors[1], colors[0], colors[8], ...colors.slice(3)].map(rgba),
  )
  expect(calls.filter((call) => call.method === 'POST')).toEqual([])
  await expect(page.getByRole('status')).toBeInViewport({ ratio: 1 })
  expect(
    await page
      .getByRole('main', { name: '功能区' })
      .evaluate((main) => main.scrollHeight <= main.clientHeight),
  ).toBe(true)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: '切换到深色模式' }).click()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await page
    .getByRole('heading', { name: '拼图预览', exact: true })
    .scrollIntoViewIfNeeded()
  await page.screenshot({
    path: info.outputPath('image-grid-dark.png'),
    fullPage: true,
  })
  expect(errors).toEqual([])
})

test('export respects full-image fit, white gutters, output size and JPEG format', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/tools/image-grid')
  await page.getByLabel('选择拼图图片').setInputFiles(await photos(page))
  await expect(
    page.getByRole('button', { name: '下载九宫格拼图' }),
  ).toBeEnabled()
  await page.getByLabel('图片适配', { exact: true }).selectOption('contain')
  await page.getByLabel('白色留边与间距').selectOption('30')
  await page.getByLabel('导出尺寸').selectOption('1500')
  await page.getByLabel('文件格式').selectOption('jpeg')
  const output = await downloadPixels(page, [
    [0, 0],
    [260, 40],
    [500, 260],
    [260, 260],
  ])
  expect(output.filename).toBe('九宫格拼图-1500x1500.jpg')
  expect(output.bytes.subarray(0, 2)).toEqual(Buffer.from([0xff, 0xd8]))
  expect([output.width, output.height]).toEqual([1500, 1500])
  for (const pixel of output.pixels.slice(0, 3))
    expect(pixel).toEqual([255, 255, 255, 255])
  rgba(colors[0]).forEach((channel, index) =>
    expect(Math.abs(output.pixels[3][index] - channel)).toBeLessThanOrEqual(3),
  )
  await page.getByLabel('图片适配', { exact: true }).selectOption('cover')
  await page.getByLabel('文件格式').selectOption('png')
  const cropped = await downloadPixels(page, [[260, 40]])
  expect(cropped.pixels).toEqual([rgba(colors[0])])
})

for (const { ratio, width, height } of [
  { ratio: '4:3', width: 1500, height: 1125 },
  { ratio: '9:16', width: 844, height: 1500 },
]) {
  test(`export and preview preserve the ${ratio} ratio, white gutters and all nine photos`, async ({
    page,
  }, info) => {
    await setup(page)
    await page.goto('/tools/image-grid')
    await page.getByLabel('选择拼图图片').setInputFiles(await photos(page))
    await expect(
      page.getByRole('button', { name: '下载九宫格拼图' }),
    ).toBeEnabled()
    await page.getByLabel('导出比例').selectOption(ratio)
    await page.getByLabel('导出尺寸').selectOption('1500')
    await page.getByLabel('白色留边与间距').selectOption('30')
    await expect(page.locator('.preview-footer')).toContainText(
      `${width} × ${height} px`,
    )
    // Read all rectangles in one frame so page entrance animations cannot skew distances.
    const { grid, first, nextColumn, nextRow } = await page
      .getByLabel('九宫格预览')
      .evaluate((element) => {
        const cells = element.querySelectorAll('.collage-cell')
        return {
          grid: element.getBoundingClientRect().toJSON(),
          first: cells[0].getBoundingClientRect().toJSON(),
          nextColumn: cells[1].getBoundingClientRect().toJSON(),
          nextRow: cells[3].getBoundingClientRect().toJSON(),
        }
      })
    const scale = grid.width / width
    expect(Math.abs(grid.width / grid.height - width / height)).toBeLessThan(
      0.002,
    )
    for (const distance of [
      first.x - grid.x,
      first.y - grid.y,
      nextColumn.x - first.x - first.width,
      nextRow.y - first.y - first.height,
    ]) {
      expect(Math.abs(distance - 30 * scale)).toBeLessThan(0.2)
    }
    const centers = colors.map((_, index) => [
      Math.round(
        30 + (index % 3) * ((width - 120) / 3 + 30) + (width - 120) / 6,
      ),
      Math.round(
        30 +
          Math.floor(index / 3) * ((height - 120) / 3 + 30) +
          (height - 120) / 6,
      ),
    ])
    const output = await downloadPixels(page, [
      ...centers,
      [0, 0],
      [width - 1, height - 1],
      [Math.round((width - 120) / 3 + 45), 100],
      [100, Math.round((height - 120) / 3 + 45)],
    ])
    expect(output.filename).toBe(`九宫格拼图-${width}x${height}.png`)
    expect([output.width, output.height]).toEqual([width, height])
    expect(output.pixels.slice(0, 9)).toEqual(colors.map(rgba))
    expect(output.pixels.slice(9)).toEqual(
      Array.from({ length: 4 }, () => [255, 255, 255, 255]),
    )
    await page.getByLabel('图片适配', { exact: true }).selectOption('contain')
    const contained = await downloadPixels(page, [
      [Math.round(centers[0][0]), 40],
      ...centers,
    ])
    expect(contained.pixels[0]).toEqual([255, 255, 255, 255])
    expect(contained.pixels.slice(1)).toEqual(colors.map(rgba))
    await page
      .getByRole('heading', { name: '拼图预览', exact: true })
      .scrollIntoViewIfNeeded()
    await page.screenshot({
      path: info.outputPath(`image-grid-${ratio.replace(':', '-')}.png`),
      fullPage: true,
    })
  })
}

test('file drops, limits and unreadable files recover without losing selected photos', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 760, height: 600 })
  await page.goto('/tools/image-grid')
  const input = page.getByLabel('选择拼图图片')
  const files = await photos(page)
  await input.setInputFiles([...files, files[0]])
  await expect(page.getByRole('alert')).toContainText('本次图片未添加')
  await expect(page.getByText('已添加 0 / 9 张')).toBeVisible()
  const transfer = await page.evaluateHandle((base64) => {
    const data = new DataTransfer()
    data.items.add(
      new File(
        [Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))],
        'dropped.png',
        { type: 'image/png' },
      ),
    )
    return data
  }, files[0].buffer.toString('base64'))
  await page
    .getByRole('group', { name: '图片拖放区' })
    .dispatchEvent('drop', { dataTransfer: transfer })
  await transfer.dispose()
  await expect(page.getByText('已添加 1 / 9 张')).toBeVisible()
  await input.setInputFiles([
    {
      name: 'notes.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('not an image'),
    },
    {
      name: 'broken.png',
      mimeType: 'image/png',
      buffer: Buffer.from('not a PNG'),
    },
    {
      name: 'large.png',
      mimeType: 'image/png',
      buffer: Buffer.alloc(20 * 1024 * 1024 + 1),
    },
    files[1],
  ])
  await expect(page.getByRole('alert')).toContainText('请使用 JPG、PNG 或 WebP')
  await expect(page.getByRole('alert')).toContainText('图片无法读取')
  await expect(page.getByRole('alert')).toContainText('不能超过 20 MB')
  await expect(page.getByRole('alert')).toBeInViewport({ ratio: 1 })
  await expect(
    page.getByRole('button', { name: '下载九宫格拼图' }),
  ).toBeInViewport({ ratio: 1 })
  expect(
    await page
      .getByRole('main', { name: '功能区' })
      .evaluate(
        (main) =>
          main.scrollHeight <= main.clientHeight &&
          main.scrollWidth <= main.clientWidth,
      ),
  ).toBe(true)
  await expect(page.getByText('已添加 2 / 9 张')).toBeVisible()
  const picker = page.waitForEvent('filechooser')
  await page
    .getByRole('button', { name: '替换第 1 张图片', exact: true })
    .click()
  await (
    await picker
  ).setFiles({
    name: 'bad.png',
    mimeType: 'image/png',
    buffer: Buffer.from('bad'),
  })
  await expect(page.getByRole('alert')).toContainText('图片无法读取')
  await expect(
    page
      .getByRole('button', { name: '替换第 1 张图片', exact: true })
      .getByRole('img'),
  ).toHaveAttribute('alt', 'dropped.png')
  await page.getByRole('button', { name: '清空', exact: true }).click()
  await expect(page.getByText('已添加 0 / 9 张')).toBeVisible()
  await expect(
    page.getByRole('button', { name: '下载九宫格拼图' }),
  ).toBeDisabled()
})
