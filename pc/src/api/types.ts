export type UserStatus = 'active' | 'disabled' | 'locked'
export interface User {
  id: number
  username: string
  displayName: string
  phone: string | null
  email: string | null
  status: UserStatus
  roleCode: string
  roleName: string
  lastLoginAt: string | null
  createdAt: string
}
export interface Role {
  id: number
  code: string
  name: string
  description: string
  status: 'active' | 'disabled'
  builtIn: boolean
  userCount: number
  permissions: string[]
}
export interface Permission {
  code: string
  name: string
  group: string
  description: string
  requires: string[]
}
export interface Session {
  user: User
  permissions: string[]
}
export interface Page<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}
export interface UserProfile {
  displayName: string
  phone: string
  email: string
}
export interface CreateUser extends UserProfile {
  username: string
  password: string
}
export interface RoleProfile {
  name: string
  description: string
  status: 'active' | 'disabled'
}
