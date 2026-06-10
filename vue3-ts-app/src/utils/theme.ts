import { computed, ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'app-theme-mode'
const themeMode = ref<ThemeMode>('light')
let initialized = false

function isThemeMode(value: string | null): value is ThemeMode {
  return value === 'light' || value === 'dark'
}

function resolveInitialTheme(): ThemeMode {
  if (typeof window === 'undefined') {
    return 'light'
  }

  const stored = window.localStorage.getItem(STORAGE_KEY)
  if (isThemeMode(stored)) {
    return stored
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(mode: ThemeMode) {
  themeMode.value = mode

  if (typeof document === 'undefined') {
    return
  }

  document.documentElement.dataset.theme = mode
  document.documentElement.style.colorScheme = mode
  document.body?.setAttribute('data-theme', mode)
}

export function initTheme() {
  if (initialized) {
    applyTheme(themeMode.value)
    return
  }

  initialized = true
  applyTheme(resolveInitialTheme())
}

export function setThemeMode(mode: ThemeMode) {
  applyTheme(mode)

  if (typeof window !== 'undefined') {
    window.localStorage.setItem(STORAGE_KEY, mode)
  }
}

export function toggleTheme() {
  setThemeMode(themeMode.value === 'dark' ? 'light' : 'dark')
}

export function useTheme() {
  return {
    themeMode: computed(() => themeMode.value),
    isDark: computed(() => themeMode.value === 'dark'),
    setThemeMode,
    toggleTheme,
  }
}
