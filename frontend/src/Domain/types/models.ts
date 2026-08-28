export type Role = 'MEMBRO' | 'MODERADOR' | 'ADMINISTRADOR' | 'LIDER'
export type MemberStatus = 'PENDENTE' | 'APROVADO' | 'REJEITADO'
export type ObjectiveType = 'NORMAL' | 'LIMITADO'
export type AuctionStatus = 'OPEN' | 'DOLE' | 'TIE_BREAK' | 'CLOSED'
export type ItemType = 'WEAPON' | 'ARMOR' | 'ACCESSORY' | 'MISC'
export type PointsModality = 'AJUSTE' | 'LEILAO' | 'EVENTO' | 'REVERSAO'

export type AuditLogType =
  | 'OBJECTIVE_CREATED'
  | 'OBJECTIVE_UPDATED'
  | 'OBJECTIVE_DELETED'
  | 'EVENT_CREATED'
  | 'EVENT_CLAIMED'
  | 'EVENT_DENIED'
  | 'EVENT_CANCELLED'
  | 'POINTS_ADJUSTED'
  | 'AUCTION_CREATED'
  | 'AUCTION_CLOSED'
  | 'PROFILE_UPDATED'
  | 'MEMBER_APPROVED'
  | 'MEMBER_REJECTED'
  | 'MEMBER_REGISTERED'
  | 'MEMBER_ROLE_CHANGED'

export interface AuthResponse {
  token: string | null
  memberId: number
  nickname: string | null
  role: Role
  status: MemberStatus
  profileComplete: boolean
}

export interface Member {
  id: number
  email: string
  nickname: string
  avatarUrl: string | null
  raceId: number
  raceName: string
  classId: number
  className: string
  classImageUrl: string | null
  role: Role
  status: MemberStatus
  points: number
  availablePoints: number
  profileComplete: boolean
}

export interface SeedOption {
  id: number
  name: string
  imageUrl?: string | null
}

export interface Objective {
  id: number
  name: string
  points: number
  type: ObjectiveType
  dailyLimit: number | null
  createdAt: string
}

export interface GuildEvent {
  id: number
  objectiveId: number
  objectiveName: string
  points: number
  durationMinutes: number
  createdAt: string
  expiresAt: string
  active: boolean
  claimedByMe: boolean
}

export interface EventClaim {
  id: number
  objectiveName: string
  points: number
  claimedAt: string
  denied: boolean
  manual: boolean
}

export interface ItemTalic {
  talicType: string
  level: number
  slot: number
}

export interface Item {
  id: number
  type: ItemType
  name: string
  rarity: string
  imageUrl: string
  description: string | null
  details: Record<string, unknown> | null
  talics: ItemTalic[]
  createdAt: string
}

export type EffectDisplayType = 'PERCENT_100' | 'FLAT' | 'BOOLEAN' | 'SEC_MILLIS'

export interface GameAccessoryEffect {
  code: number
  name: string
  displayType: EffectDisplayType
  rawValue: number | null
  displayValue: string
}

export interface GameAccessory {
  id: number
  gameCode: string
  name: string
  subtype: 'RING' | 'AMULET'
  iconId: number
  spriteSheet: string
  grade: number
  civilMask: string
  levelRequired: number
  fire: number
  water: number
  soil: number
  wind: number
  effects: GameAccessoryEffect[]
}

export interface GameArmor {
  id: number
  gameCode: string
  name: string
  slot: 'HELMET' | 'UPPER' | 'LOWER' | 'GAUNTLET' | 'SHOES'
  iconId: number
  spriteSheet: string
  spriteCols: number
  grade: number
  civilMask: string
  levelRequired: number
  defFc: number
  defFacing: number | null
  defFacingDisplay: number | null
  effects: GameAccessoryEffect[]
}

export type GameWeaponType =
  | 'KNIFE'
  | 'SWORD'
  | 'AXE'
  | 'HAMMER'
  | 'SPEAR'
  | 'BOW'
  | 'FIREARM'
  | 'LAUNCHER'
  | 'THROWING_KNIFE'
  | 'STAFF'
  | 'MINING_TOOL'
  | 'GRENADE_LAUNCHER'

export interface GameWeapon {
  id: number
  gameCode: string
  name: string
  weaponType: GameWeaponType
  iconId: number
  spriteSheet: string
  spriteCols: number
  grade: number
  civilMask: string
  levelRequired: number
  gaMinAf: number
  gaMaxAf: number
  maMinAf: number
  maMaxAf: number
  effects: GameAccessoryEffect[]
}

export interface CatalogIconRef {
  gameCode: string
  name: string
  iconId: number
  spriteSheet: string
  spriteCols?: number
}

export interface ItemSetEffect {
  code: number
  name: string
  displayType: EffectDisplayType
  rawValue: number | null
  displayValue: string
}

export interface ItemSet {
  id: number
  setCode: string
  civilMask: string | null
  head: string | null
  upper: string | null
  lower: string | null
  shoes: string | null
  gauntlet: string | null
  weapon: string | null
  shield: string | null
  amul1: string | null
  amul2: string | null
  ring1: string | null
  ring2: string | null
  cloack: string | null
  effects: ItemSetEffect[]
}

export interface EffectDefinition {
  code: number
  name: string
  displayType: EffectDisplayType
}

export interface AuctionItem {
  itemId: number
  itemName: string
  imageUrl: string
  quantity: number
}

export interface Auction {
  id: number
  status: AuctionStatus
  durationMinutes: number
  createdAt: string
  endsAt: string
  currentBid: number
  winnerId: number | null
  winnerNickname: string | null
  leaderId: number | null
  leaderNickname: string | null
  tiedCount: number
  tied: boolean
  tiedMemberIds: number[]
  tiedMemberNicknames: string[]
  items: AuctionItem[]
  tieBreakSeed: number | null
}

export interface AuctionMessage {
  id: number
  type: string
  content: string
  imageUrl: string | null
  createdAt: string
  member?: { id: number; nickname: string }
}

export interface AuditLogEntry {
  id: number
  type: AuditLogType
  actorId: number | null
  actorNickname: string | null
  targetId: number | null
  targetNickname: string | null
  payload: Record<string, unknown>
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
