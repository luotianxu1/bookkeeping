<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Download,
  ImagePlus,
  Plus,
  X,
} from '@lucide/vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  IMAGE_COUNT,
  IMAGE_TYPES,
  MAX_FILE_SIZE,
  renderImageGrid,
  type GridImage,
  type GridOptions,
} from './imageGrid'

const images = shallowRef<GridImage[]>([])
const fileInput = ref<HTMLInputElement>()
const replaceIndex = ref<number | null>(null)
const loading = ref(false)
const downloading = ref(false)
const dragDepth = ref(0)
const error = ref('')
const notice = ref('')
const size = ref(3000)
const ratios = [
  { value: '1:1', width: 1, height: 1, label: '1:1 · 正方形' },
  { value: '4:3', width: 4, height: 3, label: '4:3 · 横向' },
  { value: '3:4', width: 3, height: 4, label: '3:4 · 竖向' },
  { value: '3:2', width: 3, height: 2, label: '3:2 · 横向' },
  { value: '2:3', width: 2, height: 3, label: '2:3 · 竖向' },
  { value: '16:9', width: 16, height: 9, label: '16:9 · 宽屏' },
  { value: '9:16', width: 9, height: 16, label: '9:16 · 竖屏' },
]
const ratio = ref('1:1')
const selectedRatio = computed(
  () => ratios.find((item) => item.value === ratio.value)!,
)
function dimensionsFor(longEdge: number) {
  const { width, height } = selectedRatio.value
  const scale = longEdge / Math.max(width, height)
  return {
    width: Math.round(width * scale),
    height: Math.round(height * scale),
  }
}
const dimensions = computed(() => dimensionsFor(size.value))
const gap = ref(0)
const fit = ref<GridOptions['fit']>('cover')
const format = ref<GridOptions['format']>('png')
const busy = computed(() => loading.value || downloading.value)
const ready = computed(() => images.value.length === IMAGE_COUNT && !busy.value)
const gridStyle = computed(() => ({
  aspectRatio: `${dimensions.value.width} / ${dimensions.value.height}`,
  columnGap: `${(gap.value / (dimensions.value.width - gap.value * 2)) * 100}%`,
  rowGap: `${(gap.value / (dimensions.value.height - gap.value * 2)) * 100}%`,
  // CSS percentage padding on both axes is relative to the containing width.
  padding: `${(gap.value / dimensions.value.width) * 100}%`,
}))
const sheetStyle = computed(() => ({
  '--preview-ratio': dimensions.value.width / dimensions.value.height,
}))
const urls = new Set<string>()
let disposed = false
let downloadUrl = ''
let downloadTimer: ReturnType<typeof setTimeout> | undefined

function release(url: string) {
  URL.revokeObjectURL(url)
  urls.delete(url)
}

function chooseFiles(index: number | null = null) {
  if (busy.value) return
  replaceIndex.value = index
  if (fileInput.value) {
    // Set before opening the picker so replacing a cell only accepts one file.
    fileInput.value.multiple = index === null
    fileInput.value.click()
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  void addFiles(files, replaceIndex.value)
}

async function addFiles(files: File[], target: number | null = null) {
  if (busy.value || !files.length) return
  error.value = ''
  notice.value = ''
  const remaining = target === null ? IMAGE_COUNT - images.value.length : 1
  if (files.length > remaining) {
    error.value =
      remaining === 0
        ? '已添加 9 张图片，可点击图片进行替换，或先移除图片。'
        : `还可添加 ${remaining} 张图片，请重新选择；本次图片未添加。`
    return
  }
  loading.value = true
  const errors: string[] = []
  try {
    for (const file of files) {
      if (disposed) break
      if (!IMAGE_TYPES.includes(file.type)) {
        errors.push(`${file.name}：请使用 JPG、PNG 或 WebP 图片`)
        continue
      }
      if (file.size > MAX_FILE_SIZE) {
        errors.push(`${file.name}：单张图片不能超过 20 MB`)
        continue
      }
      const url = URL.createObjectURL(file)
      urls.add(url)
      try {
        const image = new Image()
        image.src = url
        await image.decode()
        if (disposed) {
          release(url)
          break
        }
        if (!image.naturalWidth || !image.naturalHeight)
          throw new Error('empty image')
        const entry = { name: file.name, url, image }
        if (target === null) images.value = [...images.value, entry]
        else {
          release(images.value[target]!.url)
          images.value = images.value.map((item, index) =>
            index === target ? entry : item,
          )
        }
      } catch {
        release(url)
        if (!disposed) errors.push(`${file.name}：图片无法读取，请换一张图片`)
      }
    }
    if (!disposed) error.value = errors.join('；')
  } finally {
    loading.value = false
  }
}

function onDrop(event: DragEvent) {
  dragDepth.value = 0
  void addFiles(Array.from(event.dataTransfer?.files ?? []))
}

function removeImage(index: number) {
  if (busy.value) return
  release(images.value[index]!.url)
  images.value = images.value.filter((_, position) => position !== index)
  error.value = notice.value = ''
}

function moveImage(index: number, offset: number) {
  if (busy.value) return
  const next = [...images.value]
  const destination = index + offset
  if (destination < 0 || destination >= next.length) return
  ;[next[index], next[destination]] = [next[destination]!, next[index]!]
  images.value = next
  notice.value = ''
}

function clearImages() {
  if (busy.value) return
  images.value.forEach((item) => release(item.url))
  images.value = []
  error.value = notice.value = ''
}

async function download() {
  if (!ready.value) return
  downloading.value = true
  error.value = notice.value = ''
  try {
    const blob = await renderImageGrid(images.value, {
      ...dimensions.value,
      gap: gap.value,
      fit: fit.value,
      format: format.value,
    })
    if (disposed) return
    if (downloadUrl) URL.revokeObjectURL(downloadUrl)
    clearTimeout(downloadTimer)
    downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `九宫格拼图-${dimensions.value.width}x${dimensions.value.height}.${format.value === 'jpeg' ? 'jpg' : 'png'}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    // Allow the browser to start reading the blob before releasing it.
    downloadTimer = setTimeout(() => {
      URL.revokeObjectURL(downloadUrl)
      downloadUrl = ''
    }, 30_000)
    notice.value = '拼图已生成，下载已开始。'
  } catch (cause) {
    if (!disposed)
      error.value = cause instanceof Error ? cause.message : '下载失败，请重试'
  } finally {
    downloading.value = false
  }
}

onBeforeUnmount(() => {
  disposed = true
  urls.forEach((url) => URL.revokeObjectURL(url))
  urls.clear()
  clearTimeout(downloadTimer)
  if (downloadUrl) URL.revokeObjectURL(downloadUrl)
})
</script>

<template>
  <section class="desktop-workspace image-grid-page">
    <PageHeader title="九宫格拼图">
      <template #right>
        <span class="local-note">本机处理 · 图片不会上传至服务器</span>
      </template>
    </PageHeader>
    <input
      ref="fileInput"
      class="file-input"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      multiple
      aria-label="选择拼图图片"
      @change="onFileChange"
    />

    <div class="collage-layout">
      <section class="desktop-panel preview-panel" aria-label="拼图编辑区">
        <header class="desktop-panel-header">
          <h2>拼图预览</h2>
          <span class="image-count" aria-live="polite">
            已添加 {{ images.length }} / 9 张
          </span>
        </header>
        <div class="preview-stage">
          <div class="preview-sheet" :style="sheetStyle">
            <div
              class="collage-grid"
              :style="gridStyle"
              aria-label="九宫格预览"
            >
              <div
                v-for="slot in IMAGE_COUNT"
                :key="slot"
                class="collage-cell"
                :class="{ filled: images[slot - 1] }"
              >
                <template v-if="images[slot - 1]">
                  <button
                    class="image-button"
                    type="button"
                    :disabled="busy"
                    :aria-label="`替换第 ${slot} 张图片`"
                    :title="images[slot - 1]!.name"
                    @click="chooseFiles(slot - 1)"
                  >
                    <img
                      :src="images[slot - 1]!.url"
                      :alt="images[slot - 1]!.name"
                      :style="{ objectFit: fit }"
                    />
                    <span class="replace-hint">点击替换</span>
                  </button>
                  <span class="slot-number">
                    {{ String(slot).padStart(2, '0') }}
                  </span>
                  <div class="cell-actions">
                    <button
                      type="button"
                      :disabled="busy || slot === 1"
                      :aria-label="`前移第 ${slot} 张图片`"
                      title="前移"
                      @click="moveImage(slot - 1, -1)"
                    >
                      <ArrowLeft :size="14" />
                    </button>
                    <button
                      type="button"
                      :disabled="busy || slot === images.length"
                      :aria-label="`后移第 ${slot} 张图片`"
                      title="后移"
                      @click="moveImage(slot - 1, 1)"
                    >
                      <ArrowRight :size="14" />
                    </button>
                    <button
                      type="button"
                      :disabled="busy"
                      :aria-label="`移除第 ${slot} 张图片`"
                      title="移除"
                      @click="removeImage(slot - 1)"
                    >
                      <X :size="14" />
                    </button>
                  </div>
                </template>
                <button
                  v-else
                  class="empty-cell"
                  type="button"
                  :disabled="busy"
                  :aria-label="`添加图片，当前 ${images.length} 张`"
                  @click="chooseFiles()"
                >
                  <span class="empty-number">
                    {{ String(slot).padStart(2, '0') }}
                  </span>
                  <Plus :size="22" />
                  <span>添加图片</span>
                </button>
              </div>
            </div>
          </div>
        </div>
        <footer class="preview-footer">
          <span>从左到右、从上到下排列；点击图片替换，使用箭头调整顺序。</span>
          <span>{{ dimensions.width }} × {{ dimensions.height }} px</span>
        </footer>
        <p v-if="error" class="feedback error-feedback" role="alert">
          {{ error }}
        </p>
        <p v-else-if="notice" class="feedback success-feedback" role="status">
          {{ notice }}
        </p>
      </section>

      <aside class="collage-sidebar" aria-label="图片与导出设置">
        <section class="desktop-panel">
          <header class="desktop-panel-header">
            <h2>添加照片</h2>
            <el-button
              text
              :disabled="!images.length || busy"
              @click="clearImages"
            >
              清空
            </el-button>
          </header>
          <div class="upload-body">
            <div
              class="upload-zone"
              :class="{ dragging: dragDepth > 0 }"
              @dragenter.prevent="dragDepth++"
              @dragleave.prevent="dragDepth = Math.max(0, dragDepth - 1)"
              @dragover.prevent
              @drop.prevent="onDrop"
              role="group"
              aria-label="图片拖放区"
            >
              <button
                class="upload-select-button"
                type="button"
                :disabled="busy"
                @click="chooseFiles()"
              >
                <ImagePlus :size="18" aria-hidden="true" />
                <span>{{ loading ? '正在读取图片…' : '选择图片' }}</span>
              </button>
              <span class="upload-hint">或拖入图片，可分批添加，最多 9 张</span>
              <span class="upload-formats">
                JPG / PNG / WebP · 每张不超过 20 MB
              </span>
            </div>
          </div>
        </section>

        <section class="desktop-panel">
          <header class="desktop-panel-header">
            <h2>导出设置</h2>
            <span class="setting-tag">3 × 3</span>
          </header>
          <div class="settings-body">
            <div class="setting-field">
              <label for="image-grid-ratio">导出比例</label>
              <select id="image-grid-ratio" v-model="ratio" :disabled="busy">
                <option
                  v-for="item in ratios"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </option>
              </select>
            </div>
            <div class="setting-field">
              <label for="image-grid-fit">图片适配</label>
              <select
                id="image-grid-fit"
                v-model="fit"
                :disabled="busy"
                :title="
                  fit === 'cover'
                    ? '居中裁剪以铺满格子，超出部分将被裁去'
                    : '保留完整图片，空余部分填充白色'
                "
              >
                <option value="cover">居中裁剪</option>
                <option value="contain">完整显示</option>
              </select>
            </div>
            <div class="setting-field">
              <label for="image-grid-gap">白色留边与间距</label>
              <select id="image-grid-gap" v-model.number="gap" :disabled="busy">
                <option :value="0">无缝拼接</option>
                <option :value="12">细留白 · 12 px</option>
                <option :value="30">宽留白 · 30 px</option>
              </select>
            </div>
            <div class="setting-field">
              <label for="image-grid-size">导出尺寸</label>
              <select
                id="image-grid-size"
                v-model.number="size"
                :disabled="busy"
              >
                <option :value="1500">标准 · 长边 1500</option>
                <option :value="3000">高清 · 长边 3000</option>
              </select>
            </div>
            <div class="setting-field">
              <label for="image-grid-format">文件格式</label>
              <select id="image-grid-format" v-model="format" :disabled="busy">
                <option value="png">PNG · 无损画质</option>
                <option value="jpeg">JPG · 更小文件</option>
              </select>
            </div>
            <div class="setting-summary">
              <span>导出像素</span>
              <strong>{{ dimensions.width }} × {{ dimensions.height }}</strong>
            </div>
            <el-button
              type="primary"
              size="large"
              class="full-width download-button"
              :disabled="!ready"
              :loading="downloading"
              @click="download"
            >
              <Download v-if="!downloading" :size="17" />
              {{ downloading ? '正在生成拼图…' : '下载九宫格拼图' }}
            </el-button>
            <p class="download-help" aria-live="polite">
              {{
                images.length < IMAGE_COUNT
                  ? `再添加 ${IMAGE_COUNT - images.length} 张图片即可下载`
                  : '下载为一张完整图片，不含序号与操作按钮'
              }}
            </p>
          </div>
        </section>
        <p class="session-note">图片仅保留在当前页面，离开或刷新前请先下载。</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.image-grid-page.desktop-workspace {
  height: 100%;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  animation: none;
}
.local-note,
.preview-footer,
.session-note,
.download-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.file-input {
  display: none;
}
.collage-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  min-height: 0;
  align-items: stretch;
}
.image-grid-page .desktop-panel-header {
  min-height: 42px;
  padding: 8px 14px;
}
.preview-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  min-height: 0;
}
.image-count {
  color: var(--el-color-primary);
  font-family: var(--font-mono);
  font-size: 12px;
}
.preview-stage {
  container-type: size;
  display: grid;
  place-items: center;
  min-height: 0;
  padding: 16px;
  background: var(--el-fill-color-lighter);
}
.preview-sheet {
  width: min(100cqw, calc(100cqh * var(--preview-ratio)));
}
.collage-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
  width: 100%;
  background: white;
  box-shadow: var(--shadow-1);
}
.collage-cell {
  position: relative;
  min-width: 0;
  min-height: 0;
  background: white;
}
.image-button,
.empty-cell {
  width: 100%;
  height: 100%;
  display: flex;
  padding: 0;
  border: 0;
}
.image-button {
  position: relative;
  background: white;
}
.image-button img {
  display: block;
  width: 100%;
  height: 100%;
}
.empty-cell {
  position: relative;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #8392ab;
  font-size: 12px;
  background: #f2f5fb;
  border: 1px solid white;
  transition: background 160ms;
}
.empty-cell:hover {
  color: var(--el-color-primary);
  background: #e8eefb;
}
.empty-number {
  position: absolute;
  left: 12px;
  top: 9px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: #99a6bb;
}
.slot-number {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: white;
  background: #18233899;
  pointer-events: none;
}
.replace-hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: #18233833;
  color: white;
  opacity: 0;
  font-size: 12px;
}
.image-button:hover .replace-hint,
.image-button:focus-visible .replace-hint {
  opacity: 1;
}
.cell-actions {
  position: absolute;
  bottom: 8px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 1px;
  padding: 2px;
  background: #182338cc;
  border-radius: 6px;
}
.cell-actions button {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: white;
}
.cell-actions button:hover:not(:disabled) {
  background: #ffffff33;
}
.cell-actions button:disabled {
  opacity: 0.3;
}
.preview-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
}
.preview-footer > :last-child {
  font-family: var(--font-mono);
  white-space: nowrap;
}
.collage-sidebar {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
}
.upload-body,
.settings-body {
  display: grid;
  gap: 12px;
  padding: 12px;
}
.settings-body {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.upload-zone {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px dashed var(--el-color-primary-light-5);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}
.upload-hint,
.upload-formats {
  pointer-events: none;
  overflow-wrap: anywhere;
}
.upload-zone.dragging {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}
.upload-select-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}
.upload-select-button:hover:not(:disabled) {
  background: var(--el-color-primary-light-8);
  border-color: var(--el-color-primary);
}
.upload-select-button:disabled {
  opacity: 0.6;
}
.upload-formats {
  font-size: 11px;
}
.setting-tag {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.setting-field {
  display: grid;
  gap: 5px;
  min-width: 0;
  font-size: 12px;
}
.setting-field select {
  width: 100%;
  min-width: 0;
  height: 34px;
  padding: 5px 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  font: inherit;
}
.setting-field select:focus-visible {
  outline: 2px solid var(--color-ring-focus);
  outline-offset: 2px;
}
.setting-summary {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 5px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.setting-summary strong {
  display: flex;
  align-items: center;
  min-height: 34px;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.image-grid-page :deep(.el-button svg) {
  margin-right: 6px;
}
.download-button {
  grid-column: 1 / -1;
}
.download-help {
  grid-column: 1 / -1;
  text-align: center;
  margin-top: -6px;
  font-size: 11px;
}
.feedback {
  margin: 0 12px 12px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.error-feedback {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.success-feedback {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.session-note {
  padding: 0 4px;
  line-height: 1.7;
}
@media (max-width: 1180px) {
  .collage-layout {
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 12px;
  }
  .preview-stage {
    padding: 12px;
  }
}
@media (max-height: 760px) {
  .image-grid-page.desktop-workspace {
    gap: 8px;
  }
  .image-grid-page .desktop-panel-header {
    min-height: 36px;
    padding: 4px 12px;
  }
  .collage-sidebar {
    gap: 8px;
  }
  .upload-body,
  .settings-body {
    padding: 8px;
    gap: 8px;
  }
  .upload-zone {
    padding: 6px;
  }
  .upload-select-button {
    min-height: 34px;
    padding: 5px 8px;
  }
  .setting-field,
  .setting-summary {
    gap: 3px;
  }
  .setting-field select,
  .setting-summary strong {
    height: 30px;
    min-height: 30px;
  }
  .download-button {
    height: 34px;
  }
  .preview-footer {
    padding: 6px 12px;
  }
  .cell-actions {
    bottom: 4px;
  }
  .slot-number {
    top: 4px;
    left: 4px;
  }
  .empty-cell {
    gap: 3px;
  }
  .empty-number {
    top: 4px;
    left: 6px;
  }
}
@media (max-width: 850px) {
  .local-note {
    font-size: 11px;
  }
  .preview-footer > :first-child {
    font-size: 11px;
  }
}
</style>
