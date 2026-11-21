import { useState } from 'react'
import {
  ARROW_DISTANCE_MAX,
  ARROW_DISTANCE_MIN,
  ARROW_SCALE_MAX,
  ARROW_SCALE_MIN,
  useDllDisplaySettings,
  useUpdateDllDisplaySettings,
  type DllDisplaySettings,
} from '@/Domain/Dll/hooks/useDll'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

/** Tamanho das setas do jogo em cima do alvo marcado (líder da PT / da guild), para todos que usam a DLL. */
export function ArrowSizeSettings() {
  const { data } = useDllDisplaySettings()
  if (!data) return null
  // A chave recria o formulário com os valores salvos sempre que eles mudam no servidor.
  const key = [data.partyArrowScale, data.guildArrowScale, data.arrowDistance, data.markerArrowScale, data.markerColor].join('-')
  return <ArrowSizeForm key={key} saved={data} />
}

function ArrowSlider({ label, value, onChange, min = ARROW_SCALE_MIN, max = ARROW_SCALE_MAX, step = 10 }: {
  label: string
  value: number
  onChange: (v: number) => void
  min?: number
  max?: number
  step?: number
}) {
  return (
    <label className="arrow-size">
      <span className="arrow-size__label">{label}</span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        aria-label={label}
        onChange={(e) => onChange(Number(e.target.value))}
      />
      <strong className="arrow-size__value">{value}%</strong>
    </label>
  )
}

function ArrowSizeForm({ saved }: { saved: DllDisplaySettings }) {
  const { showToast } = useAppToast()
  const update = useUpdateDllDisplaySettings()
  const [party, setParty] = useState(saved.partyArrowScale)
  const [guild, setGuild] = useState(saved.guildArrowScale)
  const [distance, setDistance] = useState(saved.arrowDistance)
  const [marker, setMarker] = useState(saved.markerArrowScale)
  const [color, setColor] = useState(`#${saved.markerColor.toLowerCase()}`)
  const savedColor = `#${saved.markerColor.toLowerCase()}`

  const dirty = party !== saved.partyArrowScale || guild !== saved.guildArrowScale || distance !== saved.arrowDistance ||
    marker !== saved.markerArrowScale || color.toLowerCase() !== savedColor
  const isDefault = saved.partyArrowScale === 100 && saved.guildArrowScale === 100 && saved.arrowDistance === 100 &&
    saved.markerArrowScale === 100

  const save = (partyArrowScale: number, guildArrowScale: number, arrowDistance: number, markerArrowScale: number) =>
    update.mutate({ partyArrowScale, guildArrowScale, arrowDistance, markerArrowScale, markerColor: color }, {
      onSuccess: () => showToast('Setas salvas.', 'success'),
      onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
    })

  return (
    <Panel title="Setas no jogo" code={`${saved.partyArrowScale}% · ${saved.guildArrowScale}% · ${saved.markerArrowScale}%`} className="chat-translator">
      <p className="dll-admin__muted">
        Tamanho das setas que o jogo mostra em cima do alvo marcado pelo líder da PT (azul) e da guild. 100% é o
        tamanho normal do jogo. A distância aproxima (menos de 100%) ou afasta (mais de 100%) a seta do alvo; em
        100% o centro da seta fica onde ele fica no tamanho normal. Vale para todos com a DLL nova (tamanho a partir
        da 1.50.0, distância a partir da 1.50.1) e muda em até meio minuto, sem reabrir o jogo.
      </p>
      <ArrowSlider label="Seta da PT" value={party} onChange={setParty} />
      <ArrowSlider label="Seta da guild" value={guild} onChange={setGuild} />
      <ArrowSlider label="Seta de marcação" value={marker} onChange={setMarker} />
      <ArrowSlider label="Distância" value={distance} onChange={setDistance} min={ARROW_DISTANCE_MIN} max={ARROW_DISTANCE_MAX} step={5} />
      <label className="arrow-size">
        <span className="arrow-size__label">Cor da marcação</span>
        <input type="color" value={color} aria-label="Cor da seta de marcação" onChange={(e) => setColor(e.target.value)} />
        <strong className="arrow-size__value">{color.toUpperCase()}</strong>
      </label>
      <p className="dll-admin__muted">
        Seta de marcação (DLL 1.52.0): Líder e Administrador dão Ctrl+Shift+clique num jogador ou mob (ou Ctrl+Espaço com
        ele selecionado) e toda a guild vê uma seta igual à da PT, nesta cor. De novo no mesmo alvo, ou Ctrl duas vezes
        rápido, tira a seta. Clicar na seta seleciona o alvo.
      </p>
      {saved.updatedBy && (
        <p className="dll-admin__muted">Última mudança: {saved.updatedBy}
          {saved.updatedAt && `, ${new Date(saved.updatedAt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' })}`}
        </p>
      )}
      <div className="chat-translator__actions">
        <Button size="sm" variant="ghost" disabled={update.isPending || isDefault}
          onClick={() => { setParty(100); setGuild(100); setDistance(100); setMarker(100); save(100, 100, 100, 100) }}>Voltar ao normal</Button>
        <Button size="sm" loading={update.isPending} disabled={!dirty} onClick={() => save(party, guild, distance, marker)}>
          Salvar
        </Button>
      </div>
    </Panel>
  )
}
