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
export const can = (permission: string) =>
  session.data?.permissions.includes(permission) ?? false
export const isSuper = () => session.data?.user.roleCode === 'super_admin'
export function clearSession() {
  generation++
  session.data = null
  session.ready = false
  session.denied = false
  pending = null
}
export function invalidatePermissions() {
  session.ready = false
}
export async function loadSession(force = false): Promise<void> {
  if (pending) return pending
  if (session.ready && !force) return
  const current = generation
  pending = (async () => {
    try {
      const data = await api.session()
      if (current === generation) {
        session.data = data
        session.denied = false
        session.ready = true
      }
    } catch (error) {
      if (current !== generation) return
      if (error instanceof ApiError && [401, 403].includes(error.status)) {
        session.data = null
        session.ready = true
        session.denied = error.status === 403
      } else throw error
    } finally {
      if (current === generation) pending = null
    }
  })()
  return pending
}
