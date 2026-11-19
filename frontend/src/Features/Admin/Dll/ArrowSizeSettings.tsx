import { useState } from 'react'
import {
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
  return <ArrowSizeForm key={`${data.partyArrowScale}-${data.guildArrowScale}`} saved={data} />
}

function ArrowSlider({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <label className="arrow-size">
      <span className="arrow-size__label">{label}</span>
      <input
        type="range"
        min={ARROW_SCALE_MIN}
        max={ARROW_SCALE_MAX}
        step={10}
        value={value}
        aria-label={`Tamanho da ${label.toLowerCase()}`}
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

  const dirty = party !== saved.partyArrowScale || guild !== saved.guildArrowScale
  const isDefault = saved.partyArrowScale === 100 && saved.guildArrowScale === 100

  const save = (partyArrowScale: number, guildArrowScale: number) =>
    update.mutate({ partyArrowScale, guildArrowScale }, {
      onSuccess: () => showToast(`Setas: PT ${partyArrowScale}%, guild ${guildArrowScale}%.`, 'success'),
      onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
    })

  return (
    <Panel title="Setas da PT e da guild" code={`${saved.partyArrowScale}% · ${saved.guildArrowScale}%`} className="chat-translator">
      <p className="dll-admin__muted">
        Tamanho das setas que o jogo mostra em cima do alvo marcado pelo líder da PT (azul) e da guild. 100% é o
        tamanho normal do jogo. Vale para todos com a DLL 1.50.0 ou mais nova e muda em até meio minuto, sem reabrir
        o jogo.
      </p>
      <ArrowSlider label="Seta da PT" value={party} onChange={setParty} />
      <ArrowSlider label="Seta da guild" value={guild} onChange={setGuild} />
      {saved.updatedBy && (
        <p className="dll-admin__muted">Última mudança: {saved.updatedBy}
          {saved.updatedAt && `, ${new Date(saved.updatedAt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' })}`}
        </p>
      )}
      <div className="chat-translator__actions">
        <Button size="sm" variant="ghost" disabled={update.isPending || isDefault}
          onClick={() => { setParty(100); setGuild(100); save(100, 100) }}>Voltar ao normal</Button>
        <Button size="sm" loading={update.isPending} disabled={!dirty} onClick={() => save(party, guild)}>
          Salvar tamanhos
        </Button>
      </div>
    </Panel>
  )
}
