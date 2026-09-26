import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './ServerInfo.styles.scss'

interface TopicIndexItem {
  slug: string
  title: string
  sourceUrl: string
}

export function ServerInfoListPage() {
  const { isAuthenticated } = useAuthContext()
  const [topics, setTopics] = useState<TopicIndexItem[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    fetch('/server-info/index.json')
      .then((r) => {
        if (!r.ok) throw new Error('Índice não encontrado')
        return r.json()
      })
      .then((data) => setTopics(data.topics ?? []))
      .catch(() => setError('Não foi possível carregar os tópicos do servidor.'))
  }, [])

  return (
    <div className="server-info-page">
      <header className="server-info-page__hero">
        <img src="/logo.png" alt="" className="server-info-page__logo" />
        <div>
          <h1>CERBERUS GAMES Inc</h1>
          <p>
            Tópicos do fórum Cerberus — títulos em português; texto e imagens da fonte oficial.
          </p>
        </div>
        <Link
          to={isAuthenticated ? '/' : '/login'}
          className="server-info-page__back"
        >
          {isAuthenticated ? '← Voltar ao painel' : '← Voltar ao login'}
        </Link>
      </header>

      <Panel title="Tópicos do servidor" code={`${topics.length} DOC`} flush>
        {error && <p className="server-info-page__error">{error}</p>}
        {!error && topics.length === 0 && (
          <p className="server-info-page__loading">Carregando tópicos...</p>
        )}
        <ul className="server-info-list">
          {topics.map((t) => (
            <li key={t.slug}>
              <Link to={`/server-info/${t.slug}`} className="server-info-list__link">
                <span className="server-info-list__title">{t.title}</span>
                <span className="server-info-list__arrow">›</span>
              </Link>
            </li>
          ))}
        </ul>
      </Panel>

      <p className="server-info-page__source">
        Fonte:{' '}
        <a
          href="https://cerberus-games.com/forums/server-ingame-information-eng.166/"
          target="_blank"
          rel="noreferrer"
        >
          Cerberus Games — Server ingame Information [ENG]
        </a>
      </p>
    </div>
  )
}
