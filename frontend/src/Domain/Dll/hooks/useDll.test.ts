import { describe, expect, it } from 'vitest'
import { dllUpdatesItself, type DllRelease } from './useDll'

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
