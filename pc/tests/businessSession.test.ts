import { afterEach, describe, expect, it } from 'vitest'
import { clearSession, session } from '../src/stores/session'
import {
  getStoredCurrentUser,
  setStoredCurrentUser,
} from '../src/business/utils/current-user'
import { useFoodSelection } from '../src/business/pages/food/shared'
import type { Session } from '../src/api/types'
const makeSession = (id: number): Session => ({
  user: {
    id,
    username: 'test',
    displayName: '测试',
    phone: null,
    email: null,
    status: 'active',
    roleCode: 'user',
    roleName: '普通用户',
    createdAt: '2026-11-10',
    lastLoginAt: null,
  },
  permissions: ['pc.access'],
})
afterEach(clearSession)
describe('business identity bridge', () => {
  it('uses current in-memory identity and never accepts profile privilege changes', () => {
    session.data = makeSession(7)
    expect(getStoredCurrentUser()).toMatchObject({ id: 7, roleName: 'user' })
    setStoredCurrentUser({
      id: 8,
      username: 'other',
      displayName: '其他账号',
      roleName: 'super_admin',
    })
    expect(session.data.user.id).toBe(7)
    setStoredCurrentUser({
      id: 7,
      username: 'test',
      displayName: '新昵称',
      roleName: 'super_admin',
    })
    expect(session.data.user.displayName).toBe('新昵称')
    expect(session.data.user.roleCode).toBe('user')
    expect(session.data.permissions).toEqual(['pc.access'])
    clearSession()
    expect(getStoredCurrentUser()).toBeNull()
  })
  it('clears selected dishes when logging out or changing accounts', () => {
    session.data = makeSession(7)
    const selection = useFoodSelection()
    selection.addDish(1)
    expect(selection.selectedCount.value).toBe(1)
    session.data = makeSession(8)
    expect(selection.selectedCount.value).toBe(0)
    selection.addDish(2)
    clearSession()
    expect(selection.selectedCount.value).toBe(0)
  })
})
