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
const tempWeek = 'Temporário por 7 dias.'
const bdKey = '8 shards = 1 BD Key.'

// Tabela v1.2.
export const questRewards: Record<EmblemTier, QuestReward[]> = {
  green: [
    { id: 'g-chocolate', name: 'x10 Chocolate (33% EXP)', section: 'consumables', options: one(3, 1), tags: ['daily', 'personal', 'temporary', 'cheap'] },
    { id: 'g-chaos-potion', name: 'x2 Chaos Potion', section: 'consumables', options: one(1, 2), tags: ['daily', 'personal', 'temporary', 'cheap'] },
    { id: 'g-exp-potion', name: 'EXP Potion 1.25%', section: 'consumables', options: one(8, 3), tags: ['daily', 'personal', 'temporary'] },
    { id: 'g-attack-potion', name: 'Attack Potion 25%', section: 'consumables', options: qty([['x8', 1, 4], ['x24', 3, 4], ['x72', 9, 4]]), tags: ['personal', 'cheap'] },
    { id: 'g-attack-exp-potion', name: 'x30 Attack EXP Potion 40%', section: 'consumables', options: one(5, 4), tags: ['once', 'personal', 'cheap'] },
    { id: 'g-scanner', name: 'Quantum Scanner', section: 'consumables', options: qty([['x5', 1, 5], ['x15', 3, 5], ['x45', 9, 5]]), tags: [],
      note: 'Escaneia personagens aliados e inimigos, inclusive PB. Cooldown de 1 min.' },
    { id: 'g-ether', name: 'Ether Scroll', section: 'consumables', options: one(7, 7), tags: ['personal', 'cheap'] },
    { id: 'g-favor-talic', name: 'Favor Talic', section: 'consumables', options: one(9, 6), tags: ['cheap'] },
    { id: 'g-rare-ore', name: 'x99 Rare Ore', section: 'consumables', options: one(9, 7), tags: ['cheap'] },
    { id: 'g-booster-40', name: 'Lv 40 Booster Blueprint', section: 'consumables', options: one(2, 10), tags: [] },
    { id: 'g-booster-50', name: 'Lv 50 Booster Blueprint', section: 'consumables', options: one(6, 10), tags: [] },
    { id: 'g-fp-potion', name: 'Unlimited FP Potion', section: 'special', options: one(20, 11), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '10kk–20kk' },
    { id: 'g-sp-potion', name: 'Unlimited SP Potion', section: 'special', options: one(21, 11), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '10kk–20kk' },
    { id: 'g-relic-box-45', name: 'x2 Random 1GR Relic Box + 45 emblemas verdes', section: 'special', options: one(60, 11), tags: ['once', 'personal'], price: '5kk' },
    { id: 'g-relic-box-60', name: 'x2 Random 1GR Relic Box + 60 emblemas verdes', section: 'special', options: one(99, 11), tags: ['once', 'personal'], price: '5kk' },
    { id: 'g-bd-quest', name: 'BD 46-50 Double Reward Quest', section: 'bd', options: one(3, 9), tags: ['daily', 'personal', 'temporary'], price: '300k' },
    { id: 'g-citadel-quest', name: 'BD Small Citadel Double Reward Quest', section: 'bd', options: one(10, 9), tags: ['daily', 'personal', 'temporary'], price: '1kk' },
    { id: 'g-bd-reward-shard-45', name: 'BD Reward Shard (45+)', section: 'bd', options: qty([['x1', 2, 7], ['x3', 6, 7], ['x9', 18, 7]]), tags: ['personal', 'cheap'] },
    { id: 'g-bd-reward-shard-50', name: 'BD Reward Shard (50+)', section: 'bd', options: qty([['x1', 8, 7], ['x3', 24, 7], ['x9', 72, 7]]), tags: ['personal', 'cheap'] },
    { id: 'g-bd-shard', name: 'BD 46-50 Shard', section: 'bd', options: qty([['x2', 3, 8], ['x6', 9, 8], ['x12', 18, 8]]), tags: ['cheap'], note: bdKey },
    { id: 'g-weapon-45', name: '45 Weapon type-B', section: 'weapons', options: one(24, 10), tags: lockedGear },
    { id: 'g-weapon-45-up', name: '45 Weapon type-B', section: 'weapons', extra: '45 Weapon type-B', options: one(16, 10), tags: lockedGear },
    { id: 'g-weapon-50-up', name: '50 Weapon type-B', section: 'weapons', extra: '50 Weapon type-B', options: one(60, 10), tags: lockedGear, price: '5kk' },
    { id: 'g-armor-c-38', name: '38(43) Armor type-C Defense', section: 'armor', extra: '43 Armor type-B', options: classes(9, [15, 16, 17]), tags: ['talic5', 'choose'] },
    { id: 'g-armor-45-up', name: '45 Armor type-B', section: 'armor', extra: '45 Armor type-B', options: classes(5, [18, 19, 20]), tags: lockedGear },
    { id: 'g-armor-47-up', name: '47 Armor type-B', section: 'armor', extra: '47 Armor type-B', options: classes(14, [21, 22, 23]), tags: lockedGear },
    { id: 'g-armor-c-44', name: '44(47) Armor type-C Defense', section: 'armor', options: one(8, 21), tags: ['talic5', 'choose', 'once', 'personal'] },
    { id: 'g-armor-50-up', name: '50 Armor type-B', section: 'armor', extra: '50 Armor type-B', options: classes(22, [24, 25, 26]), tags: lockedGear, price: '3.5kk' },
    { id: 'g-armor-50', name: '50 Armor type-B', section: 'armor', options: classes(35, [24, 25, 26]), tags: ['talic5', 'random'], price: '2kk' },
    { id: 'g-armor-c-47', name: '47(50) Armor type-C Defense', section: 'armor', options: one(30, 24), tags: ['talic5', 'choose', 'once', 'personal'] },
  ],
  yellow: [
    { id: 'y-pvp-potion', name: '300 PVP Potion', section: 'consumables', extra: '250 PVP Potion (quest do cash shop)', options: one(1, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'y-guild-exp', name: 'Guild EXP Potion +2', section: 'consumables', options: one(1, 6), tags: ['daily', 'personal', 'temporary'] },
    { id: 'y-potions', name: 'Potions (12% HP · 8 crit)', section: 'consumables', options: qty([['x5', 2, 2], ['x15', 6, 2], ['x45', 18, 2]]), tags: ['choose', 'personal', 'cheap'] },
    { id: 'y-taraven', name: 'Taraven Scroll', section: 'consumables', options: one(3, 5), tags: ['personal', 'cheap'] },
    { id: 'y-rune', name: 'x20 Random Rune', section: 'consumables', options: one(3, 6), tags: ['once', 'personal'] },
    { id: 'y-t4-set', name: 'T4 Set', section: 'special', options: one(6, 6), tags: ['cheap'] },
    { id: 'y-red-stone', name: 'x10 Red Stone + 25 emblemas amarelos', section: 'special', options: one(35, 6), tags: ['once'], price: '3kk' },
    { id: 'y-effect-crit', name: 'x2 Item Effect Crit', section: 'consumables', options: one(5, 7), tags: ['cheap'], note: 'Usado para craftar armadura type-C.' },
    { id: 'y-effect-hp', name: 'x2 Item Effect HP', section: 'consumables', options: one(5, 8), tags: ['cheap'], note: 'Usado para craftar armadura type-C.' },
    { id: 'y-red-catalyst', name: 'Red Catalyst Sample', section: 'consumables', options: one(2, 9), tags: [], note: 'Usado para craftar armadura 50 type-SP.' },
    { id: 'y-hp-3000', name: 'Unlimited +3000 HP Potion + 9 emblemas amarelos', section: 'special', options: one(10, 4), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '45kk' },
    { id: 'y-relic-box-1gr', name: 'x2 Random 1GR Relic Box + 20 emblemas amarelos', section: 'special', options: one(30, 7), tags: ['once', 'personal'], price: '5kk' },
    { id: 'y-relic-box-2gr', name: 'x2 Random 2GR Relic Box + 30 emblemas amarelos', section: 'special', options: one(50, 7), tags: ['once', 'personal'], price: '20kk' },
    { id: 'y-bd-quest', name: 'BD 50-55 Reward Quest', section: 'bd', options: one(2, 10), tags: ['daily', 'personal', 'temporary'] },
    { id: 'y-bd-reward-shard', name: 'BD Reward Shard (50+)', section: 'bd', options: qty([['x7', 9, 9], ['x21', 27, 9]]), tags: ['personal', 'cheap'] },
    { id: 'y-citadel-shard', name: 'BD Small Citadel Shard', section: 'bd', options: qty([['x1', 3, 10], ['x3', 9, 10], ['x9', 27, 10]]), tags: ['cheap'], note: bdKey },
    { id: 'y-weapon-elan-46', name: 'Blueprint 46 Weapon Elan Relic + 50 emblemas amarelos', section: 'weapons', options: one(99, 13), tags: [...lockedGear, 'once'], price: '30kk' },
    { id: 'y-armor-c-crit', name: '50 Armor type-C Crit', section: 'armor', options: one(28, 12), tags: [...lockedGear, 'once'] },
    { id: 'y-palmas-12', name: 'Blueprint for Lv.50 Palmas Armor', section: 'armor', extra: 'Lv.50 Palmas Armor', options: one(16, 12), tags: [...lockedGear, 'once'] },
    { id: 'y-palmas-13', name: 'Blueprint for Lv.50 Palmas Armor', section: 'armor', extra: 'Lv.50 Palmas Armor', options: one(17, 13), tags: [...lockedGear, 'once'] },
    { id: 'y-jewelry', name: 'Advanced MiddleClass Jewelry (10% ataque + 5% HP)', section: 'special', extra: 'MiddleClass Jewelry', options: one(12, 14), tags: ['once', 'personal', 'temporary'],
      note: `Escolha anel ou amuleto. ${tempWeek}`, price: '5kk' },
  ],
  red: [
    { id: 'r-pvp-potion-350', name: '350 PVP Potion', section: 'consumables', extra: '250 PVP Potion (quest do cash shop)', options: one(2, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'r-pvp-potion-500', name: '500 PVP Potion', section: 'consumables', extra: 'x3 45 PVP Potion (quests de Elan)', options: one(3, 1), tags: ['once', 'personal', 'temporary'] },
    { id: 'r-guild-exp-8', name: 'Guild EXP Potion +8', section: 'consumables', extra: 'Guild EXP Potion +4', options: one(1, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'r-guild-exp-temp', name: 'Temporary Guild EXP Potion 5% + 10 emblemas vermelhos', section: 'consumables', options: one(15, 2), tags: [],
      note: '+5% de EXP para os personagens da guild por 3 horas.' },
    { id: 'r-gold-points', name: '100 Gold Points', section: 'special', options: one(10, 3), tags: ['personal'] },
    { id: 'r-rune', name: 'x20 Random Rune', section: 'consumables', options: one(1, 3), tags: ['once', 'personal'] },
    { id: 'r-red-stone', name: 'x10 Red Stone + 29 emblemas vermelhos', section: 'special', options: one(30, 4), tags: ['once'], price: '3kk' },
    { id: 'r-keen-talic', name: 'x3 Keen Talic + 24 emblemas vermelhos', section: 'special', options: one(25, 4), tags: ['once'], price: '3kk' },
    { id: 'r-leon-converter', name: 'Leon Part Converter', section: 'special', options: one(70, 6), tags: ['once', 'personal'],
      note: 'A Leon part também vira pessoal na combinação. Na arma Leon só entra uma part pessoal; a arma vira pessoal e não combina de novo (exceto slot-puncher e "orange gear").' },
    { id: 'r-t5-stone', name: 'T5 Stone', section: 'special', extra: 'T5 Stone', options: one(2, 6), tags: ['choose', 'cheap'] },
    { id: 'r-hp-4000', name: 'Unlimited +4000 HP Potion', section: 'special', options: one(3, 8), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '75kk' },
    { id: 'r-relic-box-1gr', name: 'x2 Random 1GR Relic Box + 27 emblemas vermelhos', section: 'special', options: one(28, 10), tags: ['once', 'personal'], price: '5kk' },
    { id: 'r-relic-box-2gr', name: 'x2 Random 2GR Relic Box + 15 emblemas vermelhos', section: 'special', options: one(30, 10), tags: ['once', 'personal'] },
    { id: 'r-relic-box-3gr', name: 'Random 3GR Relic Box + 30 emblemas vermelhos', section: 'special', options: one(60, 10), tags: ['once', 'personal'] },
    { id: 'r-bd-shard', name: 'BD 50-55 Shard', section: 'bd', options: qty([['x1', 4, 6], ['x3', 12, 6], ['x9', 36, 6]]), tags: ['cheap'], note: bdKey },
    { id: 'r-bd-reward-shard', name: 'BD Reward Shard (50+)', section: 'bd', options: qty([['x1', 1, 7], ['x3', 3, 7], ['x9', 9, 7]]), tags: ['personal', 'cheap'] },
    { id: 'r-weapon-elan-47', name: 'Blueprint 47 Weapon Elan Relic + 40 emblemas vermelhos', section: 'weapons', extra: '47 Weapon Elan', options: one(65, 13), tags: [...lockedGear, 'once'], price: '50kk' },
    { id: 'r-red-sp-armor', name: 'Red SP Armor Blueprint + 20 emblemas vermelhos', section: 'armor', options: one(30, 13), tags: ['once', 'personal', 'cheap'],
      note: 'Ao combinar com a armadura, ela também vira pessoal e não combina de novo.' },
    { id: 'r-emitter', name: 'Blueprint Emitter Omega Epsilon Omicron B (1GR → 2GR)', section: 'special', options: one(25, 13), tags: ['once', 'personal'],
      note: 'Ao combinar com joia, a joia também vira pessoal.' },
    { id: 'r-jewelry', name: 'Advanced MiddleClass Jewelry (10% ataque + 5% HP)', section: 'special', extra: 'MiddleClass Jewelry', options: one(4, 14), tags: ['once', 'personal', 'temporary'],
      note: `Escolha anel ou amuleto. ${tempWeek}`, price: '5kk' },
  ],
}
