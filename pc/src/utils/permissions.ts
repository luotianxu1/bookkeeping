import type { Permission } from '../api/types'

// Add prerequisites on selection and remove dependent operations on deselection.
export function togglePermission(
  selected: string[],
  code: string,
  checked: boolean,
  catalog: Permission[],
): string[] {
  const result = new Set(selected)
  if (checked) {
    const add = (key: string) => {
      if (result.has(key)) return
      result.add(key)
      catalog.find((item) => item.code === key)?.requires.forEach(add)
    }
    add(code)
  } else {
    result.delete(code)
    let changed = true
    while (changed) {
      changed = false
      for (const item of catalog) {
        if (
          result.has(item.code) &&
          item.requires.some((key) => !result.has(key))
        ) {
          result.delete(item.code)
          changed = true
        }
      }
    }
  }
  return [...result].sort()
}
export function safeRedirect(value: unknown): string {
  return typeof value === 'string' &&
    /^\/(overview|users|roles|permissions)(\?|$)/.test(value)
    ? value
    : '/overview'
}
