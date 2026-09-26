export type PartyMap = 'GERAL' | 'CAULDRON' | 'ELAN'

export const PARTY_MAP_TABS: { value: PartyMap; label: string }[] = [
  { value: 'GERAL', label: 'Geral' },
  { value: 'CAULDRON', label: 'Cauldron' },
  { value: 'ELAN', label: 'Elan' },
]

export const PARTY_MAX_MEMBERS = 8

export type PartyPendingKind = 'JOIN_REQUEST' | 'INVITE'

export interface PartyMemberSummary {
  id: number
  nickname: string
  className: string | null
  classImageUrl: string | null
  level: number
  leader: boolean
}

export interface PartySummary {
  id: number
  map: PartyMap
  leaderId: number
  members: PartyMemberSummary[]
  memberCount: number
}

export interface PartyLfgEntry {
  memberId: number
  nickname: string
  className: string | null
  classImageUrl: string | null
  level: number
  map: PartyMap
  note: string | null
  createdAt: string
}

export interface PartyPendingItem {
  id: number
  kind: PartyPendingKind
  partyId: number
  partyMap: PartyMap
  otherMember: PartyMemberSummary
  createdAt: string
}

export interface MyPartyState {
  partyId: number | null
  partyMap: PartyMap | null
  leader: boolean
  lfg: PartyLfgEntry | null
  pending: PartyPendingItem[]
}

export interface PartyBoard {
  map: PartyMap
  parties: PartySummary[]
  lfg: PartyLfgEntry[]
  my: MyPartyState
}
