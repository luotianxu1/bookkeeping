import { session, clearSession } from '@/stores/session'
export interface StoredCurrentUser {
  id: number
  username: string
  phone?: string | null
  email?: string | null
  displayName: string
  avatarUrl?: string | null
  roleName?: string | null
}
export function getStoredCurrentUser(): StoredCurrentUser | null {
  const user = session.data?.user
  return user ? { ...user, roleName: user.roleCode } : null
}
export function setStoredCurrentUser(user: StoredCurrentUser) {
  // Personal profile responses must never grant or replace administration permissions.
  if (session.data?.user.id !== user.id) return
  Object.assign(session.data.user, {
    displayName: user.displayName,
    username: user.username,
    phone: user.phone,
    email: user.email,
    avatarUrl: user.avatarUrl,
  })
}
export function clearStoredCurrentUser() {
  clearSession()
}
