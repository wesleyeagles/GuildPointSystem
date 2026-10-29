// Tabela de combinações das quests: N emblemas + N cristais (+ item extra opcional) = recompensa.
export type EmblemTier = 'green' | 'yellow' | 'red'
export type RewardSection = 'consumables' | 'bd' | 'weapons' | 'armor' | 'special'
export type RewardTag = 'daily' | 'once' | 'personal' | 'temporary' | 'choose' | 'random' | 'cheap' | 'talic5' | 'locked'

export interface RewardOption { label?: string; emblems: number; crystals: number }
export interface QuestReward {
  id: string
  name: string
  section: RewardSection
  /** Item que também entra na combinação. */
  extra?: string
  options: RewardOption[]
  tags: RewardTag[]
  note?: string
  /** Custo em gold da combinação. */
  price?: string
}

export const tierLabels: Record<EmblemTier, string> = { green: 'Emblema Verde', yellow: 'Emblema Amarelo', red: 'Emblema Vermelho' }
export const sectionLabels: Record<RewardSection, string> = {
  consumables: 'Consumíveis', bd: 'Battle Dungeon', weapons: 'Armas', armor: 'Armaduras', special: 'Especiais',
}
export const tagLabels: Record<RewardTag, string> = {
  daily: '1x por dia de quest', once: 'Apenas 1x', personal: 'Pessoal', temporary: 'Temporário', choose: 'Escolha a recompensa',
  random: 'Item aleatório', cheap: 'Combinação barata', talic5: '5 slots de talic', locked: 'Não combina de novo',
}

const qty = (rows: [string, number, number][]): RewardOption[] => rows.map(([label, emblems, crystals]) => ({ label, emblems, crystals }))
const classes = (emblems: number, crystals: [number, number, number]): RewardOption[] =>
  (['Warrior', 'Ranger', 'Mage/LC'] as const).map((label, i) => ({ label, emblems, crystals: crystals[i] }))
const one = (emblems: number, crystals: number): RewardOption[] => [{ emblems, crystals }]

const lockedGear: RewardTag[] = ['talic5', 'choose', 'personal', 'locked']

export const questRewards: Record<EmblemTier, QuestReward[]> = {
  green: [
    { id: 'g-chocolate', name: 'x10 Chocolate (33% EXP)', section: 'consumables', options: one(2, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'g-chaos-potion', name: 'x2 Chaos Potion', section: 'consumables', options: one(1, 2), tags: ['daily', 'personal', 'temporary'] },
    { id: 'g-exp-potion', name: 'EXP Potion 1%', section: 'consumables', options: one(8, 3), tags: ['daily', 'personal', 'temporary'] },
    { id: 'g-attack-potion', name: 'Attack Potion 25%', section: 'consumables', options: qty([['x8', 2, 4], ['x24', 6, 4], ['x72', 18, 4]]), tags: ['personal', 'cheap'] },
    { id: 'g-scanner', name: 'Quantum Scanner', section: 'consumables', options: qty([['x4', 1, 5], ['x12', 3, 5], ['x36', 9, 5]]), tags: [],
      note: 'Escaneia personagens aliados e inimigos, inclusive PB. Cooldown de 1 min.' },
    { id: 'g-elemental-talic', name: 'Elemental Talic', section: 'consumables', options: one(4, 6), tags: ['choose', 'cheap'] },
    { id: 'g-chaos-talic', name: 'Chaos Talic', section: 'consumables', options: one(5, 6), tags: ['cheap'] },
    { id: 'g-booster', name: 'Lv 40 Booster Blueprint', section: 'consumables', options: one(3, 9), tags: [] },
    { id: 'g-bd-quest', name: 'BD 41-48 Reward Quest', section: 'bd', options: one(8, 8), tags: ['daily', 'personal', 'temporary'], price: '500k' },
    { id: 'g-bd-reward-shard', name: 'BD Reward Shard (40+)', section: 'bd', options: qty([['x1', 2, 7], ['x3', 6, 7], ['x9', 18, 7]]), tags: ['personal', 'cheap'] },
    { id: 'g-bd-shard', name: 'BD 36-45 Shard', section: 'bd', options: qty([['x2', 3, 8], ['x6', 9, 8], ['x12', 18, 8]]), tags: ['cheap'], note: '8 shards = 1 BD Key.' },
    { id: 'g-weapon-40', name: '40 Weapon type-B', section: 'weapons', options: one(12, 10), tags: lockedGear },
    { id: 'g-weapon-40-up', name: '40 Weapon type-B', section: 'weapons', extra: '40 Weapon type-B', options: one(8, 10), tags: lockedGear },
    { id: 'g-armor-41', name: '41 Armor type-B', section: 'armor', options: classes(6, [12, 13, 14]), tags: lockedGear },
    { id: 'g-armor-43-up', name: '43 Armor type-B', section: 'armor', extra: '43 Armor type-B', options: classes(8, [15, 16, 17]), tags: lockedGear },
    { id: 'g-armor-43', name: '43 Armor type-B', section: 'armor', options: classes(11, [15, 16, 17]), tags: ['talic5', 'random'] },
    { id: 'g-armor-c-40', name: '40(43) Armor type-C Defense', section: 'armor', options: one(9, 15), tags: ['talic5', 'choose', 'once', 'personal'] },
    { id: 'g-armor-45', name: '45 Armor type-B', section: 'armor', options: classes(22, [18, 19, 20]), tags: ['talic5', 'random'] },
    { id: 'g-armor-c-42', name: '42(45) Armor type-C Defense', section: 'armor', options: one(33, 18), tags: ['talic5', 'choose', 'once', 'personal'] },
  ],
  yellow: [
    { id: 'y-potions', name: 'Potions (12% HP · 8 crit)', section: 'consumables', options: qty([['x5', 2, 2], ['x15', 6, 2], ['x45', 18, 2]]), tags: ['choose', 'personal', 'cheap'] },
    { id: 'y-ether', name: 'Ether Scroll', section: 'consumables', options: one(3, 5), tags: ['personal', 'cheap'] },
    { id: 'y-rare-ore', name: 'x67 Rare Ore', section: 'consumables', options: one(2, 5), tags: ['cheap'] },
    { id: 'y-hp-3000', name: 'Unlimited +3000 HP Potion', section: 'special', options: one(30, 4), tags: ['once', 'personal', 'temporary'], note: 'Temporário por 7 dias.', price: '30kk' },
    { id: 'y-relic-box', name: 'x2 Random 1GR Relic Box', section: 'special', options: one(8, 7), tags: ['once', 'personal'] },
    { id: 'y-relic-box-alt', name: 'x2 Random 1GR Relic Box', section: 'special', options: one(26, 7), tags: ['once', 'personal'], price: '4kk' },
    { id: 'y-bd-quest', name: 'BD 46-50 Reward Quest', section: 'bd', options: one(8, 10), tags: ['once', 'personal', 'temporary'] },
    { id: 'y-bd-reward-shard', name: 'BD Reward Shard (40+)', section: 'bd', options: qty([['x3', 2, 9], ['x9', 6, 9], ['x27', 18, 9]]), tags: ['personal', 'cheap'] },
    { id: 'y-bd-shard', name: 'BD 41-48 Shard', section: 'bd', options: qty([['x1', 2, 10], ['x3', 6, 10], ['x9', 18, 10]]), tags: ['cheap'], note: '8 shards = 1 BD Key.' },
    { id: 'y-weapon-45', name: '45 Weapon type-B', section: 'weapons', options: one(24, 11), tags: lockedGear },
    { id: 'y-weapon-45-up', name: '45 Weapon type-B', section: 'weapons', extra: '45 Weapon type-B', options: one(12, 11), tags: lockedGear },
    { id: 'y-armor-45-up', name: '45 Armor type-B', section: 'armor', extra: '45 Armor type-B', options: classes(4, [16, 17, 18]), tags: lockedGear },
    { id: 'y-armor-45', name: '45 Armor type-B', section: 'armor', options: classes(7, [16, 17, 18]), tags: ['talic5', 'random'] },
    { id: 'y-armor-47-up', name: '47 Armor type-B', section: 'armor', extra: '47 Armor type-B', options: classes(11, [19, 20, 21]), tags: lockedGear },
    { id: 'y-armor-47', name: '47 Armor type-B', section: 'armor', options: classes(16, [19, 20, 21]), tags: ['talic5', 'random'] },
    { id: 'y-armor-c-44', name: '44(47) Armor type-C Defense', section: 'armor', options: one(13, 15), tags: ['talic5', 'choose', 'once', 'personal'] },
  ],
  red: [
    { id: 'r-pvp-potion', name: '300 PVP Potion', section: 'consumables', extra: '250 PVP Potion (quest do cash shop)', options: one(1, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'r-attack-potion', name: 'x20 Attack Potion 50%', section: 'consumables', options: one(3, 2), tags: ['once', 'personal'] },
    { id: 'r-gold-points', name: '180 Gold Points', section: 'special', options: one(5, 4), tags: ['once', 'personal'] },
    { id: 'r-t4-envelope', name: 'T4 Envelope', section: 'special', options: one(3, 5), tags: [] },
    { id: 'r-t5-stone', name: 'T5 Stone', section: 'special', extra: 'T5 Stone', options: one(1, 6), tags: ['choose'] },
    { id: 'r-hp-4000', name: 'Unlimited +4000 HP Potion', section: 'special', options: one(22, 3), tags: ['once', 'personal', 'temporary'], note: 'Temporário por 7 dias.', price: '50kk' },
    { id: 'r-relic-box', name: 'x2 Random 2GR Relic Box', section: 'special', options: one(1, 10), tags: ['once', 'personal'] },
    { id: 'r-citadel-shard', name: 'x2 BD Small Citadel Shard', section: 'bd', options: one(1, 8), tags: ['once'], note: '8 shards = 1 BD Key.' },
    { id: 'r-citadel-quest', name: 'BD Small Citadel Reward Quest', section: 'bd', options: one(1, 9), tags: ['once', 'personal', 'temporary'] },
    { id: 'r-bd-reward-shard', name: 'BD Reward Shard (50+)', section: 'bd', options: qty([['x1', 1, 7], ['x3', 3, 7], ['x9', 9, 7]]), tags: ['personal'] },
    { id: 'r-promocode', name: 'Promocode 47(52) Weapon type-C Defense + 70 emblemas vermelhos', section: 'weapons', options: one(73, 10), tags: ['talic5', 'choose', 'once'],
      note: 'Limitado a 14 promocodes: só os 14 primeiros jogadores a cumprir os requisitos recebem.' },
    { id: 'r-weapon-50', name: '50 Weapon type-B', section: 'weapons', extra: '50 Weapon type-B', options: one(20, 11), tags: [...lockedGear, 'once'] },
    { id: 'r-armor-50', name: '50 Armor type-B', section: 'armor', extra: '50 Armor type-B', options: one(3, 11), tags: [...lockedGear, 'once'] },
    { id: 'r-palmas', name: '50 Palmas Armor', section: 'armor', extra: 'Lv.50 Palmas Armor', options: one(7, 12), tags: [...lockedGear, 'once'] },
    { id: 'r-palmas-plus', name: '50 Palmas Armor + 20 emblemas vermelhos', section: 'armor', extra: 'Lv.50 Palmas Armor', options: one(45, 12), tags: [...lockedGear, 'once'] },
    { id: 'r-emitter', name: 'Emitter Omega Epsilon Omicron B (1GR → 2GR) + 30 emblemas vermelhos', section: 'special', options: one(36, 13), tags: ['once', 'personal'],
      note: 'Ao combinar com joia, a joia também vira pessoal.' },
    { id: 'r-jewelry', name: 'Advanced MiddleClass Jewelry (10% ataque + 5% HP)', section: 'special', extra: 'MiddleClass Jewelry', options: one(12, 14), tags: ['once', 'personal', 'temporary'],
      note: 'Escolha anel ou amuleto. Temporário por 7 dias.', price: '5kk' },
  ],
}
