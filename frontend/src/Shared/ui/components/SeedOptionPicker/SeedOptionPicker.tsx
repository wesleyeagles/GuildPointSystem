import type { SeedOption } from '@/Domain/types/models'
import { publicAssetUrl } from '@/Shared/utils/publicAssetUrl'
import './SeedOptionPicker.styles.scss'

interface SeedOptionPickerProps {
  label: string
  options: SeedOption[]
  value: number
  onChange: (id: number) => void
  required?: boolean
  showImages?: boolean
}

export function SeedOptionPicker({
  label,
  options,
  value,
  onChange,
  required,
  showImages = false,
}: SeedOptionPickerProps) {
  const hasImages = showImages && options.some((o) => o.imageUrl)

  if (hasImages) {
    return (
      <fieldset className="seed-option-picker">
        <legend>{label}</legend>
        <div className="seed-option-picker__grid" role="radiogroup" aria-label={label}>
          {options.map((option) => (
            <label
              key={option.id}
              className={[
                'seed-option-picker__option',
                value === option.id ? 'seed-option-picker__option--selected' : '',
              ]
                .filter(Boolean)
                .join(' ')}
            >
              <input
                type="radio"
                name={label}
                value={option.id}
                checked={value === option.id}
                onChange={() => onChange(option.id)}
                required={required && options[0]?.id === option.id}
              />
              {option.imageUrl && (
                <img
                  src={publicAssetUrl(option.imageUrl)}
                  alt=""
                  className="seed-option-picker__image"
                />
              )}
              <span>{option.name}</span>
            </label>
          ))}
        </div>
      </fieldset>
    )
  }

  return (
    <label>
      {label}
      <select
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        required={required}
      >
        <option value={0}>Selecione...</option>
        {options.map((option) => (
          <option key={option.id} value={option.id}>
            {option.name}
          </option>
        ))}
      </select>
    </label>
  )
}
