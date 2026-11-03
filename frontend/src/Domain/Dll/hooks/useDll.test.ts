import { describe, expect, it } from 'vitest'
import { dllAtLeast, dllAutoUpdatePending, dllUpdatesItself, type DllRelease } from './useDll'

const release = (autoUpdate: boolean): DllRelease => ({
  id: 1,
  version: '1.2.1',
  notes: null,
  fileName: 'version.dll',
  fileSize: 1,
  publishedBy: null,
  publishedAt: '2026-10-31T18:51:00Z',
  autoUpdate,
})

describe('dllUpdatesItself', () => {
  it('só quando a release é assinada e a DLL instalada já tem o atualizador (1.2.0+)', () => {
    expect(dllUpdatesItself('1.2.0', release(true))).toBe(true)
    expect(dllUpdatesItself('1.10', release(true))).toBe(true)
    expect(dllUpdatesItself('2.0.0', release(true))).toBe(true)
    expect(dllUpdatesItself('1.1.0', release(true))).toBe(false)
    expect(dllUpdatesItself('1.2.0', release(false))).toBe(false)
    expect(dllUpdatesItself(null, release(true))).toBe(false)
    expect(dllUpdatesItself('x.y', release(true))).toBe(false)
  })
})

describe('dllAutoUpdatePending', () => {
  const published = new Date('2026-10-31T18:51:00Z').getTime()
  const day = 24 * 60 * 60 * 1000
  it('várias versões atrás ainda é troca automática nos 3 primeiros dias da mais recente', () => {
    expect(dllAutoUpdatePending('1.9.0', release(true), 1, published + 10 * day)).toBe(true)
    expect(dllAutoUpdatePending('1.9.0', release(true), 4, published + day)).toBe(true)
    expect(dllAutoUpdatePending('1.9.0', release(true), 4, published + 4 * day)).toBe(false)
    expect(dllAutoUpdatePending('1.1.0', release(true), 1, published)).toBe(false)
    expect(dllAutoUpdatePending('1.9.0', release(false), 1, published)).toBe(false)
  })
})

describe('dllAtLeast', () => {
  it('compara parte a parte, sem versão ou inválida não passa', () => {
    expect(dllAtLeast('1.7.0', [1, 7, 0])).toBe(true)
    expect(dllAtLeast('1.7.1', [1, 7, 0])).toBe(true)
    expect(dllAtLeast('1.10.0', [1, 7, 0])).toBe(true)
    expect(dllAtLeast('1.6.9', [1, 7, 0])).toBe(false)
    expect(dllAtLeast('1.7', [1, 7, 0])).toBe(true)
    expect(dllAtLeast(null, [1, 7, 0])).toBe(false)
    expect(dllAtLeast('x', [1, 7, 0])).toBe(false)
  })
})
