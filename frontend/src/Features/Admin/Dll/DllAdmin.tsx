import { useMemo, useState, type FormEvent } from 'react'
import { dllDownloadUrl, useDllReleases, useMemberDlls, usePublishDll } from '@/Domain/Dll/hooks/useDll'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import './DllAdmin.styles.scss'

const VERSION_FORMAT = /^\d{1,5}(\.\d{1,5}){0,3}$/

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) : '—'
}

function formatSize(bytes: number) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(bytes / 1024)} KB`
}

/** Publica versões da DLL: todo mundo online recebe o aviso e o download passa a entregar a nova. */
export function DllAdminPage() {
  const { data: releases = [], isLoading: releasesLoading } = useDllReleases()
  const { data: members = [], isLoading: membersLoading } = useMemberDlls()
  const publish = usePublishDll()
  const { showToast } = useAppToast()
  const [version, setVersion] = useState('')
  const [notes, setNotes] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileKey, setFileKey] = useState(0)
  const [onlyOutdated, setOnlyOutdated] = useState(false)

  const latest = releases[0] ?? null
  const counts = useMemo(() => {
    const synced = members.filter((m) => m.lastSyncAt)
    return {
      synced: synced.length,
      upToDate: synced.filter((m) => !m.outdated).length,
      outdated: synced.filter((m) => m.outdated).length,
      never: members.length - synced.length,
    }
  }, [members])
  const visibleMembers = onlyOutdated ? members.filter((m) => m.outdated) : members
  const validVersion = VERSION_FORMAT.test(version.trim())

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!file || !validVersion) return
    try {
      const release = await publish.mutateAsync({ version: version.trim(), notes, file })
      showToast(`Versão ${release.version} publicada: todos foram avisados.`, 'success')
      setVersion('')
      setNotes('')
      setFile(null)
      setFileKey((k) => k + 1)
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Não foi possível publicar.', 'error')
    }
  }

  return (
    <div className="dll-admin">
      <Panel title="Versão atual" code={latest ? `v${latest.version}` : 'NENHUMA'}>
        {latest ? (
          <div className="dll-admin__current">
            <p>
              <strong>v{latest.version}</strong> · {latest.fileName} · {formatSize(latest.fileSize)} · publicada em{' '}
              {formatDate(latest.publishedAt)}
              {latest.publishedBy ? ` por ${latest.publishedBy}` : ''}
            </p>
            {latest.notes && <p className="dll-admin__notes">{latest.notes}</p>}
            <a className="btn btn--secondary btn--sm dll-admin__download" href={dllDownloadUrl(latest)} download>
              Baixar esta versão
            </a>
          </div>
        ) : (
          <p className="dll-admin__muted">
            Nenhuma versão publicada pelo site ainda: o botão de download usa o arquivo fixo do site e ninguém aparece
            como desatualizado.
          </p>
        )}
      </Panel>

      <Panel title="Publicar nova versão" code="ADM">
        <form className="dll-admin__form" onSubmit={submit}>
          <label>
            Versão
            <input
              value={version}
              onChange={(e) => setVersion(e.target.value)}
              placeholder={latest ? `maior que ${latest.version}` : 'ex.: 1.1.0'}
              aria-invalid={version !== '' && !validVersion}
              required
            />
            <small>A mesma versão compilada na DLL (enviada no User-Agent "CerberusBags/x.y.z").</small>
          </label>
          <label>
            Arquivo (.rar ou .zip, até 5 MB)
            <input
              key={fileKey}
              type="file"
              accept=".rar,.zip"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              required
            />
          </label>
          <label className="dll-admin__form-notes">
            O que mudou (aparece no aviso para os membros)
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} maxLength={2000} />
          </label>
          <div className="dll-admin__actions">
            <Button type="submit" loading={publish.isPending} disabled={!file || !validVersion}>
              Publicar e avisar todos
            </Button>
          </div>
        </form>
      </Panel>

      <Panel
        title="Versão de cada membro"
        code={`${counts.upToDate} EM DIA · ${counts.outdated} DESATUALIZADOS · ${counts.never} SEM SYNC`}
        actions={
          <label className="dll-admin__filter">
            <input type="checkbox" checked={onlyOutdated} onChange={(e) => setOnlyOutdated(e.target.checked)} />
            Só desatualizados
          </label>
        }
      >
        {membersLoading ? (
          <p className="dll-admin__muted">Carregando...</p>
        ) : (
          <table className="dll-admin__table">
            <thead>
              <tr>
                <th>Membro</th>
                <th>Versão da DLL</th>
                <th>Último envio</th>
                <th>Situação</th>
              </tr>
            </thead>
            <tbody>
              {visibleMembers.map((m) => (
                <tr key={m.memberId}>
                  <td>{m.nickname}</td>
                  <td>{m.dllVersion ? `v${m.dllVersion}` : m.lastSyncAt ? 'sem versão' : '—'}</td>
                  <td>{formatDate(m.lastSyncAt)}</td>
                  <td>
                    {!m.lastSyncAt ? (
                      <span className="dll-admin__state dll-admin__state--never">Nunca sincronizou</span>
                    ) : m.outdated ? (
                      <span className="dll-admin__state dll-admin__state--outdated">Desatualizada</span>
                    ) : (
                      <span className="dll-admin__state dll-admin__state--ok">Em dia</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      <Panel title="Histórico de versões" code={`${releases.length}`}>
        {releasesLoading ? (
          <p className="dll-admin__muted">Carregando...</p>
        ) : releases.length === 0 ? (
          <p className="dll-admin__muted">Nenhuma versão publicada.</p>
        ) : (
          <ul className="dll-admin__releases">
            {releases.map((r) => (
              <li key={r.id}>
                <strong>v{r.version}</strong> · {formatDate(r.publishedAt)}
                {r.publishedBy ? ` · ${r.publishedBy}` : ''} · {r.fileName} · {formatSize(r.fileSize)}
                {r.notes && <p className="dll-admin__notes">{r.notes}</p>}
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  )
}
