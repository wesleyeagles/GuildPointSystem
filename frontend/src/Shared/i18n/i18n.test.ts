import { afterEach, describe, expect, it } from 'vitest'
import { setLanguage, translateText } from './i18n'
import { EN } from './translations.en'
import { RU } from './translations.ru'

const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

describe('i18n', () => {
  afterEach(() => {
    setLanguage('pt')
    document.body.innerHTML = ''
  })

  it('translates exact strings and templates, keeping surrounding whitespace', () => {
    setLanguage('en')
    expect(translateText('Leilões')).toBe('Auctions')
    expect(translateText('  Sair ')).toBe('  Log out ')
    expect(translateText('Atualize para a v1.35.3')).toBe('Update to v1.35.3')
    // Placeholder values are translated too when they are known words.
    expect(translateText('Tipo Arma')).toBe('Type Weapon')
    expect(translateText('Texto que não está no dicionário')).toBe('Texto que não está no dicionário')
  })

  it('translates to Russian, switching straight from English', () => {
    document.body.innerHTML = '<span id="a">Leilões</span><input id="b" placeholder="Seu nick no jogo">'
    setLanguage('en')
    expect(document.getElementById('a')!.textContent).toBe('Auctions')
    // EN→RU must start again from the Portuguese, not from the English text on screen.
    setLanguage('ru')
    expect(document.getElementById('a')!.textContent).toBe('Аукционы')
    expect(document.getElementById('b')!.getAttribute('placeholder')).toBe('Ваш ник в игре')
    expect(translateText('Atualize para a v1.35.3')).toBe('Обновите до v1.35.3')
    setLanguage('pt')
    expect(document.getElementById('a')!.textContent).toBe('Leilões')
  })

  it('has a Russian entry for every English one', () => {
    // A string translated only to English shows up in Portuguese for Russian players.
    // Entries whose English is the source itself ("Ping", "Guild System") need no Russian.
    const norm = (key: string) => key.replace(/\s+/g, ' ').trim()
    const ruKeys = new Set(Object.keys(RU).map(norm))
    const missing = Object.entries(EN)
      .filter(([source, english]) => norm(source) !== norm(english) && !ruKeys.has(norm(source)))
      .map(([source]) => source)
    expect(missing).toEqual([])
  })

  it('leaves Portuguese untouched while the language is pt', () => {
    expect(translateText('Leilões')).toBe('Leilões')
  })

  it('translates the DOM, follows later updates and restores Portuguese', async () => {
    document.body.innerHTML =
      '<nav><span id="a">Leilões</span><button id="b" title="Abrir menu">Sair</button></nav>' +
      '<div data-no-i18n><span id="c">Leilões</span></div>'
    setLanguage('en')
    const a = document.getElementById('a')!
    const b = document.getElementById('b')!
    expect(a.textContent).toBe('Auctions')
    expect(b.textContent).toBe('Log out')
    expect(b.getAttribute('title')).toBe('Open menu')
    expect(document.getElementById('c')!.textContent).toBe('Leilões')

    // React rewriting the same text node with new Portuguese.
    a.firstChild!.nodeValue = 'Eventos'
    const added = document.createElement('p')
    added.textContent = 'Frases'
    document.body.appendChild(added)
    await flush()
    expect(a.textContent).toBe('Events')
    expect(added.textContent).toBe('Phrases')

    setLanguage('pt')
    expect(a.textContent).toBe('Eventos')
    expect(b.textContent).toBe('Sair')
    expect(b.getAttribute('title')).toBe('Abrir menu')
    expect(added.textContent).toBe('Frases')
  })
})
