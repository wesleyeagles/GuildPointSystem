/**
 * Translates scraped forum HTML to Portuguese (leaf text elements only).
 * Run: node scripts/translate-cerberus-topics.mjs
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync, unlinkSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import * as cheerio from 'cheerio'
import translate from 'google-translate-api-x'

const __dirname = dirname(fileURLToPath(import.meta.url))
const RAW = join(__dirname, '../src/Features/ServerInfo/data/topics.raw.json')
const OUT_DIR = join(__dirname, '../public/server-info/topics')
const INDEX_OUT = join(__dirname, '../public/server-info/index.json')
const CACHE = join(__dirname, '../src/Features/ServerInfo/data/translate-cache.json')

/** Mega-threads (índice geral) — mantém HTML original em inglês; títulos já vêm em PT. */
const SKIP_BODY_TRANSLATE = new Set([
  'server-information-general-information.32501',
  'server-information-classes-30-and-40-level.32445',
  'alpha-server-information-class-balance-changes.32426',
])

const LEAF_SELECTORS =
  'p, li, td, th, h1, h2, h3, h4, h5, h6, b, strong, i, em, u, span, div.bbCodeBlock-title, button.bbCodeSpoiler-button'

const PROTECT = [
  'Quests',
  'Quest',
  'Accretia',
  'Bellato',
  'Cora',
  'MAU',
  'Chip War',
  'Web-Auction',
  'Guild Points',
  'Guild Store',
  'Guild skills',
  'Premium Service',
  'Cash shop',
  'Hero Path',
  'Paragons',
  'Cartella',
  'Adventurer',
  'ExitLag',
  'CBT',
  'PvP',
  'RF Online',
  'FORCE & STEEL',
  'CERBERUS GAMES Inc',
  'Desert of Hunters',
  'Charshop',
  'Relics',
  'Rifts',
  'Dungeon Turrets',
  'Server Box',
  'Race Leader',
  'New Locations',
  'Gathering Resources',
  'Dodge',
  'Accuracy',
  'PrecisionAccuracy',
  'Level Drop System',
  'Catching-up-system',
  'Class Balance',
  'Solo BD',
]

function protectText(text) {
  let out = text
  const tokens = []
  PROTECT.forEach((term, i) => {
    const token = `⟦P${i}⟧`
    const re = new RegExp(`\\b${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi')
    if (re.test(out)) {
      tokens.push({ token, term })
      out = out.replace(re, token)
    }
  })
  return { text: out, tokens }
}

function restoreText(text, tokens) {
  let out = text
  for (const { token, term } of tokens) {
    out = out.split(token).join(term)
  }
  return out
}

function titlePtFromEn(title) {
  return title
    .replace(/^Важно\s*-\s*/i, '')
    .replace(/^\[Alpha Server information\]\s*/i, '[Informações do servidor Alpha] ')
    .replace(/^\[Server [Ii]nformation\]\s*/i, '[Informações do servidor] ')
    .replace(/^\[Server information\]\s*/i, '[Informações do servidor] ')
    .replace(/General information/i, 'Informações gerais')
    .replace(/All New Quests/i, 'Todas as novas Quests')
    .replace(/Solo BD quests/i, 'Solo BD Quests')
    .replace(/Relics \/ Relics Upgrade/i, 'Relics / Upgrade de Relics')
    .replace(
      /Guild Progression System \(Guild Points, Guild Store, Guild skills\)/i,
      'Sistema de progressão da Guild (Guild Points, Guild Store, Guild skills)',
    )
    .replace(/GATHERING RESOURCES \(Flower, Cactus, Ore\)/i, 'COLETA DE RECURSOS (Flower, Cactus, Ore)')
    .replace(/Classes 30 and 40 Level/i, 'Classes nível 30 e 40')
    .replace(/Chip War and Ore/i, 'Chip War e Ore')
    .replace(/Race Leader Voting system/i, 'Sistema de votação do líder da raça')
    .replace(/New Locations/i, 'Novos locais')
    .replace(/MAU Changes/i, 'Alterações de MAU')
    .replace(/PvP Point Gain System/i, 'Sistema de ganho de pontos PvP')
    .replace(/Class Balance Changes/i, 'Alterações de balanceamento de classes')
}

let cache = {}
if (existsSync(CACHE)) unlinkSync(CACHE)

async function translatePlain(text) {
  const trimmed = text.trim()
  if (!trimmed) return text
  if (cache[trimmed]) {
    const leading = text.match(/^\s*/)?.[0] ?? ''
    const trailing = text.match(/\s*$/)?.[0] ?? ''
    return leading + cache[trimmed] + trailing
  }

  const { text: protectedText, tokens } = protectText(trimmed)
  const res = await translate(protectedText, { from: 'en', to: 'pt' })
  const translated = restoreText(res.text, tokens)
  cache[trimmed] = translated
  const leading = text.match(/^\s*/)?.[0] ?? ''
  const trailing = text.match(/\s*$/)?.[0] ?? ''
  return leading + translated + trailing
}

async function translateHtml(html) {
  const $ = cheerio.load(`<div id="wrap">${html}</div>`, { decodeEntities: false }, false)
  const elements = $(LEAF_SELECTORS).toArray()

  for (const el of elements) {
    const $el = $(el)
    if ($el.closest('.bbImageWrapper, img, script, style, nav.rf-stage-nav').length) continue
    if ($el.find('img, table, ul, ol, .bbImageWrapper').length) continue
    const text = $el.text()
    if (!text.trim()) continue
    const translated = await translatePlain(text)
    $el.text(translated)
    await new Promise((r) => setTimeout(r, 60))
  }

  return $('#wrap').html() ?? ''
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const raw = JSON.parse(readFileSync(RAW, 'utf8'))
  const index = []

  for (let i = 0; i < raw.topics.length; i++) {
    const t = raw.topics[i]
    console.log(`[${i + 1}/${raw.topics.length}] ${t.slug}`)
    const titlePt = titlePtFromEn(t.title)
    const bodyHtmlPt = SKIP_BODY_TRANSLATE.has(t.slug)
      ? t.bodyHtml
      : await translateHtml(t.bodyHtml)
    writeFileSync(
      join(OUT_DIR, `${t.slug}.json`),
      JSON.stringify({ slug: t.slug, sourceUrl: t.sourceUrl, title: titlePt, bodyHtml: bodyHtmlPt }),
      'utf8',
    )
    index.push({ slug: t.slug, title: titlePt, sourceUrl: t.sourceUrl })
    writeFileSync(CACHE, JSON.stringify(cache), 'utf8')
  }

  writeFileSync(
    INDEX_OUT,
    JSON.stringify({ translatedAt: new Date().toISOString(), topics: index }, null, 2),
    'utf8',
  )
  console.log(`Wrote ${index.length} topics to ${OUT_DIR}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
