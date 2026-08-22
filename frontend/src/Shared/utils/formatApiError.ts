const ENGLISH_FALLBACKS: Record<string, string> = {
  'Invalid credentials': 'Email ou senha incorretos.',
  'Unauthorized': 'Não autorizado. Faça login novamente.',
  'Forbidden': 'Você não tem permissão para esta ação.',
  'Validation error': 'Verifique os dados e tente novamente.',
  'Member not found': 'Membro não encontrado.',
  'Event not found': 'Evento não encontrado.',
  'Auction not found': 'Leilão não encontrado.',
  'Item not found': 'Item não encontrado.',
  'Objective not found': 'Objetivo não encontrado.',
  'Insufficient available points': 'Saldo disponível insuficiente para este lance.',
  'Insufficient points': 'Pontos insuficientes.',
  'Empty file': 'Selecione um arquivo.',
  'File too large': 'O arquivo é muito grande.',
  'Invalid file type': 'Tipo de arquivo inválido. Use JPG, PNG ou WebP.',
  'Senha inválida': 'Senha incorreta. Tente novamente.',
}

export function formatApiError(message: string): string {
  const trimmed = message.trim()
  if (!trimmed) return 'Ocorreu um erro. Tente novamente.'

  if (ENGLISH_FALLBACKS[trimmed]) return ENGLISH_FALLBACKS[trimmed]

  if (/deve corresponder a/i.test(trimmed)) {
    return 'A senha deve ter exatamente 4 caracteres.'
  }

  const withoutField = trimmed.replace(/^[a-zA-Z_]+:\s*/, '')
  if (withoutField !== trimmed) {
    if (/deve corresponder a/i.test(withoutField)) {
      return 'A senha deve ter exatamente 4 caracteres.'
    }
    if (ENGLISH_FALLBACKS[withoutField]) return ENGLISH_FALLBACKS[withoutField]
    return withoutField
  }

  return trimmed
}
