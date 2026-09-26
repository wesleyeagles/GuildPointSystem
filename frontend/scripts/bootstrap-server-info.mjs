/** Quick index + untranslated bodies (dev fallback). Prefer translate-cerberus-topics.mjs */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const RAW = join(__dirname, '../src/Features/ServerInfo/data/topics.raw.json')
const OUT_DIR = join(__dirname, '../public/server-info/topics')
const INDEX_OUT = join(__dirname, '../public/server-info/index.json')

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

const raw = JSON.parse(readFileSync(RAW, 'utf8'))
mkdirSync(OUT_DIR, { recursive: true })
const index = []
for (const t of raw.topics) {
  const title = titlePtFromEn(t.title)
  writeFileSync(
    join(OUT_DIR, `${t.slug}.json`),
    JSON.stringify({ slug: t.slug, sourceUrl: t.sourceUrl, title, bodyHtml: t.bodyHtml }),
  )
  index.push({ slug: t.slug, title, sourceUrl: t.sourceUrl })
}
writeFileSync(INDEX_OUT, JSON.stringify({ bootstrappedAt: new Date().toISOString(), topics: index }, null, 2))
console.log('Bootstrap complete (English bodies, PT titles)')
