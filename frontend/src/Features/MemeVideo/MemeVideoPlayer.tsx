import { useEffect, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import './MemeVideoPlayer.styles.scss'

const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/

interface YtPlayer { destroy(): void }
interface YtApi {
  Player: new (el: HTMLElement, options: {
    videoId: string
    playerVars: Record<string, number | string>
    events: { onStateChange?: (e: { data: number }) => void; onError?: () => void }
  }) => YtPlayer
}
declare global {
  interface Window { YT?: YtApi; onYouTubeIframeAPIReady?: () => void }
}

function close() {
  window.close()
}

/**
 * Página aberta pela DLL (1.45.0+) numa janela do Edge no meio do jogo: só o vídeo do YouTube e um botão de
 * fechar. A janela fecha sozinha quando o vídeo acaba. Pública: não mostra nada do site.
 */
export function MemeVideoPlayerPage() {
  const [params] = useSearchParams()
  const id = params.get('v') ?? ''
  const start = Math.max(0, Math.min(86_400, Number(params.get('t')) || 0))
  const host = useRef<HTMLDivElement>(null)

  useEffect(() => {
    document.title = 'Cerberus'
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => {
    if (!VIDEO_ID.test(id) || !host.current) return
    let player: YtPlayer | null = null
    const target = host.current
    const create = () => {
      if (!window.YT) return
      player = new window.YT.Player(target, {
        videoId: id,
        playerVars: { autoplay: 1, start, rel: 0, playsinline: 1, modestbranding: 1 },
        events: {
          onStateChange: (e) => { if (e.data === 0) close() },   // 0 = terminou
        },
      })
    }
    if (window.YT?.Player) create()
    else {
      window.onYouTubeIframeAPIReady = create
      if (!document.querySelector('script[data-yt-api]')) {
        const script = document.createElement('script')
        script.src = 'https://www.youtube.com/iframe_api'
        script.dataset.ytApi = '1'
        document.head.appendChild(script)
      }
    }
    return () => player?.destroy()
  }, [id, start])

  if (!VIDEO_ID.test(id)) {
    return <div className="meme-video meme-video--empty">Vídeo inválido.</div>
  }
  return (
    <div className="meme-video">
      <div className="meme-video__frame"><div ref={host} /></div>
      <button type="button" className="meme-video__close" onClick={close} title="Fechar (Esc)">✕</button>
    </div>
  )
}
