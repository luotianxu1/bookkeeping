const DESIGN_WIDTH = 375
const ROOT_VALUE = 37.5
const MIN_VIEWPORT_WIDTH = 320
const MAX_VIEWPORT_WIDTH = 540

function updateRootFontSize() {
  const html = document.documentElement
  const viewportWidth = Math.min(
    Math.max(html.clientWidth || window.innerWidth, MIN_VIEWPORT_WIDTH),
    MAX_VIEWPORT_WIDTH,
  )

  html.style.fontSize = `${(viewportWidth / DESIGN_WIDTH) * ROOT_VALUE}px`
}

export function setupRem() {
  updateRootFontSize()
  window.addEventListener('resize', updateRootFontSize, { passive: true })
  window.addEventListener('pageshow', updateRootFontSize, { passive: true })
}
