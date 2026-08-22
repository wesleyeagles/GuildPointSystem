/**
 * Parses AmuletItem.xlsx, rIngItem.xlsx, and SetItemEff.xlsx
 * and outputs Flyway migration SQL for V6__accessory_catalog.sql
 *
 * Usage: node backend/scripts/generate-accessory-seeds.mjs > backend/src/main/resources/db/migration/V6__accessory_catalog.sql
 */
import { readFileSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import XLSX from 'xlsx'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = join(__dirname, '..')
const xlsxDir = join(root, 'xlsx')

const EFFECT_DEFINITIONS = [
  { code: 1, name: 'SP Max', displayType: 'PERCENT_100' },
  { code: 2, name: 'FP Consumption', displayType: 'PERCENT_100' },
  { code: 3, name: 'Accuracy', displayType: 'FLAT' },
  { code: 4, name: 'Dodge', displayType: 'FLAT' },
  { code: 5, name: 'HP/FP Max', displayType: 'PERCENT_100' },
  { code: 6, name: 'All Attack Power', displayType: 'PERCENT_100' },
  { code: 7, name: 'Defense', displayType: 'PERCENT_100' },
  { code: 8, name: 'Skill Level', displayType: 'FLAT' },
  { code: 10, name: 'Detect', displayType: 'BOOLEAN' },
  { code: 12, name: 'Speed', displayType: 'FLAT' },
  { code: 14, name: 'Unknown Effect 14', displayType: 'FLAT' },
  { code: 17, name: 'Attack Damage to HP', displayType: 'PERCENT_100' },
  { code: 20, name: 'Range', displayType: 'FLAT' },
  { code: 22, name: 'Debuff Assisting Time', displayType: 'PERCENT_100' },
]

function escapeSql(str) {
  if (str == null) return null
  return String(str).replace(/'/g, "''")
}

function sqlValue(val) {
  if (val == null || val === '' || val === 0 && typeof val === 'number' && val === 0) {
    // keep 0 as valid number
  }
  if (val == null || val === '') return 'NULL'
  if (typeof val === 'number') {
    if (!Number.isFinite(val)) return 'NULL'
    return String(val)
  }
  return `'${escapeSql(val)}'`
}

function sqlNum(val) {
  if (val == null || val === '') return 'NULL'
  const n = Number(val)
  if (!Number.isFinite(n) || n === 0) return 'NULL'
  return String(n)
}

function sqlInt(val, defaultVal = 0) {
  if (val == null || val === '') return String(defaultVal)
  const n = parseInt(val, 10)
  return String(Number.isFinite(n) ? n : defaultVal)
}

function readSheet(filePath) {
  const buf = readFileSync(filePath)
  const wb = XLSX.read(buf, { type: 'buffer' })
  const sheet = wb.Sheets[wb.SheetNames[0]]
  const raw = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: null })

  let headerRowIdx = raw.findIndex((row) =>
    Array.isArray(row) && row.some((c) => c === 'IsExist')
  )
  if (headerRowIdx < 0) headerRowIdx = 1

  const headers = raw[headerRowIdx] ?? []
  const dataRows = raw.slice(headerRowIdx + 1)

  return dataRows.map((row) => {
    const obj = {}
    headers.forEach((h, i) => {
      if (h) obj[h] = row[i]
    })
    // First column is always game Code (header may be 'Code' or garbage like 'iiumc-1579')
    if (row[0] != null) obj.Code = row[0]
    return obj
  })
}

function parseAccessories(filePath, subtype) {
  const rows = readSheet(filePath)
  const inserts = []

  for (const row of rows) {
    const isExist = row.IsExist ?? row.isExist
    if (isExist !== 1 && isExist !== '1') continue

    const gameCode = row.Code ?? row.code
    const name = row.Name ?? row.name
    if (!gameCode || !name) continue

    const iconId = sqlInt(row.IconID ?? row.iconID, 0)
    const grade = sqlInt(row.ItemGrade ?? row.itemGrade, 0)
    const civil = row.Civil ?? row.civil ?? '11111000'
    const lvLim = sqlInt(row.LvLim ?? row.lvLim, 0)
    const fire = sqlInt(row.fire ?? row.Fire, 0)
    const water = sqlInt(row.water ?? row.Water, 0)
    const soil = sqlInt(row.soil ?? row.Soil, 0)
    const wind = sqlInt(row.wind ?? row.Wind, 0)

    const effCode1 = sqlNum(row.EffCode1 ?? row.effCode1)
    const effUnit1 = sqlNum(row.EffUnit1 ?? row.effUnit1)
    const effCode2 = sqlNum(row.EffCode2 ?? row.effCode2)
    const effUnit2 = sqlNum(row.EffUnit2 ?? row.effUnit2)
    const effCode3 = sqlNum(row.EffCode3 ?? row.effCode3)
    const effUnit3 = sqlNum(row.EffUnit3 ?? row.effUnit3)
    const effCode4 = sqlNum(row.EffCode4 ?? row.effCode4)
    const effUnit4 = sqlNum(row.EffUnit4 ?? row.effUnit4)

    inserts.push(
      `(${sqlValue(gameCode)}, ${sqlValue(name)}, '${subtype}', ${iconId}, '/sprites/ringseamulets.png', ${grade}, '${escapeSql(String(civil))}', ${lvLim}, ${fire}, ${water}, ${soil}, ${wind}, ${effCode1}, ${effUnit1}, ${effCode2}, ${effUnit2}, ${effCode3}, ${effUnit3}, ${effCode4}, ${effUnit4})`
    )
  }

  return inserts
}

function parseItemSets(filePath) {
  const rows = readSheet(filePath)
  const inserts = []

  for (const row of rows) {
    const setCode = row.Code ?? row.code
    if (!setCode) continue

    const civil = row.Civil ?? row.civil ?? null
    const head = row.head ?? row.Head ?? null
    const upper = row.upper ?? row.Upper ?? null
    const lower = row.lower ?? row.Lower ?? null
    const shoes = row.shoes ?? row.Shoes ?? null
    const gauntlet = row.gauntlet ?? row.Gauntlet ?? null
    const weapon = row.weapon ?? row.Weapon ?? null
    const shield = row.shield ?? row.Shield ?? null
    const amul1 = row.amul1 ?? row.Amul1 ?? null
    const amul2 = row.amul2 ?? row.Amul2 ?? null
    const ring1 = row.ring1 ?? row.Ring1 ?? null
    const ring2 = row.ring2 ?? row.Ring2 ?? null
    const cloack = row.cloack ?? row.Cloack ?? row.cloak ?? row.Cloak ?? null

  const effPairs = []
    for (let i = 1; i <= 8; i++) {
      const codeKey = `eff${i}code`
      const unitKey = `eff${i}unit`
      const altCodeKey = `eff${i}Code`
      const altUnitKey = `eff${i}Unit`
      const code = row[codeKey] ?? row[altCodeKey] ?? null
      const unit = row[unitKey] ?? row[altUnitKey] ?? null
      effPairs.push(sqlNum(code), sqlNum(unit))
    }

    inserts.push(
      `(${sqlValue(setCode)}, ${sqlValue(civil)}, ${sqlValue(head)}, ${sqlValue(upper)}, ${sqlValue(lower)}, ${sqlValue(shoes)}, ${sqlValue(gauntlet)}, ${sqlValue(weapon)}, ${sqlValue(shield)}, ${sqlValue(amul1)}, ${sqlValue(amul2)}, ${sqlValue(ring1)}, ${sqlValue(ring2)}, ${sqlValue(cloack)}, ${effPairs.join(', ')})`
    )
  }

  return inserts
}

// --- Generate SQL ---
const lines = []

lines.push('-- Accessory catalog: effect definitions, game accessories, and set item bonuses')
lines.push('')
lines.push('CREATE TABLE effect_definition (')
lines.push('    code         INT PRIMARY KEY,')
lines.push('    name         VARCHAR(100) NOT NULL,')
lines.push('    display_type VARCHAR(20)  NOT NULL DEFAULT \'PERCENT_100\',')
lines.push('    CONSTRAINT chk_effect_display_type CHECK (display_type IN (\'PERCENT_100\', \'FLAT\', \'BOOLEAN\'))')
lines.push(');')
lines.push('')
lines.push('CREATE TABLE game_accessory (')
lines.push('    id             BIGSERIAL PRIMARY KEY,')
lines.push('    game_code      VARCHAR(50)  NOT NULL UNIQUE,')
lines.push('    name           VARCHAR(200) NOT NULL,')
lines.push('    subtype        VARCHAR(20)  NOT NULL,')
lines.push('    icon_id        INT          NOT NULL DEFAULT 0,')
lines.push('    sprite_sheet   VARCHAR(500) NOT NULL DEFAULT \'/sprites/ringseamulets.png\',')
lines.push('    grade          INT          NOT NULL DEFAULT 0,')
lines.push('    civil_mask     VARCHAR(20)  NOT NULL DEFAULT \'11111000\',')
lines.push('    level_required INT          NOT NULL DEFAULT 0,')
lines.push('    fire           INT          NOT NULL DEFAULT 0,')
lines.push('    water          INT          NOT NULL DEFAULT 0,')
lines.push('    soil           INT          NOT NULL DEFAULT 0,')
lines.push('    wind           INT          NOT NULL DEFAULT 0,')
lines.push('    eff_code_1     INT,')
lines.push('    eff_unit_1     NUMERIC(10,6),')
lines.push('    eff_code_2     INT,')
lines.push('    eff_unit_2     NUMERIC(10,6),')
lines.push('    eff_code_3     INT,')
lines.push('    eff_unit_3     NUMERIC(10,6),')
lines.push('    eff_code_4     INT,')
lines.push('    eff_unit_4     NUMERIC(10,6),')
lines.push('    CONSTRAINT chk_ga_subtype CHECK (subtype IN (\'RING\', \'AMULET\'))')
lines.push(');')
lines.push('')
lines.push('CREATE INDEX idx_game_accessory_subtype ON game_accessory (subtype);')
lines.push('CREATE INDEX idx_game_accessory_grade ON game_accessory (grade);')
lines.push('CREATE INDEX idx_game_accessory_name ON game_accessory (name);')
lines.push('')
lines.push('CREATE TABLE item_set (')
lines.push('    id          BIGSERIAL PRIMARY KEY,')
lines.push('    set_code    VARCHAR(50) NOT NULL,')
lines.push('    civil_mask  VARCHAR(20),')
lines.push('    head        VARCHAR(50),')
lines.push('    upper       VARCHAR(50),')
lines.push('    lower       VARCHAR(50),')
lines.push('    shoes       VARCHAR(50),')
lines.push('    gauntlet    VARCHAR(50),')
lines.push('    weapon      VARCHAR(50),')
lines.push('    shield      VARCHAR(50),')
lines.push('    amul1       VARCHAR(50),')
lines.push('    amul2       VARCHAR(50),')
lines.push('    ring1       VARCHAR(50),')
lines.push('    ring2       VARCHAR(50),')
lines.push('    cloack      VARCHAR(50),')
lines.push('    eff1_code   INT,')
lines.push('    eff1_unit   NUMERIC(10,6),')
lines.push('    eff2_code   INT,')
lines.push('    eff2_unit   NUMERIC(10,6),')
lines.push('    eff3_code   INT,')
lines.push('    eff3_unit   NUMERIC(10,6),')
lines.push('    eff4_code   INT,')
lines.push('    eff4_unit   NUMERIC(10,6),')
lines.push('    eff5_code   INT,')
lines.push('    eff5_unit   NUMERIC(10,6),')
lines.push('    eff6_code   INT,')
lines.push('    eff6_unit   NUMERIC(10,6),')
lines.push('    eff7_code   INT,')
lines.push('    eff7_unit   NUMERIC(10,6),')
lines.push('    eff8_code   INT,')
lines.push('    eff8_unit   NUMERIC(10,6)')
lines.push(');')
lines.push('')
lines.push('CREATE INDEX idx_item_set_code ON item_set (set_code);')
lines.push('')

// Effect definitions seed
lines.push('INSERT INTO effect_definition (code, name, display_type) VALUES')
const effectRows = EFFECT_DEFINITIONS.map(
  (e) => `    (${e.code}, '${escapeSql(e.name)}', '${e.displayType}')`
)
lines.push(effectRows.join(',\n') + ';')
lines.push('')

// Accessories
const amuletInserts = parseAccessories(join(xlsxDir, 'AmuletItem.xlsx'), 'AMULET')
const ringInserts = parseAccessories(join(xlsxDir, 'rIngItem.xlsx'), 'RING')
const allAccessoryInserts = [...amuletInserts, ...ringInserts]

if (allAccessoryInserts.length > 0) {
  lines.push('INSERT INTO game_accessory (game_code, name, subtype, icon_id, sprite_sheet, grade, civil_mask, level_required, fire, water, soil, wind, eff_code_1, eff_unit_1, eff_code_2, eff_unit_2, eff_code_3, eff_unit_3, eff_code_4, eff_unit_4) VALUES')
  lines.push(allAccessoryInserts.join(',\n') + ';')
  lines.push('')
}

// Item sets
const setInserts = parseItemSets(join(xlsxDir, 'SetItemEff.xlsx'))
if (setInserts.length > 0) {
  lines.push('INSERT INTO item_set (set_code, civil_mask, head, upper, lower, shoes, gauntlet, weapon, shield, amul1, amul2, ring1, ring2, cloack, eff1_code, eff1_unit, eff2_code, eff2_unit, eff3_code, eff3_unit, eff4_code, eff4_unit, eff5_code, eff5_unit, eff6_code, eff6_unit, eff7_code, eff7_unit, eff8_code, eff8_unit) VALUES')
  lines.push(setInserts.join(',\n') + ';')
}

console.log(lines.join('\n'))
console.error(`\n--- Stats: ${amuletInserts.length} amulets, ${ringInserts.length} rings, ${setInserts.length} sets ---`)
