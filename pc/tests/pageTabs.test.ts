import { beforeEach, describe, expect, it } from 'vitest'
import {
  pageTabs,
  openPageTab,
  closePageTab,
  closeOtherPageTabs,
  closeRightPageTabs,
  closeAllPageTabs,
  clearPageTabs,
} from '../src/stores/pageTabs'

const tab = (path: string) => ({ key: path, path, title: path })

beforeEach(clearPageTabs)

describe('opened page tabs', () => {
  it('starts with a fixed overview tab and refuses to close it', () => {
    expect(pageTabs.map((item) => item.key)).toEqual(['/overview'])
    closePageTab('/overview')
    expect(pageTabs.map((item) => item.key)).toEqual(['/overview'])
  })

  it('opens unique tabs by full path and ignores duplicates', () => {
    openPageTab(tab('/finance/transactions'))
    openPageTab(tab('/finance/analysis'))
    openPageTab(tab('/finance/transactions'))
    expect(pageTabs.map((item) => item.key)).toEqual([
      '/overview',
      '/finance/transactions',
      '/finance/analysis',
    ])
  })

  it('closes a tab by key without touching neighbours', () => {
    openPageTab(tab('/a'))
    openPageTab(tab('/b'))
    closePageTab('/a')
    expect(pageTabs.map((item) => item.key)).toEqual(['/overview', '/b'])
  })

  it('keeps only the target and fixed tab when closing others', () => {
    openPageTab(tab('/a'))
    openPageTab(tab('/b'))
    closeOtherPageTabs('/b')
    expect(pageTabs.map((item) => item.key)).toEqual(['/overview', '/b'])
  })

  it('keeps tabs to the left plus the fixed tab when closing to the right', () => {
    openPageTab(tab('/a'))
    openPageTab(tab('/b'))
    openPageTab(tab('/c'))
    closeRightPageTabs('/b')
    expect(pageTabs.map((item) => item.key)).toEqual(['/overview', '/a', '/b'])
  })

  it('closing all resets to the fixed home tab', () => {
    openPageTab(tab('/a'))
    openPageTab(tab('/b'))
    closeAllPageTabs()
    expect(pageTabs.map((item) => item.key)).toEqual(['/overview'])
  })
})
