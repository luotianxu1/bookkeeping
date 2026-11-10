import { request } from './request'
import type {
  Session,
  User,
  Role,
  Permission,
  Page,
  CreateUser,
  UserProfile,
  RoleProfile,
  UserStatus,
} from './types'
const root = '/api/admin'
export const api = {
  login: (username: string, password: string) =>
    request('/api/auth/login', {
      method: 'POST',
      body: { username, password },
    }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
  session: () => request<Session>(root + '/session'),
  users: (query: {
    keyword: string
    status: string
    roleCode: string
    page: number
    pageSize: number
  }) =>
    request<Page<User>>(
      root +
        '/users?' +
        new URLSearchParams(
          Object.entries(query).map(([key, value]) => [key, String(value)]),
        ),
    ),
  createUser: (body: CreateUser) =>
    request<User>(root + '/users', { method: 'POST', body }),
  updateUser: (id: number, body: UserProfile) =>
    request<User>(`${root}/users/${id}`, { method: 'PUT', body }),
  userStatus: (id: number, status: UserStatus) =>
    request<User>(`${root}/users/${id}/status`, {
      method: 'PUT',
      body: { status },
    }),
  userRole: (id: number, roleCode: string) =>
    request<User>(`${root}/users/${id}/role`, {
      method: 'PUT',
      body: { roleCode },
    }),
  roles: () => request<Role[]>(root + '/roles'),
  roleOptions: () => request<Role[]>(root + '/roles/options'),
  createRole: (body: { code: string; name: string; description: string }) =>
    request<Role>(root + '/roles', { method: 'POST', body }),
  updateRole: (id: number, body: RoleProfile) =>
    request<Role>(`${root}/roles/${id}`, { method: 'PUT', body }),
  deleteRole: (id: number) =>
    request<void>(`${root}/roles/${id}`, { method: 'DELETE' }),
  permissions: () => request<Permission[]>(root + '/permissions'),
  grant: (id: number, permissions: string[]) =>
    request<Role>(`${root}/roles/${id}/permissions`, {
      method: 'PUT',
      body: { permissions },
    }),
}
