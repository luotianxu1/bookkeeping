const CURRENT_USER_KEY = 'bookkeeping_current_user'

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
  const raw = window.localStorage.getItem(CURRENT_USER_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as StoredCurrentUser
  } catch {
    clearStoredCurrentUser()
    return null
  }
}

export function setStoredCurrentUser(user: StoredCurrentUser) {
  window.localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user))
}

export function clearStoredCurrentUser() {
  window.localStorage.removeItem(CURRENT_USER_KEY)
}
