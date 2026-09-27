import type { DragEvent } from 'react'

export const PARTY_DRAG_MIME = 'application/x-guild-party-member'

export interface PartyDragPayload {
  memberId: number
  fromPartyId: number | null
  nickname: string
}

export function parsePartyDragPayload(data: string): PartyDragPayload | null {
  try {
    const parsed = JSON.parse(data) as PartyDragPayload
    if (
      typeof parsed.memberId !== 'number' ||
      typeof parsed.nickname !== 'string' ||
      (parsed.fromPartyId !== null && typeof parsed.fromPartyId !== 'number')
    ) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function setPartyDragData(event: DragEvent, payload: PartyDragPayload) {
  event.dataTransfer.setData(PARTY_DRAG_MIME, JSON.stringify(payload))
  event.dataTransfer.effectAllowed = 'move'
}

export function readPartyDragData(event: DragEvent): PartyDragPayload | null {
  const raw = event.dataTransfer.getData(PARTY_DRAG_MIME)
  if (!raw) return null
  return parsePartyDragPayload(raw)
}
