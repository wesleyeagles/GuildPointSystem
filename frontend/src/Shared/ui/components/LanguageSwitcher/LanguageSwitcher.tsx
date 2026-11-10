import { setLanguage, useLanguage, type Language } from '@/Shared/i18n/i18n'
import './LanguageSwitcher.styles.scss'

/** Inline SVG: Windows does not draw flag emojis. */
function BrazilFlag() {
  return (
    <svg viewBox="0 0 28 20" aria-hidden="true">
      <rect width="28" height="20" fill="#009c3b" />
      <polygon points="14,2.5 25.5,10 14,17.5 2.5,10" fill="#ffdf00" />
      <circle cx="14" cy="10" r="4.6" fill="#002776" />
      <path d="M9.6 9.1 C12.5 8.4 15.8 8.9 18.4 10.6" stroke="#fff" strokeWidth="0.9" fill="none" />
    </svg>
  )
}

function UsaFlag() {
  const stripes = Array.from({ length: 7 }, (_, index) => (
    <rect key={index} y={(index * 2 * 20) / 13} width="28" height={20 / 13} fill="#b22234" />
  ))
  return (
    <svg viewBox="0 0 28 20" aria-hidden="true">
      <rect width="28" height="20" fill="#fff" />
      {stripes}
      <rect width="12" height={(20 / 13) * 7} fill="#3c3b6e" />
      {Array.from({ length: 12 }, (_, index) => (
        <circle key={index} cx={1.6 + (index % 4) * 2.9} cy={1.8 + Math.floor(index / 4) * 3.4} r="0.6" fill="#fff" />
      ))}
    </svg>
  )
}

function RussiaFlag() {
  return (
    <svg viewBox="0 0 28 20" aria-hidden="true">
      <rect width="28" height="20" fill="#fff" />
      <rect y="6.67" width="28" height="6.67" fill="#0039a6" />
      <rect y="13.33" width="28" height="6.67" fill="#d52b1e" />
    </svg>
  )
}

const OPTIONS: { value: Language; label: string; Flag: () => React.JSX.Element }[] = [
  { value: 'pt', label: 'Português', Flag: BrazilFlag },
  { value: 'en', label: 'English', Flag: UsaFlag },
  { value: 'ru', label: 'Русский', Flag: RussiaFlag },
]

export function LanguageSwitcher() {
  const language = useLanguage()
  return (
    // data-no-i18n: the labels name each language in its own tongue.
    <div className="language-switcher" role="group" aria-label="Idioma / Language / Язык" data-no-i18n>
      {OPTIONS.map(({ value, label, Flag }) => (
        <button
          key={value}
          type="button"
          className={`language-switcher__flag${language === value ? ' language-switcher__flag--active' : ''}`}
          title={label}
          aria-label={label}
          aria-pressed={language === value}
          onClick={() => setLanguage(value)}
        >
          <Flag />
        </button>
      ))}
    </div>
  )
}
