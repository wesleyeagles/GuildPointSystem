/**
 * Parses AmuletItem.xlsx, rIngItem.xlsx, and SetItemEff.xlsx
 * and outputs Flyway migration SQL for V6__accessory_catalog.sql
 *
 * Usage: node backend/scripts/generate-accessory-seeds.mjs > backend/src/main/resources/db/migration/V6__accessory_catalog.sql
 */
import { readFileSync, writeFileSync, unlinkSync } from 'fs'
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
  { code: 9, name: 'Subtlety', displayType: 'BOOLEAN' },
  { code: 10, name: 'Detect', displayType: 'BOOLEAN' },
  { code: 11, name: 'Remove Protective Ability', displayType: 'BOOLEAN' },
  { code: 12, name: 'Speed', displayType: 'FLAT' },
  { code: 13, name: 'Disclose Vulnerability', displayType: 'BOOLEAN' },
  { code: 14, name: 'OP Regen', displayType: 'FLAT' },
  { code: 15, name: 'Magic Attack Power', displayType: 'PERCENT_100' },
  { code: 16, name: 'Max FP', displayType: 'PERCENT_100' },
  { code: 18, name: 'Attack Damage to FP', displayType: 'PERCENT_100' },
  { code: 19, name: 'Critical Rate', displayType: 'PERCENT_100' },
  { code: 21, name: 'Mage Protection', displayType: 'PERCENT_100' },
  { code: 23, name: 'SP Regen', displayType: 'FLAT' },
  { code: 24, name: 'Dodge Rate', displayType: 'PERCENT_100' },
  { code: 25, name: 'Attack Delay of Launcher', displayType: 'SEC_MILLIS' },
  { code: 26, name: 'Force range', displayType: 'FLAT' },
  { code: 27, name: 'Receive Critical Rate', displayType: 'PERCENT_100' },
  { code: 28, name: 'Shield Block', displayType: 'PERCENT_100' },
  { code: 29, name: 'All Resistance', displayType: 'FLAT' },
  { code: 30, name: 'HP Max', displayType: 'PERCENT_100' },
  { code: 31, name: 'Negative Force Duration', displayType: 'PERCENT_100' },
  { code: 32, name: 'Ignore Block Chance', displayType: 'PERCENT_100' },
  { code: 33, name: 'Stealth', displayType: 'BOOLEAN' },
  { code: 34, name: 'Delay of Skill Attack', displayType: 'SEC_MILLIS' },
  { code: 35, name: 'Force Skill Delay', displayType: 'SEC_MILLIS' },
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

function parseAccessories(filePath, subtype, seenCodes) {
  const rows = readSheet(filePath)
  const inserts = []
  let skipped = 0

  for (const row of rows) {
    const isExist = row.IsExist ?? row.isExist
    if (isExist !== 1 && isExist !== '1') continue

    const gameCode = row.Code ?? row.code
    const name = row.Name ?? row.name
    if (!gameCode || !name) continue

    if (seenCodes.has(gameCode)) {
      skipped++
      continue
    }
    seenCodes.add(gameCode)

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

  if (skipped > 0) {
    console.error(`Skipped ${skipped} duplicate game_code rows in ${subtype}`)
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

function chunkArray(arr, size) {
  const chunks = []
  for (let i = 0; i < arr.length; i += size) {
    chunks.push(arr.slice(i, i + size))
  }
  return chunks
}

function buildBatchedInserts(tableName, columns, rows, batchSize = 250) {
  if (rows.length === 0) return []
  const header = `INSERT INTO ${tableName} (${columns}) VALUES`
  return chunkArray(rows, batchSize).map(
    (batch) => `${header}\n${batch.join(',\n')};`
  )
}

const ACCESSORY_COLUMNS =
  'game_code, name, subtype, icon_id, sprite_sheet, grade, civil_mask, level_required, fire, water, soil, wind, eff_code_1, eff_unit_1, eff_code_2, eff_unit_2, eff_code_3, eff_unit_3, eff_code_4, eff_unit_4'

const SET_COLUMNS =
  'set_code, civil_mask, head, upper, lower, shoes, gauntlet, weapon, shield, amul1, amul2, ring1, ring2, cloack, eff1_code, eff1_unit, eff2_code, eff2_unit, eff3_code, eff3_unit, eff4_code, eff4_unit, eff5_code, eff5_unit, eff6_code, eff6_unit, eff7_code, eff7_unit, eff8_code, eff8_unit'

// --- Generate SQL ---
const schemaLines = []

schemaLines.push('-- Accessory catalog schema + effect definitions')
schemaLines.push('')
schemaLines.push('CREATE TABLE effect_definition (')
schemaLines.push('    code         INT PRIMARY KEY,')
schemaLines.push('    name         VARCHAR(100) NOT NULL,')
schemaLines.push('    display_type VARCHAR(20)  NOT NULL DEFAULT \'PERCENT_100\',')
schemaLines.push('    CONSTRAINT chk_effect_display_type CHECK (display_type IN (\'PERCENT_100\', \'FLAT\', \'BOOLEAN\', \'SEC_MILLIS\'))')
schemaLines.push(');')
schemaLines.push('')
schemaLines.push('CREATE TABLE game_accessory (')
schemaLines.push('    id             BIGSERIAL PRIMARY KEY,')
schemaLines.push('    game_code      VARCHAR(50)  NOT NULL UNIQUE,')
schemaLines.push('    name           VARCHAR(200) NOT NULL,')
schemaLines.push('    subtype        VARCHAR(20)  NOT NULL,')
schemaLines.push('    icon_id        INT          NOT NULL DEFAULT 0,')
schemaLines.push('    sprite_sheet   VARCHAR(500) NOT NULL DEFAULT \'/sprites/ringseamulets.png\',')
schemaLines.push('    grade          INT          NOT NULL DEFAULT 0,')
schemaLines.push('    civil_mask     VARCHAR(20)  NOT NULL DEFAULT \'11111000\',')
schemaLines.push('    level_required INT          NOT NULL DEFAULT 0,')
schemaLines.push('    fire           INT          NOT NULL DEFAULT 0,')
schemaLines.push('    water          INT          NOT NULL DEFAULT 0,')
schemaLines.push('    soil           INT          NOT NULL DEFAULT 0,')
schemaLines.push('    wind           INT          NOT NULL DEFAULT 0,')
schemaLines.push('    eff_code_1     INT,')
schemaLines.push('    eff_unit_1     NUMERIC(10,6),')
schemaLines.push('    eff_code_2     INT,')
schemaLines.push('    eff_unit_2     NUMERIC(10,6),')
schemaLines.push('    eff_code_3     INT,')
schemaLines.push('    eff_unit_3     NUMERIC(10,6),')
schemaLines.push('    eff_code_4     INT,')
schemaLines.push('    eff_unit_4     NUMERIC(10,6),')
schemaLines.push('    CONSTRAINT chk_ga_subtype CHECK (subtype IN (\'RING\', \'AMULET\'))')
schemaLines.push(');')
schemaLines.push('')
schemaLines.push('CREATE INDEX idx_game_accessory_subtype ON game_accessory (subtype);')
schemaLines.push('CREATE INDEX idx_game_accessory_grade ON game_accessory (grade);')
schemaLines.push('CREATE INDEX idx_game_accessory_name ON game_accessory (name);')
schemaLines.push('')
schemaLines.push('CREATE TABLE item_set (')
schemaLines.push('    id          BIGSERIAL PRIMARY KEY,')
schemaLines.push('    set_code    VARCHAR(50) NOT NULL,')
schemaLines.push('    civil_mask  VARCHAR(20),')
schemaLines.push('    head        VARCHAR(50),')
schemaLines.push('    upper       VARCHAR(50),')
schemaLines.push('    lower       VARCHAR(50),')
schemaLines.push('    shoes       VARCHAR(50),')
schemaLines.push('    gauntlet    VARCHAR(50),')
schemaLines.push('    weapon      VARCHAR(50),')
schemaLines.push('    shield      VARCHAR(50),')
schemaLines.push('    amul1       VARCHAR(50),')
schemaLines.push('    amul2       VARCHAR(50),')
schemaLines.push('    ring1       VARCHAR(50),')
schemaLines.push('    ring2       VARCHAR(50),')
schemaLines.push('    cloack      VARCHAR(50),')
schemaLines.push('    eff1_code   INT,')
schemaLines.push('    eff1_unit   NUMERIC(10,6),')
schemaLines.push('    eff2_code   INT,')
schemaLines.push('    eff2_unit   NUMERIC(10,6),')
schemaLines.push('    eff3_code   INT,')
schemaLines.push('    eff3_unit   NUMERIC(10,6),')
schemaLines.push('    eff4_code   INT,')
schemaLines.push('    eff4_unit   NUMERIC(10,6),')
schemaLines.push('    eff5_code   INT,')
schemaLines.push('    eff5_unit   NUMERIC(10,6),')
schemaLines.push('    eff6_code   INT,')
schemaLines.push('    eff6_unit   NUMERIC(10,6),')
schemaLines.push('    eff7_code   INT,')
schemaLines.push('    eff7_unit   NUMERIC(10,6),')
schemaLines.push('    eff8_code   INT,')
schemaLines.push('    eff8_unit   NUMERIC(10,6)')
schemaLines.push(');')
schemaLines.push('')
schemaLines.push('CREATE INDEX idx_item_set_code ON item_set (set_code);')
schemaLines.push('')
schemaLines.push('INSERT INTO effect_definition (code, name, display_type) VALUES')
const effectRows = EFFECT_DEFINITIONS.map(
  (e) => `    (${e.code}, '${escapeSql(e.name)}', '${e.displayType}')`
)
schemaLines.push(effectRows.join(',\n') + ';')

const seenCodes = new Set()
const amuletInserts = parseAccessories(join(xlsxDir, 'AmuletItem.xlsx'), 'AMULET', seenCodes)
const ringInserts = parseAccessories(join(xlsxDir, 'rIngItem.xlsx'), 'RING', seenCodes)
const allAccessoryInserts = [...amuletInserts, ...ringInserts]
const setInserts = parseItemSets(join(xlsxDir, 'SetItemEff.xlsx'))

const accessoryDataLines = [
  '-- Accessory catalog: game_accessory seed data (batched inserts)',
  '',
  ...buildBatchedInserts('game_accessory', ACCESSORY_COLUMNS, allAccessoryInserts),
]

const setDataLines = [
  '-- Accessory catalog: item_set seed data',
  '',
  ...buildBatchedInserts('item_set', SET_COLUMNS, setInserts, 100),
]

const migrationDir = join(root, 'src/main/resources/db/migration')
writeFileSync(join(migrationDir, 'V6__accessory_catalog_schema.sql'), schemaLines.join('\n') + '\n')
writeFileSync(join(migrationDir, 'V7__accessory_catalog_data.sql'), accessoryDataLines.join('\n\n') + '\n')
writeFileSync(join(migrationDir, 'V8__item_set_catalog_data.sql'), setDataLines.join('\n\n') + '\n')

const legacy = join(migrationDir, 'V6__accessory_catalog.sql')
try {
  unlinkSync(legacy)
  console.log('Removed legacy V6__accessory_catalog.sql')
} catch {
  // already removed
}

console.log('Written migrations:')
console.log('  V6__accessory_catalog_schema.sql')
console.log('  V7__accessory_catalog_data.sql')
console.log('  V8__item_set_catalog_data.sql')
console.error(`\n--- Stats: ${amuletInserts.length} amulets, ${ringInserts.length} rings, ${setInserts.length} sets ---`)
