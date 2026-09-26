/**
 * One-off scraper for Cerberus forum threads (category 166).
 * Run: node scripts/scrape-cerberus-forum.mjs
 */
import { writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const BASE = 'https://cerberus-games.com'
const CATEGORY = `${BASE}/forums/server-ingame-information-eng.166/`

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = join(__dirname, '../src/Features/ServerInfo/data/topics.raw.json')

function absolutizeUrls(html) {
  return html
    .replace(/src="\/attachments\//g, `src="${BASE}/attachments/`)
    .replace(/href="\/attachments\//g, `href="${BASE}/attachments/`)
    .replace(/data-src="\/attachments\//g, `data-src="${BASE}/attachments/`)
    .replace(/src="\/data\//g, `src="${BASE}/data/`)
}

function extractFirstPost(html) {
  const marker = '<div class="bbWrapper">'
  const start = html.indexOf(marker)
  if (start === -1) return ''
  let depth = 0
  let i = start + marker.length
  while (i < html.length) {
    const open = html.indexOf('<div', i)
    const close = html.indexOf('</div>', i)
    if (close === -1) break
    if (open !== -1 && open < close) {
      depth++
      i = open + 4
      continue
    }
    if (depth === 0) {
      return html.slice(start + marker.length, close)
    }
    depth--
    i = close + 6
  }
  return ''
}

function extractTitle(html) {
  const m = html.match(/<title>([^<]+)<\/title>/i)
  if (!m) return 'Untitled'
  return m[1].replace(/\s*\|\s*CERBERUS GAMES Inc.*$/i, '').trim()
}

function slugFromPath(path) {
  const m = path.match(/\/threads\/([^/]+)\//)
  return m ? m[1] : path
}

async function listThreads() {
  const res = await fetch(CATEGORY)
  const html = await res.text()
  const paths = new Set()
  for (const m of html.matchAll(/href="(\/threads\/[^"]+\/)"/g)) {
    const p = m[1]
    if (!p.includes('/latest')) paths.add(p)
  }
  return [...paths]
}

async function main() {
  const paths = await listThreads()
  const topics = []

  for (const path of paths) {
    const url = BASE + path
    console.log('Fetching', url)
    const res = await fetch(url)
    const html = await res.text()
    const title = extractTitle(html)
    let bodyHtml = extractFirstPost(html)
    bodyHtml = absolutizeUrls(bodyHtml)
    // Drop XenForo lightbox script noise
    bodyHtml = bodyHtml.replace(/<script class="js-extraPhrases"[\s\S]*?<\/script>/gi, '')
    topics.push({
      slug: slugFromPath(path),
      sourceUrl: url,
      title,
      bodyHtml,
    })
    await new Promise((r) => setTimeout(r, 400))
  }

  writeFileSync(OUT, JSON.stringify({ scrapedAt: new Date().toISOString(), topics }, null, 2), 'utf8')
  console.log(`Wrote ${topics.length} topics to ${OUT}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
