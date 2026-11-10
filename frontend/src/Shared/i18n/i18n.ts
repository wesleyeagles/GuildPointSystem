import { useSyncExternalStore } from 'react'
import { EN_EXACT, EN_PATTERNS } from './translations.en'

/**
 * Site language. Portuguese is the source: every string in the code is written in PT-BR.
 * English is applied at the DOM level: text nodes and a few attributes are looked up in
 * a PT→EN dictionary as React renders them, so no component needs a t() call. Anything
 * missing from the dictionary simply stays in Portuguese.
 */
export type Language = 'pt' | 'en'

const STORAGE_KEY = 'guild.language'
const ATTRIBUTES = ['title', 'placeholder', 'aria-label', 'alt'] as const
const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'TEXTAREA', 'INPUT', 'NOSCRIPT', 'CODE', 'PRE'])
/** User-written content (editors, chat) and anything explicitly opted out. */
const SKIP_SELECTOR = '[data-no-i18n], [contenteditable="true"], .ProseMirror'

function readStored(): Language {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'en' ? 'en' : 'pt'
  } catch {
    return 'pt'
  }
}

let language: Language = readStored()
const listeners = new Set<() => void>()

export function getLanguage(): Language {
  return language
}

export function setLanguage(next: Language) {
  if (next === language) return
  language = next
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    /* private mode: the choice lasts until reload */
  }
  applyLanguage()
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function useLanguage(): Language {
  return useSyncExternalStore(subscribe, getLanguage, getLanguage)
}

/** Locale for Intl / toLocale* calls that should follow the chosen language. */
export function getLocale(): string {
  return language === 'en' ? 'en-US' : 'pt-BR'
}

// ─── Dictionary lookup ──────────────────────────────────────────────────────

interface CompiledPattern {
  regex: RegExp
  /** Longest literal piece: a cheap includes() check before running the regex. */
  hint: string
  output: string
  literalLength: number
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const PATTERNS: CompiledPattern[] = Object.entries(EN_PATTERNS).map(([source, output]) => {
  const pieces = source.split(/\{\d+\}/)
  const body = source
    .split(/(\{\d+\})/)
    .map((piece) => (/^\{\d+\}$/.test(piece) ? `(?<p${piece.slice(1, -1)}>.*?)` : escapeRegex(piece)))
    .join('')
  const hint = pieces.reduce((longest, piece) => (piece.trim().length > longest.length ? piece.trim() : longest), '')
  const literalLength = pieces.join('').length
  return { regex: new RegExp(`^${body}$`, 's'), hint, output, literalLength }
})
  // Most specific first: "{0} · {1} · {2} pts" must win over "{0} · {1} pts".
  .sort((a, b) => b.literalLength - a.literalLength)

const cache = new Map<string, string>()
const CACHE_LIMIT = 8000

/** Placeholders often hold translatable words themselves ("Tipo {0}" → "Tipo Arma"). */
function translateValue(value: string): string {
  return EN_EXACT[value] ?? value
}

function lookup(core: string): string | null {
  const exact = EN_EXACT[core]
  if (exact !== undefined) return exact
  for (const pattern of PATTERNS) {
    if (pattern.hint && !core.includes(pattern.hint)) continue
    const match = pattern.regex.exec(core)
    if (!match) continue
    const groups = match.groups ?? {}
    return pattern.output.replace(/\{(\d+)\}/g, (_, index: string) => translateValue(groups[`p${index}`] ?? ''))
  }
  return null
}

/** PT→EN for one string; leading/trailing whitespace is kept as is. */
export function translateText(text: string): string {
  if (language !== 'en') return text
  const cached = cache.get(text)
  if (cached !== undefined) return cached
  let result = text
  const core = text.replace(/\s+/g, ' ').trim()
  if (core && /[A-Za-zÀ-ú]/.test(core)) {
    const translated = lookup(core)
    if (translated !== null) {
      const lead = text.match(/^\s*/)?.[0] ?? ''
      const trail = text.match(/\s*$/)?.[0] ?? ''
      result = lead + translated + trail
    }
  }
  if (cache.size > CACHE_LIMIT) cache.clear()
  cache.set(text, result)
  return result
}

/** For strings that never reach the DOM (Notification titles and the like). */
export const tr = translateText

// ─── DOM translator ─────────────────────────────────────────────────────────

/** What we wrote, and the Portuguese it replaced, per node / attribute. */
const textRecords = new WeakMap<Text, { source: string; output: string }>()
const attrRecords = new WeakMap<Element, Map<string, { source: string; output: string }>>()
let observer: MutationObserver | null = null

function isSkipped(element: Element | null): boolean {
  if (!element) return true
  if (SKIP_TAGS.has(element.tagName)) return true
  return element.closest(SKIP_SELECTOR) !== null
}

function processTextNode(node: Text) {
  const value = node.nodeValue ?? ''
  const record = textRecords.get(node)
  if (record && record.output === value) return // our own write
  if (isSkipped(node.parentElement)) return
  const output = translateText(value)
  if (output === value) {
    textRecords.delete(node)
    return
  }
  textRecords.set(node, { source: value, output })
  node.nodeValue = output
}

function processAttribute(element: Element, name: string) {
  const value = element.getAttribute(name)
  if (value === null) return
  let records = attrRecords.get(element)
  const record = records?.get(name)
  if (record && record.output === value) return
  const output = translateText(value)
  if (output === value) {
    records?.delete(name)
    return
  }
  if (!records) {
    records = new Map()
    attrRecords.set(element, records)
  }
  records.set(name, { source: value, output })
  element.setAttribute(name, output)
}

/** Attributes: inputs keep their placeholder/title translated; only opted-out areas are skipped. */
function isAttributeSkipped(element: Element): boolean {
  return element.closest(SKIP_SELECTOR) !== null
}

function processElement(element: Element) {
  if (isAttributeSkipped(element)) return
  for (const name of ATTRIBUTES) {
    if (element.hasAttribute(name)) processAttribute(element, name)
  }
}

function processTree(root: Node) {
  if (root.nodeType === Node.TEXT_NODE) {
    processTextNode(root as Text)
    return
  }
  if (root.nodeType !== Node.ELEMENT_NODE && root.nodeType !== Node.DOCUMENT_FRAGMENT_NODE) return
  if (root.nodeType === Node.ELEMENT_NODE) {
    const element = root as Element
    processElement(element)
    if (isSkipped(element)) return
  }
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
    // Text inside skipped tags is left alone, but their attributes (placeholder) still count.
    acceptNode: (node) =>
      node.nodeType === Node.ELEMENT_NODE && isAttributeSkipped(node as Element)
        ? NodeFilter.FILTER_REJECT
        : NodeFilter.FILTER_ACCEPT,
  })
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (node.nodeType === Node.TEXT_NODE) processTextNode(node as Text)
    else processElement(node as Element)
  }
}

/** Back to Portuguese: put every original string back where we changed it. */
function restoreTree(root: Node) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT)
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (node.nodeType === Node.TEXT_NODE) {
      const record = textRecords.get(node as Text)
      if (record && node.nodeValue === record.output) node.nodeValue = record.source
      textRecords.delete(node as Text)
    } else {
      const element = node as Element
      const records = attrRecords.get(element)
      if (!records) continue
      records.forEach((record, name) => {
        if (element.getAttribute(name) === record.output) element.setAttribute(name, record.source)
      })
      attrRecords.delete(element)
    }
  }
}

function handleMutations(mutations: MutationRecord[]) {
  for (const mutation of mutations) {
    if (mutation.type === 'characterData') {
      processTextNode(mutation.target as Text)
    } else if (mutation.type === 'attributes') {
      const element = mutation.target as Element
      if (mutation.attributeName && !isAttributeSkipped(element)) processAttribute(element, mutation.attributeName)
    } else {
      mutation.addedNodes.forEach(processTree)
    }
  }
}

let originalTitle: string | null = null

function applyLanguage() {
  if (typeof document === 'undefined') return
  document.documentElement.lang = language === 'en' ? 'en' : 'pt-BR'
  cache.clear()
  if (language === 'en') {
    if (!observer) {
      observer = new MutationObserver(handleMutations)
      observer.observe(document.body, {
        subtree: true,
        childList: true,
        characterData: true,
        attributes: true,
        attributeFilter: [...ATTRIBUTES],
      })
    }
    processTree(document.body)
    originalTitle = document.title
    document.title = translateText(document.title)
  } else {
    observer?.disconnect()
    observer = null
    restoreTree(document.body)
    if (originalTitle !== null) document.title = originalTitle
  }
}

/** Called once at startup, before React renders. */
export function startTranslator() {
  if (typeof document === 'undefined') return
  if (language === 'en') applyLanguage()
}
