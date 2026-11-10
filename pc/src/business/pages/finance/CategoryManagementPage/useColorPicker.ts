// 分类颜色选择器：色轮、明度场、HEX 输入与应用逻辑的自洽块。
import { computed, ref } from 'vue'

export type UseColorPickerOptions = {
  /** 打开选择器时用于初始化的来源颜色（页面表单当前颜色）。 */
  getSourceColor: () => string
  /** 点击「应用」后把选中颜色写回页面。 */
  onApply: (color: string) => void
}

export function useColorPicker(options: UseColorPickerOptions) {
  const showColorPickerModal = ref(false)
  const colorPickerColor = ref(options.getSourceColor())
  const colorPickerDraft = ref(options.getSourceColor().toUpperCase())
  const colorPickerHue = ref(210)
  const colorPickerSaturation = ref(0.32)
  const colorPickerValue = ref(0.38)
  const colorWheelRef = ref<HTMLElement | null>(null)
  const colorFieldRef = ref<HTMLElement | null>(null)

  const colorWheelHandleStyle = computed(() => {
    const angle = (colorPickerHue.value * Math.PI) / 180
    return {
      left: `${50 + Math.sin(angle) * 43}%`,
      top: `${50 - Math.cos(angle) * 43}%`,
    }
  })
  const colorFieldHandleStyle = computed(() => ({
    left: `${colorPickerSaturation.value * 100}%`,
    top: `${(1 - colorPickerValue.value) * 100}%`,
  }))
  const colorWheelHueStyle = computed(() => ({
    background: `hsl(${colorPickerHue.value} 100% 50%)`,
  }))
  const colorFieldHueStyle = computed(() => ({
    background: `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, transparent), hsl(${colorPickerHue.value} 100% 50%)`,
  }))

  function openColorPickerModal() {
    syncColorPickerFromHex(options.getSourceColor())
    showColorPickerModal.value = true
  }

  function handleColorDraftInput(event: Event) {
    const value = (event.target as HTMLInputElement).value.trim().toUpperCase()
    colorPickerDraft.value = value
    if (/^#[\dA-F]{6}$/.test(value)) {
      syncColorPickerFromHex(value)
    }
  }

  function resetColorDraft() {
    colorPickerDraft.value = colorPickerColor.value.toUpperCase()
  }

  function selectCategoryColor(color: string) {
    syncColorPickerFromHex(color)
  }

  function applyCategoryColor() {
    options.onApply(colorPickerColor.value)
    showColorPickerModal.value = false
  }

  function syncColorPickerFromHex(hex: string) {
    const hsv = hexToHsv(hex)
    colorPickerHue.value = hsv.h
    colorPickerSaturation.value = hsv.s
    colorPickerValue.value = hsv.v
    colorPickerColor.value = hsvToHex(hsv.h, hsv.s, hsv.v)
    colorPickerDraft.value = colorPickerColor.value.toUpperCase()
  }

  function updateColorFromHsv() {
    colorPickerColor.value = hsvToHex(
      colorPickerHue.value,
      colorPickerSaturation.value,
      colorPickerValue.value,
    )
    colorPickerDraft.value = colorPickerColor.value.toUpperCase()
  }

  function handleColorWheelPointer(event: PointerEvent) {
    const wheel = colorWheelRef.value
    if (!wheel) {
      return
    }
    wheel.setPointerCapture(event.pointerId)
    updateHueFromPointer(event)
    wheel.addEventListener('pointermove', updateHueFromPointer)
    wheel.addEventListener('pointerup', stopColorWheelPointer, { once: true })
    wheel.addEventListener('pointercancel', stopColorWheelPointer, {
      once: true,
    })
  }

  function updateHueFromPointer(event: PointerEvent) {
    const wheel = colorWheelRef.value
    if (!wheel) {
      return
    }
    const rect = wheel.getBoundingClientRect()
    const x = event.clientX - (rect.left + rect.width / 2)
    const y = event.clientY - (rect.top + rect.height / 2)
    const distance = Math.hypot(x, y)
    if (distance < rect.width * 0.31 || distance > rect.width * 0.52) {
      return
    }
    colorPickerHue.value = ((Math.atan2(x, -y) * 180) / Math.PI + 360) % 360
    updateColorFromHsv()
  }

  function stopColorWheelPointer() {
    colorWheelRef.value?.removeEventListener(
      'pointermove',
      updateHueFromPointer,
    )
  }

  function handleColorWheelKeydown(event: KeyboardEvent) {
    const step = event.shiftKey ? 10 : 1
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
      event.preventDefault()
      colorPickerHue.value = (colorPickerHue.value + step) % 360
      updateColorFromHsv()
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
      event.preventDefault()
      colorPickerHue.value = (colorPickerHue.value - step + 360) % 360
      updateColorFromHsv()
    }
  }

  function handleColorFieldPointer(event: PointerEvent) {
    const field = colorFieldRef.value
    if (!field) {
      return
    }
    field.setPointerCapture(event.pointerId)
    updateSaturationValueFromPointer(event)
    field.addEventListener('pointermove', updateSaturationValueFromPointer)
    field.addEventListener('pointerup', stopColorFieldPointer, { once: true })
    field.addEventListener('pointercancel', stopColorFieldPointer, {
      once: true,
    })
  }

  function updateSaturationValueFromPointer(event: PointerEvent) {
    const field = colorFieldRef.value
    if (!field) {
      return
    }
    const rect = field.getBoundingClientRect()
    colorPickerSaturation.value = Math.min(
      1,
      Math.max(0, (event.clientX - rect.left) / rect.width),
    )
    colorPickerValue.value = Math.min(
      1,
      Math.max(0, 1 - (event.clientY - rect.top) / rect.height),
    )
    updateColorFromHsv()
  }

  function stopColorFieldPointer() {
    colorFieldRef.value?.removeEventListener(
      'pointermove',
      updateSaturationValueFromPointer,
    )
  }

  function hexToHsv(hex: string) {
    const normalized = hex.replace('#', '')
    const r = Number.parseInt(normalized.slice(0, 2), 16) / 255
    const g = Number.parseInt(normalized.slice(2, 4), 16) / 255
    const b = Number.parseInt(normalized.slice(4, 6), 16) / 255
    const max = Math.max(r, g, b)
    const min = Math.min(r, g, b)
    const delta = max - min
    let h = 0
    if (delta) {
      if (max === r) h = 60 * (((g - b) / delta) % 6)
      else if (max === g) h = 60 * ((b - r) / delta + 2)
      else h = 60 * ((r - g) / delta + 4)
    }
    return { h: (h + 360) % 360, s: max ? delta / max : 0, v: max }
  }

  function hsvToHex(h: number, s: number, v: number) {
    const chroma = v * s
    const x = chroma * (1 - Math.abs(((h / 60) % 2) - 1))
    const m = v - chroma
    const [r, g, b] =
      h < 60
        ? [chroma, x, 0]
        : h < 120
          ? [x, chroma, 0]
          : h < 180
            ? [0, chroma, x]
            : h < 240
              ? [0, x, chroma]
              : h < 300
                ? [x, 0, chroma]
                : [chroma, 0, x]
    return `#${[r, g, b]
      .map((value) =>
        Math.round((value + m) * 255)
          .toString(16)
          .padStart(2, '0'),
      )
      .join('')}`
  }

  return {
    showColorPickerModal,
    colorPickerColor,
    colorPickerDraft,
    colorPickerHue,
    colorPickerSaturation,
    colorPickerValue,
    colorWheelRef,
    colorFieldRef,
    colorWheelHandleStyle,
    colorFieldHandleStyle,
    colorWheelHueStyle,
    colorFieldHueStyle,
    openColorPickerModal,
    handleColorDraftInput,
    resetColorDraft,
    selectCategoryColor,
    applyCategoryColor,
    handleColorWheelPointer,
    handleColorWheelKeydown,
    handleColorFieldPointer,
  }
}
