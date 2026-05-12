const ACCESS_TOKEN_KEY = 'bookkeeping_access_token'
const TOKEN_TYPE_KEY = 'bookkeeping_token_type'

export interface StoredToken {
  accessToken: string
  tokenType: string
}

export function getStoredToken(): StoredToken | null {
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)
  if (!accessToken) {
    return null
  }

  return {
    accessToken,
    tokenType: window.localStorage.getItem(TOKEN_TYPE_KEY) ?? 'Bearer',
  }
}

export function setStoredToken(token: StoredToken) {
  window.localStorage.setItem(ACCESS_TOKEN_KEY, token.accessToken)
  window.localStorage.setItem(TOKEN_TYPE_KEY, token.tokenType)
}

export function clearStoredToken() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(TOKEN_TYPE_KEY)
}
