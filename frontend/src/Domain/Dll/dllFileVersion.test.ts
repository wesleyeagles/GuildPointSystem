import { describe, expect, it } from 'vitest'
import { dllVersionFromBytes } from './dllFileVersion'

const utf16 = (text: string) => {
  const out = new Uint8Array(text.length * 2)
  for (let i = 0; i < text.length; i++) out[i * 2] = text.charCodeAt(i)
  return out
}

describe('dllVersionFromBytes', () => {
  it('acha o User-Agent no meio do binário', () => {
    const bytes = new Uint8Array([0x4d, 0x5a, 0, 0, ...utf16('xx CerberusBags/1.20.1\0'), 7, 7])
    expect(dllVersionFromBytes(bytes)).toBe('1.20.1')
  })

  it('null sem a marca', () => {
    expect(dllVersionFromBytes(utf16('qualquer coisa'))).toBeNull()
    expect(dllVersionFromBytes(new Uint8Array([1, 2, 3]))).toBeNull()
  })
})
