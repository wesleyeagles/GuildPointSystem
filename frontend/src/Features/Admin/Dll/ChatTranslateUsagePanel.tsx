import type { ChatTranslateUsage } from '@/Domain/ChatTranslate/hooks/useChatTranslate'

const number = (n: number) => n.toLocaleString('pt-BR')

/**
 * Uso de hoje do tradutor do chat. O MyMemory não informa quanto da cota já foi usado: o site conta os
 * caracteres que mandou para ele (é o que a cota conta) e mostra quanto veio do dicionário e do glossário.
 */
export function ChatTranslateUsagePanel({ usage }: { usage: ChatTranslateUsage }) {
  const messages = usage.apiCalls + usage.savedHits + usage.glossaryHits
  const free = usage.savedHits + usage.glossaryHits
  const percent = usage.dailyLimit > 0 ? Math.min(100, Math.round((usage.apiChars / usage.dailyLimit) * 100)) : null
  const pausedUntil = usage.quotaPausedUntil
    ? new Date(usage.quotaPausedUntil).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    : null

  return (
    <div className="chat-usage">
      <div className="chat-usage__head">
        <strong>Cota de hoje</strong>
        <span>
          {number(usage.apiChars)}
          {usage.dailyLimit > 0 && <> de ~{number(usage.dailyLimit)}</>} caracteres
          {percent !== null && <> ({percent}%)</>}
        </span>
      </div>
      {percent !== null && (
        <div
          className={`chat-usage__bar${percent >= 90 ? ' chat-usage__bar--full' : ''}`}
          role="meter"
          aria-label="Cota do MyMemory usada hoje"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
        >
          <span style={{ width: `${percent}%` }} />
        </div>
      )}
      {usage.warning && <p className="chat-usage__alert">{usage.warning}</p>}
      {pausedUntil && (
        <p className="chat-usage__alert">
          A cota do MyMemory acabou. O tradutor volta a pedir traduções às {pausedUntil}; até lá só funciona o que já
          está no dicionário e no glossário.
        </p>
      )}
      <p className="dll-admin__muted">
        {messages === 0
          ? 'Nenhuma mensagem traduzida hoje.'
          : `${number(messages)} mensagens hoje: ${number(usage.apiCalls)} pela API, ${number(usage.savedHits)} do dicionário e ${number(usage.glossaryHits)} do glossário (${Math.round((free / messages) * 100)}% sem gastar cota).`}
        {usage.failed > 0 && ` ${number(usage.failed)} não traduziram (API fora ou sem cota).`}
        {' '}Conta do horário de Brasília; o MyMemory pode virar o dia em outro horário, então o número é aproximado.
      </p>
    </div>
  )
}
