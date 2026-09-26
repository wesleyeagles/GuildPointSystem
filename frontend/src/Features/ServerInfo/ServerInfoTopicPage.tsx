import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './ServerInfo.styles.scss'

interface TopicDoc {
  slug: string
  title: string
  sourceUrl: string
  bodyHtml: string
}

export function ServerInfoTopicPage() {
  const { slug } = useParams<{ slug: string }>()
  const [topic, setTopic] = useState<TopicDoc | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!slug) return
    setTopic(null)
    setError('')
    fetch(`/server-info/topics/${slug}.json`)
      .then((r) => {
        if (!r.ok) throw new Error('Tópico não encontrado')
        return r.json()
      })
      .then((data) => setTopic(data))
      .catch(() => setError('Não foi possível carregar este tópico.'))
  }, [slug])

  return (
    <div className="server-info-page">
      <header className="server-info-page__hero server-info-page__hero--compact">
        <Link to="/server-info" className="server-info-page__back">← Todos os tópicos</Link>
      </header>

      {error && <p className="server-info-page__error">{error}</p>}
      {!error && !topic && <p className="server-info-page__loading">Carregando...</p>}

      {topic && (
        <Panel title={topic.title} variant="amber" flush className="server-info-topic">
          <div
            className="server-info-topic__body forum-content"
            dangerouslySetInnerHTML={{ __html: topic.bodyHtml }}
          />
          <footer className="server-info-topic__footer">
            <a href={topic.sourceUrl} target="_blank" rel="noreferrer">
              Ver original no fórum
            </a>
          </footer>
        </Panel>
      )}
    </div>
  )
}
