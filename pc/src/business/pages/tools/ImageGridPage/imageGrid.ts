export const IMAGE_COUNT = 9
export const MAX_FILE_SIZE = 20 * 1024 * 1024
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export interface GridImage {
  name: string
  url: string
  image: HTMLImageElement
}

export interface GridOptions {
  width: number
  height: number
  gap: number
  fit: 'cover' | 'contain'
  format: 'png' | 'jpeg'
}

export async function renderImageGrid(
  images: GridImage[],
  options: GridOptions,
): Promise<Blob> {
  if (images.length !== IMAGE_COUNT) throw new Error('请先添加 9 张图片')
  const canvas = document.createElement('canvas')
  canvas.width = options.width
  canvas.height = options.height
  try {
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器无法生成图片，请更换浏览器重试')
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, options.width, options.height)
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    const cellWidth = (options.width - options.gap * 4) / 3
    const cellHeight = (options.height - options.gap * 4) / 3
    images.forEach(({ image }, index) => {
      // Round shared boundaries to avoid subpixel seams in the exported image.
      const x = Math.round(
        options.gap + (index % 3) * (cellWidth + options.gap),
      )
      const y = Math.round(
        options.gap + Math.floor(index / 3) * (cellHeight + options.gap),
      )
      const width =
        Math.round(
          options.gap + (index % 3) * (cellWidth + options.gap) + cellWidth,
        ) - x
      const height =
        Math.round(
          options.gap +
            Math.floor(index / 3) * (cellHeight + options.gap) +
            cellHeight,
        ) - y
      const iw = image.naturalWidth
      const ih = image.naturalHeight
      if (options.fit === 'cover') {
        const scale = Math.max(width / iw, height / ih)
        const sw = width / scale
        const sh = height / scale
        context.drawImage(
          image,
          (iw - sw) / 2,
          (ih - sh) / 2,
          sw,
          sh,
          x,
          y,
          width,
          height,
        )
      } else {
        const scale = Math.min(width / iw, height / ih)
        const dw = iw * scale
        const dh = ih * scale
        context.drawImage(
          image,
          x + (width - dw) / 2,
          y + (height - dh) / 2,
          dw,
          dh,
        )
      }
    })
    return await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (blob) =>
          blob ? resolve(blob) : reject(new Error('图片生成失败，请重试')),
        `image/${options.format}`,
        0.95,
      )
    })
  } finally {
    canvas.width = canvas.height = 0
  }
}
