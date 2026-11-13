/**
 * Valores dos tópicos "Server Information" do Cerberus (Stage Era), já salvos em
 * Features/ServerInfo/data/topics.raw.json: buffs da Cora no tópico de classes (nível 7),
 * poções conferidas no tooltip do jogo.
 */

/** Célula numa folha de ícones do jogo (64 px por célula). */
export interface GameIcon { sheet: string; cols: number; rows: number; cell: number }

export interface Boost {
  id: string
  name: string
  /** Classe que lança o buff. */
  source?: string
  /** % de ataque */
  atk: number
  /** chance de crítico (pontos) */
  crit?: number
  /** Só soma nesse tipo de dano (Rage Range / Dark Frenzy: ataque físico; Adept: força). */
  only?: 'atk' | 'force'
  icon: GameIcon
}

/** Fórum: Talics → "Keen talic effect with upgrade changed". +7 não mudou (200%). */
export const KEEN_PCT = [0, 5, 10, 20, 45, 90, 150, 200] as const

/** Skills de classe: célula = registro+0x08 em SkillForce.edf (mesma folha da raid). */
const classIcon = (cell: number): GameIcon => ({ sheet: '/effect-icons/class.png', cols: 16, rows: 16, cell })
/** Skills (tabela 1) e forces (tabela 0) do SkillForce.edf, nas folhas da raid. */
const skillIcon = (cell: number): GameIcon => ({ sheet: '/effect-icons/skill.png', cols: 8, rows: 8, cell })
const forceIcon = (cell: number): GameIcon => ({ sheet: '/effect-icons/force.png', cols: 16, rows: 8, cell })
/** Recursos (geradores): folha resource.png, 16 por linha. */
const resourceIcon = (cell: number): GameIcon => ({ sheet: '/sprites/resource.png', cols: 16, rows: 32, cell })
/** Poções: IconIDX do catálogo (V31); Burst Premium = ipcsa02 (Cora, loja). */
const potionIcon = (cell: number): GameIcon => ({ sheet: '/sprites/potion.png', cols: 32, rows: 32, cell })

export const BUFFS: Boost[] = [
  // tooltip do jogo, nível 7 / máximo: "10-30% attack"
  { id: 'rage-range', name: 'Rage Range', source: 'Skill · corpo a corpo', atk: 30, only: 'atk', icon: skillIcon(4) },
  // "Increases damage from own melee attacks and ranged attacks" — 7lv: +25% dano, +7% dano recebido
  { id: 'dark-frenzy', name: 'Dark Frenzy', source: 'Force Dark · melee e ranger', atk: 25, only: 'atk', icon: forceIcon(1) },
  { id: 'adept', name: 'Adept', source: 'Force Dark · só força', atk: 30, only: 'force', icon: forceIcon(4) },
  { id: 'faithful-grace', name: 'Faithful Grace', source: 'Templar', atk: 40, crit: 20, icon: classIcon(123) },
  { id: 'focus', name: 'Focus', source: 'Warlock', atk: 40, crit: 20, icon: classIcon(78) },
  { id: 'quick-charge', name: 'Quick Charge', source: 'Guardian', atk: 35, crit: 20, icon: classIcon(16) },
  { id: 'precise-striking', name: 'Precise Striking', source: 'Adventurer', atk: 30, icon: classIcon(45) },
  { id: 'hawk-eye', name: 'Hawkeye', source: 'Ranger', atk: 15, icon: classIcon(222) },
  { id: 'channel', name: 'Channel', source: 'Caster', atk: 15, icon: classIcon(77) },
  { id: 'blink-illusion', name: 'Blink Illusion', source: 'Steeler', atk: 10, icon: classIcon(120) },
]

/** As duas que o pessoal usa (tooltip do jogo: "Attack N% Increase / 300sec"). */
export const ADRENAS: Boost[] = [
  { id: 'attack-25', name: 'Attack Potion 25%', atk: 25, icon: potionIcon(97) },
  { id: 'burst-premium', name: 'Burst Potion [Premium]', atk: 55, icon: potionIcon(135) },
]

/** Geradores (item de recurso). Tooltip: "Increases the level of attack and defense by 20%". */
export const GENERATORS: Boost[] = [
  { id: 'atk-def-generator', name: 'Attack and Defense Generator', atk: 20, icon: resourceIcon(156) },
]
