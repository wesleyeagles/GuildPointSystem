import { describe, expect, it } from 'vitest'
import { isRaidMemberOnline, nextFreeGroup, raidGroups, type Raid, type RaidMember } from './useRaids'

const member = (id: number, group: number): RaidMember => ({
  id, nickname: `M${id}`, className: null, classImageUrl: null, level: 50, group, raidLeader: id === 1, groupLeader: false,
})
const raid = (members: RaidMember[]): Raid => ({
  id: 1, name: 'Raid', leaderId: 1, leader: 'M1', createdAt: '2026-11-02T13:00:00Z', canManage: true, members,
})

describe('raidGroups', () => {
  it('groups members by subgroup in order, keeping the server order inside each one', () => {
    const groups = raidGroups(raid([member(5, 3), member(1, 1), member(2, 1), member(9, 3)]))
    expect(groups.map((g) => g.group)).toEqual([1, 3])
    expect(groups[1].members.map((m) => m.id)).toEqual([5, 9])
  })
})

describe('nextFreeGroup', () => {
  it('returns the first unused subgroup number, filling gaps', () => {
    expect(nextFreeGroup(raid([member(1, 1), member(2, 3)]))).toBe(2)
    expect(nextFreeGroup(raid([member(1, 1), member(2, 2)]))).toBe(3)
  })
})

describe('isRaidMemberOnline', () => {
  const vitals = { memberId: 1, level: 50, hp: 1, hpMax: 2, fp: 1, fpMax: 2, sp: 1, spMax: 2, ageMs: 1000 }
  it('counts the time since the message arrived on top of the server age', () => {
    expect(isRaidMemberOnline(vitals, 10_000, 13_000)).toBe(true)
    expect(isRaidMemberOnline(vitals, 10_000, 14_000)).toBe(false)
  })
  it('treats members that never reported as offline', () => {
    expect(isRaidMemberOnline({ ...vitals, ageMs: -1 }, 10_000, 10_000)).toBe(false)
    expect(isRaidMemberOnline(undefined, 10_000, 10_000)).toBe(false)
  })
})
