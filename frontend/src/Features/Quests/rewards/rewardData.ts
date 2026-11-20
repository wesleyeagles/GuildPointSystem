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

// Tabela atualizada em 20/11/2026.
export const questRewards: Record<EmblemTier, QuestReward[]> = {
  green: [
    { id: 'g-chocolate', name: 'x10 Chocolate (33% EXP)', section: 'consumables', options: one(3, 1), tags: ['daily', 'personal', 'temporary', 'cheap'] },
    { id: 'g-chaos-potion', name: 'x2 Chaos Potion', section: 'consumables', options: one(1, 2), tags: ['daily', 'personal', 'temporary', 'cheap'] },
    { id: 'g-exp-potion', name: 'EXP Potion 2%', section: 'consumables', options: one(8, 3), tags: ['daily', 'personal', 'temporary'] },
    { id: 'g-attack-potion', name: 'Attack Potion 25%', section: 'consumables', options: qty([['x8', 1, 4], ['x24', 3, 4], ['x72', 9, 4]]), tags: ['personal', 'cheap'] },
    { id: 'g-attack-exp-potion', name: 'x30 Attack EXP Potion 40%', section: 'consumables', options: one(5, 4), tags: ['once', 'personal', 'cheap'] },
    { id: 'g-scanner', name: 'Quantum Scanner', section: 'consumables', options: qty([['x6', 1, 5], ['x18', 3, 5], ['x54', 9, 5]]), tags: [],
      note: 'Escaneia personagens aliados e inimigos, inclusive PB. Cooldown de 1 min.' },
    { id: 'g-ether', name: 'Ether Scroll', section: 'consumables', options: one(7, 7), tags: ['personal', 'cheap'] },
    { id: 'g-rare-ore', name: 'x99 Rare Ore', section: 'consumables', options: one(9, 7), tags: ['cheap'] },
    { id: 'g-booster-40', name: 'Lv 40 Booster Blueprint', section: 'consumables', options: one(1, 10), tags: [] },
    { id: 'g-booster-50', name: 'Lv 50 Booster Blueprint', section: 'consumables', options: one(5, 10), tags: [] },
    { id: 'g-red-catalyst', name: 'Red Catalyst Sample', section: 'consumables', options: qty([['x1', 2, 10], ['x3', 6, 10], ['x9', 18, 10]]), tags: ['cheap'],
      note: 'Usado para craftar o Red Blueprint da armadura SP.' },
    { id: 'g-fp-potion', name: 'Unlimited FP Potion', section: 'special', options: one(20, 11), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '10kk–20kk' },
    { id: 'g-sp-potion', name: 'Unlimited SP Potion', section: 'special', options: one(21, 11), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '10kk–20kk' },
    { id: 'g-relic-box-35', name: 'x2 Random 1GR Relic Box + 35 emblemas verdes', section: 'special', options: one(45, 11), tags: ['once', 'personal'], price: '5kk' },
    { id: 'g-relic-box-45', name: 'x2 Random 1GR Relic Box + 45 emblemas verdes', section: 'special', options: one(60, 11), tags: ['once', 'personal'], price: '5kk' },
    { id: 'g-relic-box-2gr', name: 'x2 Random 2GR Relic Box + 60 emblemas verdes', section: 'special', options: one(99, 11), tags: ['once', 'personal'], price: '20kk' },
    { id: 'g-relic-box-1gr', name: 'Random 1GR Relic Box', section: 'special', options: one(35, 11), tags: ['personal'], price: '5kk' },
    { id: 'g-bd-quest', name: 'BD 46-50 Double Reward Quest', section: 'bd', options: one(3, 9), tags: ['daily', 'personal', 'temporary'], price: '100k' },
    { id: 'g-citadel-quest', name: 'BD Small Citadel Double Reward Quest', section: 'bd', options: one(6, 9), tags: ['daily', 'personal', 'temporary'], price: '500k' },
    { id: 'g-bd-50-quest', name: 'BD 50-55 Double Reward Quest', section: 'bd', options: one(8, 10), tags: ['daily', 'personal', 'temporary'], price: '750k' },
    { id: 'g-bd-reward-shard-45', name: 'BD Reward Shard (45+)', section: 'bd', options: qty([['x1', 2, 7], ['x3', 6, 7], ['x9', 18, 7]]), tags: ['personal', 'cheap'] },
    { id: 'g-bd-reward-shard-50', name: 'BD Reward Shard (50+)', section: 'bd', options: qty([['x1', 8, 7], ['x3', 24, 7], ['x9', 72, 7]]), tags: ['personal', 'cheap'] },
    { id: 'g-bd-shard', name: 'BD 46-50 Shard', section: 'bd', options: qty([['x2', 2, 8], ['x6', 6, 8], ['x12', 12, 8]]), tags: ['cheap'], note: bdKey },
    { id: 'g-citadel-shard', name: 'x2 BD Small Citadel Shard', section: 'bd', options: one(8, 8), tags: ['daily', 'cheap'], note: bdKey },
    { id: 'g-weapon-50-up', name: '50 Weapon type-B', section: 'weapons', extra: '50 Weapon type-B', options: one(30, 10), tags: [...lockedGear, 'cheap'] },
    { id: 'g-weapon-50', name: '50 Weapon type-B', section: 'weapons', options: one(50, 10), tags: ['talic5', 'random'], price: '2kk' },
    { id: 'g-weapon-elan-45', name: 'Blueprint 45 Weapon Elan Relic', section: 'weapons', extra: '45 Weapon Elan Relic', options: one(65, 10), tags: [...lockedGear, 'once', 'cheap'] },
    { id: 'g-armor-c-38', name: '38(43) Armor type-C Defense', section: 'armor', extra: '43 Armor type-B', options: classes(5, [15, 16, 17]), tags: ['talic5', 'choose'] },
    { id: 'g-armor-c-42', name: '42(45) Armor type-C Defense', section: 'armor', extra: '45 Armor type-B', options: classes(9, [18, 19, 20]), tags: ['talic5', 'choose'] },
    { id: 'g-armor-c-44', name: '44(47) Armor type-C Defense', section: 'armor', extra: '47 Armor type-B', options: classes(20, [21, 22, 23]), tags: ['talic5', 'choose'], price: '3kk' },
    { id: 'g-armor-50', name: '50 Armor type-B', section: 'armor', options: classes(22, [24, 25, 26]), tags: ['talic5', 'random'], price: '1kk' },
    { id: 'g-armor-c-47', name: '47(50) Armor type-C Defense', section: 'armor', options: one(18, 24), tags: ['talic5', 'choose', 'once', 'personal'] },
    { id: 'g-armor-c-hp', name: '(45)50 Armor type-C HP', section: 'armor', options: one(36, 24), tags: [...lockedGear, 'once'], price: '3.5kk' },
  ],
  yellow: [
    { id: 'y-pvp-potion', name: '300 PVP Potion', section: 'consumables', extra: '250 PVP Potion (quest do cash shop)', options: one(1, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'y-pvp-potion-500', name: '500 PVP Potion', section: 'consumables', extra: 'x3 45 PVP Potion (quests de Elan)', options: one(6, 1), tags: ['once', 'personal', 'temporary'] },
    { id: 'y-guild-exp', name: 'Guild EXP Potion +2', section: 'consumables', options: one(1, 6), tags: ['daily', 'personal', 'temporary'] },
    { id: 'y-relic-mines', name: 'Relic Mines 1GR Lv.50', section: 'special', options: one(5, 11), tags: [] },
    { id: 'y-relic-mines-x3', name: 'x99+x99+x99 Relic Mines 1GR Lv.50', section: 'special', options: one(30, 11), tags: [] },
    { id: 'y-potions', name: 'Potions (12% HP · 8 crit)', section: 'consumables', options: qty([['x5', 2, 2], ['x15', 6, 2], ['x45', 18, 2]]), tags: ['choose', 'personal', 'cheap'] },
    { id: 'y-taraven', name: 'Taraven Scroll', section: 'consumables', options: one(3, 5), tags: ['personal', 'cheap'] },
    { id: 'y-relic-turret', name: 'Relic Turret 1GR Lv.50', section: 'special', options: one(3, 6), tags: ['personal', 'cheap'] },
    { id: 'y-rune', name: 'x20 Random Rune', section: 'consumables', options: one(4, 6), tags: ['once', 'personal'] },
    { id: 'y-t4-set', name: 'T4 Set', section: 'special', options: one(6, 6), tags: ['cheap'] },
    { id: 'y-effect-crit', name: 'x2 Item Effect Crit', section: 'consumables', options: one(4, 7), tags: ['cheap'], note: 'Usado para craftar armadura type-C.' },
    { id: 'y-effect-hp', name: 'x2 Item Effect HP', section: 'consumables', options: one(4, 8), tags: ['cheap'], note: 'Usado para craftar armadura type-C.' },
    { id: 'y-hammer', name: 'Normal Blacksmith Hammer', section: 'consumables', options: one(3, 9), tags: ['cheap'] },
    { id: 'y-hp-3000', name: 'Unlimited +3000 HP Potion + 10 emblemas amarelos', section: 'special', options: one(20, 4), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '45kk' },
    { id: 'y-relic-box-1gr', name: 'x2 Random 1GR Relic Box + 12 emblemas amarelos', section: 'special', options: one(20, 7), tags: ['once', 'personal'], price: '5kk' },
    { id: 'y-relic-box-2gr-25', name: 'x2 Random 2GR Relic Box + 25 emblemas amarelos', section: 'special', options: one(40, 7), tags: ['once', 'personal'], price: '15kk' },
    { id: 'y-relic-box-2gr-30', name: 'x2 Random 2GR Relic Box + 30 emblemas amarelos', section: 'special', options: one(65, 7), tags: ['once', 'personal'], price: '15kk' },
    { id: 'y-mid-citadel-quest', name: 'BD Mid Citadel Reward Quest', section: 'bd', options: one(5, 10), tags: ['daily', 'personal', 'temporary'], price: '1kk' },
    { id: 'y-bd-reward-shard', name: 'BD Reward Shard (50+)', section: 'bd', options: qty([['x7', 9, 9], ['x21', 27, 9]]), tags: ['personal', 'cheap'] },
    { id: 'y-bd-shard', name: 'BD 50-55 Shard', section: 'bd', options: qty([['x1', 4, 10], ['x3', 12, 10], ['x9', 36, 10]]), tags: ['cheap'], note: bdKey },
    { id: 'y-weapon-elan-47', name: 'Blueprint 47 Weapon Elan Relic + 30 emblemas amarelos', section: 'weapons', extra: '47 Weapon Elan Relic', options: one(75, 13), tags: [...lockedGear, 'once'], price: '10kk' },
    { id: 'y-red-sp-armor', name: 'Red SP Armor Blueprint', section: 'armor', options: one(28, 12), tags: ['once', 'personal', 'cheap'],
      note: 'Ao combinar com a armadura, ela também vira pessoal e não combina de novo.' },
    { id: 'y-jewelry', name: "Advanced Adventurer's Jewelry (10% ataque + 5% HP)", section: 'special', extra: 'MiddleClass Jewelry', options: one(12, 14), tags: ['once', 'personal', 'temporary'],
      note: `Escolha anel ou amuleto. ${tempWeek}`, price: '5kk' },
  ],
  red: [
    { id: 'r-pvp-potion-350', name: '350 PVP Potion', section: 'consumables', extra: '250 PVP Potion (quest do cash shop)', options: one(2, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'r-guild-exp-8', name: 'Guild EXP Potion +8', section: 'consumables', extra: 'Guild EXP Potion +4', options: one(1, 1), tags: ['daily', 'personal', 'temporary'] },
    { id: 'r-guild-exp-temp', name: 'Temporary Guild EXP Potion 10% + 10 emblemas vermelhos', section: 'consumables', options: one(15, 2), tags: [],
      note: '+10% de EXP para os personagens da guild por 3 horas.' },
    { id: 'r-gold-points', name: '100 Gold Points', section: 'special', options: one(4, 3), tags: ['daily', 'personal', 'cheap'] },
    { id: 'r-rune', name: 'x20 Random Rune', section: 'consumables', options: one(1, 3), tags: ['once', 'personal'] },
    { id: 'r-leon-converter', name: 'Leon Part Converter', section: 'special', options: one(20, 6), tags: ['once', 'personal'],
      note: 'A Leon part também vira pessoal na combinação. Na arma Leon só entra uma part pessoal; a arma vira pessoal e não combina de novo (exceto slot-puncher e "orange gear").' },
    { id: 'r-t5-stone', name: 'T5 Stone', section: 'special', extra: 'T5 Stone', options: one(2, 6), tags: ['choose', 'cheap'] },
    { id: 'r-hp-4000', name: 'Unlimited +4000 HP Potion + 15 emblemas vermelhos', section: 'special', options: one(25, 7), tags: ['once', 'personal', 'temporary'], note: tempWeek, price: '75kk' },
    { id: 'r-relic-box-1gr', name: 'x2 Random 1GR Relic Box + 49 emblemas vermelhos', section: 'special', options: one(50, 10), tags: ['once', 'personal'], price: '5kk' },
    { id: 'r-relic-box-2gr-25', name: 'x2 Random 2GR Relic Box + 25 emblemas vermelhos', section: 'special', options: one(51, 10), tags: ['once', 'personal'], price: '15kk' },
    { id: 'r-relic-box-2gr-40', name: 'x2 Random 2GR Relic Box + 40 emblemas vermelhos', section: 'special', options: one(65, 10), tags: ['once', 'personal'], price: '15kk' },
    { id: 'r-relic-box-3gr', name: 'x2 Random 3GR Relic Box + 40 emblemas vermelhos', section: 'special', options: one(70, 10), tags: ['once', 'personal'] },
    { id: 'r-mid-citadel-shard', name: 'BD Mid Citadel Shard', section: 'bd', options: qty([['x1', 4, 6], ['x3', 12, 6], ['x9', 36, 6]]), tags: ['cheap'], note: bdKey },
    { id: 'r-bd-reward-shard', name: 'BD Reward Shard (50+)', section: 'bd', options: qty([['x1', 1, 7], ['x3', 3, 7], ['x9', 9, 7]]), tags: ['personal', 'cheap'] },
    { id: 'r-armor-53-legs', name: '53 Armor type-B Legs + 20 emblemas vermelhos', section: 'armor', options: one(75, 13), tags: ['talic5', 'choose', 'once'] },
    { id: 'r-jewelry', name: 'Elite MiddleClass Jewelry (15% ataque + 5% HP)', section: 'special', extra: 'MiddleClass Jewelry', options: one(12, 14), tags: ['once', 'personal', 'temporary'],
      note: `Escolha anel ou amuleto. ${tempWeek}`, price: '15kk' },
  ],
}
