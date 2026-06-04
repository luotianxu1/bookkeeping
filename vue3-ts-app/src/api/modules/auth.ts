import { authRequest, requestDelete, requestGet, requestPost } from '@/api/request'

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
