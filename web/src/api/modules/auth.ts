import { authRequest, requestDelete, requestGet, requestPost, requestPut } from '@/api/request'

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
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

export interface FamilyMember {
  userId: number
  displayName: string
  role: string
  status: '已绑定'
  canUnbind: boolean
}

export interface FamilyOverview {
  hasFamily: boolean
  inviteCode?: string | null
  memberCount: number
  members: FamilyMember[]
}

export interface JoinFamilyParams {
  inviteCode: string
}

export function login(params: LoginParams) {
  return requestPost<LoginResult, LoginParams>(authRequest, '/api/auth/login', params)
}

export function logout() {
  return requestPost<null>(authRequest, '/api/auth/logout')
}

export function register(params: RegisterParams) {
  return requestPost<null, RegisterParams>(authRequest, '/api/auth/register', params)
}

export function getCurrentUser() {
  return requestGet<CurrentUser>(authRequest, '/api/auth/me')
}

export function getFamilyOverview() {
  return requestGet<FamilyOverview>(authRequest, '/api/auth/family')
}

export function createFamily() {
  return requestPost<FamilyOverview>(authRequest, '/api/auth/family/create')
}

export function joinFamily(params: JoinFamilyParams) {
  return requestPost<FamilyOverview, JoinFamilyParams>(authRequest, '/api/auth/family/join', params)
}

export function unbindFamilyMember(userId: number) {
  return requestDelete<FamilyOverview>(authRequest, `/api/auth/family/members/${userId}`)
}

export function deleteCurrentAccount() {
  return requestDelete<void>(authRequest, '/api/auth/account')
}

export interface LoginSession {
  id: string
  deviceName: string
  ipAddress: string
  createdAt: string
  lastSeenAt: string
  expiresAt: string
  current: boolean
}
export interface RecoveryCodeStatus { configured: boolean; createdAt: string | null }
export function updateProfile(params: { displayName: string }) {
  return requestPut<CurrentUser>(authRequest, '/api/auth/profile', params)
}
export function changePassword(params: { currentPassword: string; newPassword: string }) {
  return requestPost<void>(authRequest, '/api/auth/password/change', params)
}
export function getRecoveryCodeStatus() {
  return requestGet<RecoveryCodeStatus>(authRequest, '/api/auth/recovery-code')
}
export function generateRecoveryCode(currentPassword: string) {
  return requestPost<{ code: string }>(authRequest, '/api/auth/recovery-code', { currentPassword })
}
export function resetPassword(params: { username: string; recoveryCode: string; newPassword: string }) {
  return requestPost<void>(authRequest, '/api/auth/password/reset', params)
}
export function getLoginSessions() {
  return requestGet<LoginSession[]>(authRequest, '/api/auth/sessions')
}
export function revokeLoginSession(id: string) {
  return requestDelete<void>(authRequest, `/api/auth/sessions/${encodeURIComponent(id)}`)
}
export function revokeOtherLoginSessions() {
  return requestPost<void>(authRequest, '/api/auth/sessions/revoke-others')
}
