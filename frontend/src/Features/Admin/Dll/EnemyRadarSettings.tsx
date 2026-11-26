import { useState, type FormEvent } from 'react'
import { useAddEnemyGuild, useEnemyRadar, useRemoveEnemyGuild } from '@/Domain/EnemyRadar/hooks/useEnemyRadar'
import { mapLabel } from '@/Domain/Location/gameMaps'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

const age = (s: number) => (s < 60 ? `${s} s` : `${Math.floor(s / 60)} min`)

/**
 * Radar de guilds inimigas: a DLL de cada membro lê a guild de quem está por perto e, se for uma guild da lista,
 * avisa o site; todos os membros com a DLL 1.53.0+ veem pontos vermelhos no mapa do jogo (M).
 */
export function EnemyRadarSettings() {
  const { data } = useEnemyRadar()
  const { showToast } = useAppToast()
  const add = useAddEnemyGuild()
  const remove = useRemoveEnemyGuild()
  const [name, setName] = useState('')
  if (!data) return null

  const fail = (e: unknown) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error')
  const submit = (e: FormEvent) => {
    e.preventDefault()
    const guild = name.trim()
    if (!guild) return
    add.mutate(guild, {
      onSuccess: () => {
        showToast(`${guild} entrou no radar.`, 'success')
        setName('')
      },
      onError: fail,
    })
  }

  return (
    <Panel title="Radar de guilds inimigas" code={`${data.guilds.length}`} className="market-watch">
      <p className="dll-admin__muted">
        A DLL de cada membro lê a guild de quem está por perto. Quem for de uma guild da lista aparece como ponto
        vermelho no mapa do jogo (M) para todos os membros, por 15 segundos depois da última vez que alguém viu.
        Escreva o nome exatamente como no jogo: maiúsculas contam, e há guilds com nome parecido para enganar
        (ex.: BIackLisT com I). Só Admin e Líder editam a lista.
      </p>

      {data.canConfigure && (
        <form className="market-watch__form" onSubmit={submit}>
          <input
            className="chat-translator__search"
            placeholder="Nome da guild (ex.: Betrayed)"
            aria-label="Nome da guild inimiga"
            maxLength={16}
            value={name}
            onChange={(e) => setName(e.target.value.replace(/\s/g, ''))}
          />
          <Button size="sm" type="submit" loading={add.isPending} disabled={!name.trim()}>Adicionar</Button>
        </form>
      )}

      {data.guilds.length === 0 ? (
        <p className="dll-admin__muted">Nenhuma guild no radar.</p>
      ) : (
        <div className="chat-translator__chips">
          {data.guilds.map((g) => (
            <span key={g.id} className="chat-translator__chip">
              {g.name}
              {data.canConfigure && (
                <button
                  type="button"
                  title={`Tirar ${g.name} do radar`}
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(g.id, { onError: fail })}
                >
                  ✕
                </button>
              )}
            </span>
          ))}
        </div>
      )}

      <h4 className="chat-glossary__heading">Vistos nos últimos 10 minutos</h4>
      {data.sightings.length === 0 ? (
        <p className="dll-admin__muted">Ninguém visto.</p>
      ) : (
        <table className="market-watch__table">
          <thead>
            <tr><th>Nick</th><th>Guild</th><th>Onde</th><th>Visto por</th><th>Há</th></tr>
          </thead>
          <tbody>
            {data.sightings.map((s) => (
              <tr key={s.nick}>
                <td>{s.nick}</td>
                <td>{s.guild}</td>
                <td>
                  {s.map
                    ? `${mapLabel(s.map)}${s.region && s.region !== s.map ? ` · ${s.region}` : ''}`
                    : <span className="dll-admin__muted">—</span>}
                </td>
                <td>{s.seenBy}</td>
                <td>{age(s.ageSeconds)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Panel>
  )
}
