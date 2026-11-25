import { describe, expect, it } from 'vitest'
import { dllDownloadUrl, dllAtLeast, dllReleaseNoticeEnabled, dllUpdatesItself, type DllRelease } from './useDll'

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

describe('dllReleaseNoticeEnabled', () => {
  it('quiets only the manual 1.52.2 release', () => {
    expect(dllReleaseNoticeEnabled({ ...release(false), version: '1.52.2' })).toBe(false)
    expect(dllReleaseNoticeEnabled(null)).toBe(false)
    expect(dllReleaseNoticeEnabled(undefined)).toBe(false)
  })

  it('restores normal notices for signed 1.52.2 and every other release', () => {
    expect(dllReleaseNoticeEnabled({ ...release(true), version: '1.52.2' })).toBe(true)
    for (const version of ['1.52.1', '1.52.3', '1.53.0']) {
      expect(dllReleaseNoticeEnabled({ ...release(false), version })).toBe(true)
      expect(dllReleaseNoticeEnabled({ ...release(true), version })).toBe(true)
    }
    expect(dllUpdatesItself('1.52.1', { ...release(true), version: '1.52.2' })).toBe(true)
    expect(dllUpdatesItself('1.1.0', { ...release(true), version: '1.52.2' })).toBe(false)
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


describe('dllDownloadUrl', () => {
  it('serves the signed 1.52.3 package from the frontend', () => {
    expect(dllDownloadUrl({ ...release(true), version: '1.52.3' })).toBe('/downloads/CerberusBags-1.52.3.zip')
  })

  it('uses the backend for earlier and later versions', () => {
    for (const version of ['1.52.2', '1.52.4', '1.53.0']) {
      expect(dllDownloadUrl({ ...release(true), version })).toContain(`/dll/download?v=${version}`)
    }
  })

  it('keeps unsigned releases on the backend', () => {
    expect(dllDownloadUrl({ ...release(false), version: '1.52.3' })).toContain('/dll/download?v=1.52.3')
  })

  it('preserves the pre-release fallback', () => {
    expect(dllDownloadUrl(null)).toBe('/version.rar?v=1.0.0')
    expect(dllDownloadUrl(undefined)).toBe('/version.rar?v=1.0.0')
  })
})
