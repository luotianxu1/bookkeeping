import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { modules, findMenu } from '../src/data/modules'
import { businessRoutes, publicBusinessRoutes } from '../src/router/business'
import { createRouter, createMemoryHistory } from 'vue-router'
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    ...businessRoutes,
    ...publicBusinessRoutes,
    { path: '/users', component: {} },
    { path: '/roles', component: {} },
    { path: '/permissions', component: {} },
  ],
})
describe('desktop business coverage', () => {
  it('every H5 business route remains reachable in PC', () => {
    for (const file of ['finance', 'tools', 'food', 'main']) {
      const source = readFileSync(
        new URL(`../../web/src/router/modules/${file}.ts`, import.meta.url),
        'utf8',
      )
      const names = [...source.matchAll(/name:\s*'([^']+)'/g)]
        .map((x) => x[1])
        .filter((x) => x !== 'login')
      for (const name of names) expect(router.hasRoute(name), name).toBe(true)
    }
  })
  it('each sidebar destination resolves and belongs to its declared module', () => {
    for (const group of modules)
      for (const item of group.entries) {
        expect(
          router.resolve(item.path).matched.length,
          item.path,
        ).toBeGreaterThan(0)
        expect(findMenu(item.path)?.module.id, item.path).toBe(group.id)
      }
  })
  it('detail routes remain in the parent module', () => {
    expect(findMenu('/finance/accounts/cash/12')?.module.id).toBe('assets')
    expect(findMenu('/food/dishes/12')?.entry.path).toBe('/food/dishes')
    expect(findMenu('/tools/travel-plans/12/edit')?.entry.path).toBe(
      '/tools/travel-plans',
    )
    expect(
      findMenu('/finance/global-market/detail/stock/600000')?.module.id,
    ).toBe('market')
  })
})
