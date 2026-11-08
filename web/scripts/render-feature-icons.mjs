// Rebuild only the PNGs registered by FeatureIcon from their editable SVG sources.
// Run from web/: node scripts/render-feature-icons.mjs
import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'

const assets = new URL('../src/assets/system-icons/', import.meta.url)
const component = await readFile(new URL('../src/components/common/FeatureIcon/index.vue', import.meta.url), 'utf8')
const names = [...component.matchAll(/from '@\/assets\/system-icons\/([\w-]+)\.png'/g)].map(match => match[1])
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  for (const name of names) {
    const svg = await readFile(new URL(`source/${name}.svg`, assets), 'utf8')
    const src = `data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`
    const png = await page.evaluate(async src => {
      const image = new Image()
      image.src = src
      await image.decode()
      const canvas = document.createElement('canvas')
      canvas.width = canvas.height = 64
      canvas.getContext('2d').drawImage(image, 0, 0, 64, 64)
      return canvas.toDataURL('image/png').split(',')[1]
    }, src)
    await writeFile(new URL(`${name}.png`, assets), Buffer.from(png, 'base64'))
  }
  console.log(`Rendered ${names.length} finance feature icons to ${fileURLToPath(assets)}`)
} finally {
  await browser.close()
}
