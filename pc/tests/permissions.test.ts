import { describe, expect, it } from 'vitest'
import { safeRedirect, togglePermission } from '../src/utils/permissions'
import type { Permission } from '../src/api/types'
const catalog: Permission[] = [
  {
    code: 'pc.access',
    name: '访问',
    group: '工作台',
    description: '',
    requires: [],
  },
  {
    code: 'user.read',
    name: '查看',
    group: '用户管理',
    description: '',
    requires: ['pc.access'],
  },
  {
    code: 'user.create',
    name: '新增',
    group: '用户管理',
    description: '',
    requires: ['pc.access', 'user.read'],
  },
  {
    code: 'role.read',
    name: '查看角色',
    group: '角色管理',
    description: '',
    requires: ['pc.access'],
  },
]
describe('permission dependencies', () => {
  it('selecting a write operation adds its complete dependency chain', () => {
    expect(togglePermission([], 'user.create', true, catalog)).toEqual([
      'pc.access',
      'user.create',
      'user.read',
    ])
  })
  it('removing read access removes dependent writes while retaining unrelated permissions', () => {
    expect(
      togglePermission(
        ['pc.access', 'user.read', 'user.create', 'role.read'],
        'user.read',
        false,
        catalog,
      ),
    ).toEqual(['pc.access', 'role.read'])
  })
  it('removing PC access clears all dependent permissions', () => {
    expect(
      togglePermission(
        catalog.map((item) => item.code),
        'pc.access',
        false,
        catalog,
      ),
    ).toEqual([])
  })
})
describe('post-login destinations', () => {
  it('allows known local pages and discards external or unknown redirects', () => {
    expect(safeRedirect('/users')).toBe('/users')
    expect(safeRedirect('/permissions?role=3')).toBe('/permissions?role=3')
    for (const value of [
      '//evil.test',
      'https://evil.test',
      '/login',
      '/users/../../evil',
      ['users'],
      undefined,
    ])
      expect(safeRedirect(value)).toBe('/overview')
  })
})
