import { useId, useState, type FormEvent } from 'react'
import {
  useAutoPointMaps,
  useAutoPointRules,
  useDeleteAutoPointRule,
  useSaveAutoPointRule,
  type AutoPointMap,
  type AutoPointRule,
  type AutoPointRuleInput,
} from '@/Domain/AutoPoints/hooks/useAutoPoints'
import { useObjectives } from '@/Domain/Objective/hooks/useObjectives'
import { Button } from '@/Shared/ui/components/Button/Button'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './AutoPoints.styles.scss'

const DAYS: { value: number; label: string }[] = [
  { value: 1, label: 'Seg' },
  { value: 2, label: 'Ter' },
  { value: 3, label: 'Qua' },
  { value: 4, label: 'Qui' },
  { value: 5, label: 'Sex' },
  { value: 6, label: 'Sáb' },
  { value: 7, label: 'Dom' },
]
const ALL_DAYS = DAYS.map((d) => d.value)

const EMPTY: AutoPointRuleInput = {
  label: '',
  enabled: true,
  weekdays: ALL_DAYS,
  shoutsBefore: [12, 7, 2],
  shout: '',
  shoutEn: '',
  rgEnabled: true,
  rgAt: '',
  rgMap: 'Cora HQ',
  rgRegions: ['Cora Outpost'],
  rgPoints: 2,
  presenceEnabled: true,
  presenceFrom: '',
  presenceTo: '',
  presenceMap: '',
  presenceRegions: [],
  objectiveName: '',
}

const place = (map: string | null, regions: string[]) =>
  regions.length ? `${regions.join(', ')} · ${map}` : `${map} (mapa inteiro)`

function daysLabel(days: number[]) {
  if (days.length === 7) return 'Todo dia'
  return DAYS.filter((d) => days.includes(d.value)).map((d) => d.label).join(', ')
}

const shortDate = (iso: string) => {
  const [, m, d] = iso.split('-')
  return `${d}/${m}`
}

/**
 * Admin → Pontos automáticos: regras de RG e presença por local, horário e dia da semana. O servidor manda os
 * shouts, dá os Pontos de RG a quem está no local no horário e o objetivo a quem passou pelo local na janela.
 */
export function AutoPointsPage() {
  const { data: rules, isLoading, error } = useAutoPointRules()
  const save = useSaveAutoPointRule()
  const remove = useDeleteAutoPointRule()
  const [editing, setEditing] = useState<{ id?: number; form: AutoPointRuleInput } | null>(null)
  const [deleting, setDeleting] = useState<AutoPointRule | null>(null)

  const toggle = (rule: AutoPointRule) => save.mutate({ id: rule.id, body: { ...rule, enabled: !rule.enabled } })

  return (
    <div className="auto-points">
      <Panel
        title="Pontos automáticos"
        code={rules ? String(rules.length) : '…'}
        actions={!editing && <Button size="sm" onClick={() => setEditing({ form: EMPTY })}>+ Nova regra</Button>}
      >
        <p className="auto-points__muted">
          Horários de Brasília. <strong>RG</strong>: quem está no local no horário (DLL enviou nos últimos 3 min, AFK ou
          não) ganha os pontos. <strong>Presença</strong>: quem a DLL viu no local durante a janela ganha o objetivo.
          Os shouts saem no jogo pelo "Sistema" nos minutos antes do início.
        </p>
        {isLoading && <p className="auto-points__muted">Carregando…</p>}
        {error && <p className="auto-points__error">{(error as Error).message}</p>}
        {save.error && !editing && <p className="auto-points__error">{(save.error as Error).message}</p>}

        {editing && (
          <RuleForm
            key={editing.id ?? 'new'}
            initial={editing.form}
            isNew={!editing.id}
            saving={save.isPending}
            error={save.error ? (save.error as Error).message : null}
            onCancel={() => { setEditing(null); save.reset() }}
            onSave={(form) => save.mutate({ id: editing.id, body: form }, { onSuccess: () => setEditing(null) })}
          />
        )}

        <ul className="auto-points__list">
          {rules?.map((rule) => (
            <li key={rule.id} className={`auto-points__rule${rule.enabled ? '' : ' auto-points__rule--off'}`}>
              <div className="auto-points__head">
                <strong>{rule.label}</strong>
                <span className="auto-points__days">{daysLabel(rule.weekdays)}</span>
                {!rule.enabled && <span className="auto-points__tag">Desligada</span>}
                <span className="auto-points__spacer" />
                <Button size="sm" variant="secondary" onClick={() => toggle(rule)} disabled={save.isPending}>
                  {rule.enabled ? 'Desligar' : 'Ligar'}
                </Button>
                <Button size="sm" variant="secondary" onClick={() => { save.reset(); setEditing({ id: rule.id, form: rule }) }}>
                  Editar
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setDeleting(rule)}>Apagar</Button>
              </div>
              {rule.rgEnabled && (
                <div className="auto-points__step">
                  <span className="auto-points__kind">RG</span>
                  {rule.rgAt} · {place(rule.rgMap, rule.rgRegions)} · <strong>+{rule.rgPoints}</strong> pts
                </div>
              )}
              {rule.presenceEnabled && (
                <div className="auto-points__step">
                  <span className="auto-points__kind auto-points__kind--presence">Presença</span>
                  {rule.presenceFrom}–{rule.presenceTo} · {place(rule.presenceMap, rule.presenceRegions)} →{' '}
                  <strong>{rule.objectiveName}</strong>
                  {rule.objectiveMissing && (
                    <span className="auto-points__error"> · o objetivo não existe na aba Objetivos: ninguém recebe</span>
                  )}
                </div>
              )}
              {rule.shoutsBefore.length > 0 && rule.shout && (
                <div className="auto-points__step auto-points__muted">
                  Shout {rule.shoutsBefore.join(', ')} min antes: “{rule.shout}”
                </div>
              )}
              {rule.lastRuns.length > 0 && (
                <div className="auto-points__runs">
                  {rule.lastRuns.map((run) => (
                    <span key={run.date}>
                      {shortDate(run.date)}:{rule.rgEnabled && ` RG ${run.rg}`}
                      {rule.presenceEnabled && ` · ${run.objective} no objetivo`}
                    </span>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ul>
        {rules && rules.length === 0 && !editing && <p className="auto-points__muted">Nenhuma regra ainda.</p>}
      </Panel>

      <ConfirmDialog
        open={!!deleting}
        title="Apagar regra"
        code="ADM"
        confirmLabel="Apagar"
        variant="danger"
        loading={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })}
      >
        Apagar <strong>{deleting?.label}</strong>? Os pontos já dados continuam. Para só pausar, use “Desligar”.
      </ConfirmDialog>
    </div>
  )
}

interface RuleFormProps {
  initial: AutoPointRuleInput
  isNew: boolean
  saving: boolean
  error: string | null
  onSave: (form: AutoPointRuleInput) => void
  onCancel: () => void
}

function RuleForm({ initial, isNew, saving, error, onSave, onCancel }: RuleFormProps) {
  const [form, setForm] = useState<AutoPointRuleInput>({ ...initial, shout: initial.shout ?? '', shoutEn: initial.shoutEn ?? '' })
  const [shoutsText, setShoutsText] = useState(initial.shoutsBefore.join(', '))
  const { data: maps = [] } = useAutoPointMaps()
  const { data: objectives = [] } = useObjectives()
  const set = <K extends keyof AutoPointRuleInput>(key: K, value: AutoPointRuleInput[K]) => setForm((f) => ({ ...f, [key]: value }))

  const toggleDay = (day: number) =>
    set('weekdays', form.weekdays.includes(day) ? form.weekdays.filter((d) => d !== day) : [...form.weekdays, day].sort())

  const start = form.rgEnabled ? form.rgAt : form.presenceFrom
  const suggestShout = () => {
    const where = form.rgEnabled ? (form.rgRegions[0] ?? form.rgMap) : (form.presenceRegions[0] ?? form.presenceMap)
    set('shout', form.rgEnabled ? `RG ${where}, Pontos de RG as ${form.rgAt}` : `${form.label} as ${form.presenceFrom} em ${where}`)
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const shoutsBefore = shoutsText.split(/[,\s]+/).filter(Boolean).map(Number).filter((n) => Number.isFinite(n))
    onSave({ ...form, shoutsBefore })
  }

  return (
    <form className="auto-points__form" onSubmit={submit}>
      <h3>{isNew ? 'Nova regra' : `Editar: ${initial.label}`}</h3>
      <div className="auto-points__row">
        <label className="auto-points__grow">
          Nome
          <input value={form.label} onChange={(e) => set('label', e.target.value)} placeholder="Ex.: PB Elan" maxLength={80} required />
        </label>
        <label className="auto-points__check">
          <input type="checkbox" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} /> Ligada
        </label>
      </div>

      <fieldset className="auto-points__fieldset">
        <legend>Dias (Brasília)</legend>
        <div className="auto-points__chips">
          {DAYS.map((d) => (
            <button
              key={d.value}
              type="button"
              className={`auto-points__chip${form.weekdays.includes(d.value) ? ' auto-points__chip--on' : ''}`}
              onClick={() => toggleDay(d.value)}
            >
              {d.label}
            </button>
          ))}
          <button type="button" className="auto-points__chip" onClick={() => set('weekdays', form.weekdays.length === 7 ? [] : ALL_DAYS)}>
            {form.weekdays.length === 7 ? 'Nenhum' : 'Todos'}
          </button>
        </div>
      </fieldset>

      <fieldset className="auto-points__fieldset">
        <legend>
          <label className="auto-points__check">
            <input type="checkbox" checked={form.rgEnabled} onChange={(e) => set('rgEnabled', e.target.checked)} /> RG no local
          </label>
        </legend>
        {form.rgEnabled && (
          <>
            <div className="auto-points__row">
              <label>Horário<input type="time" value={form.rgAt ?? ''} onChange={(e) => set('rgAt', e.target.value)} required /></label>
              <label>Pontos<input type="number" min={1} max={100} value={form.rgPoints} onChange={(e) => set('rgPoints', Number(e.target.value))} /></label>
            </div>
            <PlacePicker maps={maps} map={form.rgMap ?? ''} regions={form.rgRegions}
              onMap={(m) => setForm((f) => ({ ...f, rgMap: m, rgRegions: [] }))} onRegions={(r) => set('rgRegions', r)} />
          </>
        )}
      </fieldset>

      <fieldset className="auto-points__fieldset">
        <legend>
          <label className="auto-points__check">
            <input type="checkbox" checked={form.presenceEnabled} onChange={(e) => set('presenceEnabled', e.target.checked)} /> Presença → objetivo
          </label>
        </legend>
        {form.presenceEnabled && (
          <>
            <div className="auto-points__row">
              <label>De<input type="time" value={form.presenceFrom ?? ''} onChange={(e) => set('presenceFrom', e.target.value)} required /></label>
              <label>Até<input type="time" value={form.presenceTo ?? ''} onChange={(e) => set('presenceTo', e.target.value)} required /></label>
              <label className="auto-points__grow">
                Objetivo
                <select value={form.objectiveName ?? ''} onChange={(e) => set('objectiveName', e.target.value)} required>
                  <option value="">Escolha…</option>
                  {form.objectiveName && !objectives.some((o) => o.name === form.objectiveName) && (
                    <option value={form.objectiveName}>{form.objectiveName} (não existe)</option>
                  )}
                  {objectives.map((o) => <option key={o.id} value={o.name}>{o.name} ({o.points} pts)</option>)}
                </select>
              </label>
            </div>
            <PlacePicker maps={maps} map={form.presenceMap ?? ''} regions={form.presenceRegions}
              onMap={(m) => setForm((f) => ({ ...f, presenceMap: m, presenceRegions: [] }))} onRegions={(r) => set('presenceRegions', r)} />
            <p className="auto-points__muted">Fim antes do início = passa da meia-noite. Quem passar pelo local pelo menos uma vez na janela recebe.</p>
          </>
        )}
      </fieldset>

      <fieldset className="auto-points__fieldset">
        <legend>Shouts no jogo</legend>
        <div className="auto-points__row">
          <label>
            Minutos antes{start ? ` de ${start}` : ''}
            <input value={shoutsText} onChange={(e) => setShoutsText(e.target.value)} placeholder="12, 7, 2 (vazio = sem shout)" />
          </label>
          <label className="auto-points__grow">
            Texto
            <input value={form.shout ?? ''} onChange={(e) => set('shout', e.target.value)} maxLength={120} placeholder="RG Base Avançada, Pontos de RG as 21:52" />
          </label>
          <Button type="button" size="sm" variant="ghost" onClick={suggestShout}>Sugerir</Button>
        </div>
        <label>
          Texto em inglês (English Players; vazio = o mesmo)
          <input value={form.shoutEn ?? ''} onChange={(e) => set('shoutEn', e.target.value)} maxLength={120} placeholder="RG at the Outpost, RG points at 07:52 (Jakarta time)" />
        </label>
      </fieldset>

      {error && <p className="auto-points__error">{error}</p>}
      <div className="auto-points__actions">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" loading={saving}>{isNew ? 'Criar regra' : 'Salvar'}</Button>
      </div>
    </form>
  )
}

interface PlacePickerProps {
  maps: AutoPointMap[]
  map: string
  regions: string[]
  onMap: (map: string) => void
  onRegions: (regions: string[]) => void
}

/** Mapa (lista do que a DLL já viu, ou digitado) e regiões dele; nenhuma região marcada = o mapa inteiro. */
function PlacePicker({ maps, map, regions, onMap, onRegions }: PlacePickerProps) {
  const [extra, setExtra] = useState('')
  const known = maps.find((m) => m.map.toLowerCase() === map.trim().toLowerCase())?.regions ?? []
  const options = [...known, ...regions.filter((r) => !known.includes(r))]
  const toggle = (region: string) =>
    onRegions(regions.includes(region) ? regions.filter((r) => r !== region) : [...regions, region])
  const listId = useId()

  return (
    <div className="auto-points__place">
      <label>
        Mapa
        <input list={listId} value={map} onChange={(e) => onMap(e.target.value)} placeholder="Ex.: Cora HQ" required />
        <datalist id={listId}>{maps.map((m) => <option key={m.map} value={m.map} />)}</datalist>
      </label>
      <div className="auto-points__regions">
        <span className="auto-points__muted">Regiões {regions.length === 0 ? '(nenhuma = mapa inteiro)' : `(${regions.length})`}</span>
        <div className="auto-points__chips">
          {options.map((r) => (
            <button key={r} type="button" className={`auto-points__chip${regions.includes(r) ? ' auto-points__chip--on' : ''}`} onClick={() => toggle(r)}>
              {r}
            </button>
          ))}
          <input
            className="auto-points__extra"
            value={extra}
            onChange={(e) => setExtra(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                const v = extra.trim()
                if (v && !regions.includes(v)) onRegions([...regions, v])
                setExtra('')
              }
            }}
            placeholder="+ outra região (Enter)"
          />
        </div>
      </div>
    </div>
  )
}
