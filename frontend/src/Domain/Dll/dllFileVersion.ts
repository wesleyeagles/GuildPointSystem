/**
 * Versão gravada dentro da version.dll: o User-Agent "CerberusBags/x.y.z" (UTF-16, build_info.hpp).
 * null se não achar (DLL antiga ou outro arquivo).
 */
export function dllVersionFromBytes(bytes: Uint8Array): string | null {
  // Lê nos dois alinhamentos (o texto quase sempre começa em byte par, mas não custa conferir o ímpar).
  for (const start of [0, 1]) {
    const part = bytes.subarray(start, start + ((bytes.length - start) & ~1))
    const match = /CerberusBags\/(\d{1,5}(?:\.\d{1,5}){1,3})/.exec(new TextDecoder('utf-16le').decode(part))
    if (match) return match[1]
  }
  return null
}

export async function dllVersionFromFile(file: File): Promise<string | null> {
  try {
    return dllVersionFromBytes(new Uint8Array(await file.arrayBuffer()))
  } catch {
    return null
  }
}
