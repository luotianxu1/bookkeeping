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
  username: string
}

export function login(params: LoginParams) {
  return requestPost<LoginResult, LoginParams>(authRequest, '/api/auth/login', params)
}

export function getCurrentUser() {
  return requestGet<CurrentUser>(authRequest, '/api/auth/me')
}
