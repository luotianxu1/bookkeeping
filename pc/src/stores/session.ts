import { reactive } from 'vue'
import { api } from '@/api/admin'
import { ApiError } from '@/api/request'
import type { Session } from '@/api/types'

export const session = reactive<{
  data: Session | null
  ready: boolean
  denied: boolean
}>({ data: null, ready: false, denied: false })
let pending: Promise<void> | null = null
let generation = 0
// 会话缓存有效期：路由守卫不再每次导航都强刷，
// 过期后的下一次导航才重新校验（权限变更仍走 permissions-changed 事件即时失效）
const SESSION_TTL_MS = 60_000
let loadedAt = 0
export const can = (permission: string) =>
  session.data?.permissions?.includes(permission) ?? false
export const isSuper = () => session.data?.user.roleCode === 'super_admin'
export function clearSession() {
  generation++
  session.data = null
  session.ready = false
  session.denied = false
  pending = null
  loadedAt = 0
}
export function invalidatePermissions() {
  session.ready = false
  loadedAt = 0
}
export async function loadSession(force = false): Promise<void> {
  if (pending) return pending
  const stale = Date.now() - loadedAt > SESSION_TTL_MS
  if (session.ready && !force && !stale) return
  const current = generation
  pending = (async () => {
    try {
      const data = await api.session()
      if (current === generation) {
        session.data = data
        session.denied = false
        session.ready = true
        loadedAt = Date.now()
      }
    } catch (error) {
      if (current !== generation) return
      if (error instanceof ApiError && [401, 403].includes(error.status)) {
        session.data = null
        session.ready = true
        session.denied = error.status === 403
        loadedAt = Date.now()
      } else throw error
    } finally {
      if (current === generation) pending = null
    }
  })()
  return pending
}
