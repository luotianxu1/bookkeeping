import { authRequest, requestGet, requestPost } from '@/api/request'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface CurrentUser {
  id: number
  username: string
  phone?: string | null
  email?: string | null
  displayName: string
  avatarUrl?: string | null
  roleName?: string | null
}

export function login(params: LoginParams) {
  return requestPost<LoginResult, LoginParams>(authRequest, '/api/auth/login', params)
}

export function getCurrentUser() {
  return requestGet<CurrentUser>(authRequest, '/api/auth/me')
}
