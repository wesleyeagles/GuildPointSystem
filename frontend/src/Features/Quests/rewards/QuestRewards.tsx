import { useState } from 'react'
import { questRewards, sectionLabels, tagLabels, tierLabels, type EmblemTier, type QuestReward, type RewardOption, type RewardSection } from './rewardData'
import './QuestRewards.styles.scss'

const tiers = Object.keys(tierLabels) as EmblemTier[]
const sections = Object.keys(sectionLabels) as RewardSection[]

function Cost({ option, tier }: { option: RewardOption; tier: EmblemTier }) {
  return <span className="quest-rewards__cost" aria-label={`${option.emblems} emblemas e ${option.crystals} cristais`}>
    <span className={`quest-rewards__amount is-${tier}`}><img src="/icons/quests/emblem.png" alt="" width={34} height={34} />{option.emblems}</span>
    <span aria-hidden="true" className="quest-rewards__plus">+</span>
    <span className="quest-rewards__amount is-crystal"><img src="/icons/quests/crystal.png" alt="" width={34} height={34} />{option.crystals}</span>
  </span>
}

function RewardCard({ reward, tier }: { reward: QuestReward; tier: EmblemTier }) {
  const single = reward.options.length === 1 ? reward.options[0] : null
  return <article className="quest-rewards__card">
    <header>
      <h4>{reward.name}</h4>
      {reward.extra && <span className="quest-rewards__extra" title="Item extra necessário na combinação">+ {reward.extra}</span>}
    </header>
    {single ? <Cost option={single} tier={tier} /> : <ul className="quest-rewards__options">
      {reward.options.map(option => <li key={option.label}>
        <span className="quest-rewards__option-label">{option.label}</span><Cost option={option} tier={tier} />
      </li>)}
    </ul>}
    {reward.tags.length > 0 && <ul className="quest-rewards__tags">
      {reward.tags.map(tag => <li key={tag} className={`is-${tag}`}>{tagLabels[tag]}</li>)}
    </ul>}
    {(reward.note || reward.price) && <footer>
      {reward.note && <p>{reward.note}</p>}
      {reward.price && <p className="quest-rewards__price">Taxa da combinação: <strong>{reward.price}</strong></p>}
    </footer>}
  </article>
}

export function QuestRewards() {
  const [tier, setTier] = useState<EmblemTier>('green')
  const [section, setSection] = useState<RewardSection | 'all'>('all')
  const [search, setSearch] = useState('')
  const term = search.trim().toLowerCase()
  const visible = questRewards[tier].filter(r => (section === 'all' || r.section === section)
    && (!term || `${r.name} ${r.extra ?? ''} ${r.note ?? ''}`.toLowerCase().includes(term)))
  const grouped = sections.map(s => [s, visible.filter(r => r.section === s)] as const).filter(([, rows]) => rows.length > 0)
  return <div className="quest-rewards">
    <div className="quest-rewards__tiers" role="radiogroup" aria-label="Tipo de emblema">
      {tiers.map(t => <button key={t} type="button" role="radio" aria-checked={tier === t} className={`is-${t}`} onClick={() => setTier(t)}>
        <img src="/icons/quests/emblem.png" alt="" width={36} height={36} />
        <span><strong>{tierLabels[t]}</strong><small>{questRewards[t].length} combinações</small></span>
      </button>)}
    </div>
    <div className="quest-rewards__toolbar">
      <label className="quest-rewards__search"><span>Buscar</span>
        <input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Ex.: Armor, BD, Talic…" /></label>
    </div>
    <div className="quest-rewards__sections" role="group" aria-label="Categoria">
      {(['all', ...sections] as const).map(s => <button key={s} type="button" aria-pressed={section === s} onClick={() => setSection(s)}>
        {s === 'all' ? 'Todas' : sectionLabels[s]}
      </button>)}
    </div>
    {grouped.length === 0 && <p className="quest-rewards__empty">Nenhuma combinação encontrada.</p>}
    {grouped.map(([s, rows]) => <section key={s} className="quest-rewards__group" aria-labelledby={`quest-rewards-${s}`}>
      <h3 id={`quest-rewards-${s}`}>{sectionLabels[s]} <span>{rows.length}</span></h3>
      <div className="quest-rewards__grid">{rows.map(r => <RewardCard key={r.id} reward={r} tier={tier} />)}</div>
    </section>)}
  </div>
}
