import './ItemQuantity.styles.scss'

export function ItemQuantity({ quantity }: { quantity: number }) {
  const label = quantity === 1 ? 'unidade' : 'unidades'
  return (
    <span className="item-quantity" aria-label={`${quantity} ${label}`}>
      <b className="item-quantity__value" aria-hidden="true">×{quantity.toLocaleString('pt-BR')}</b>
      <span className="item-quantity__label" aria-hidden="true">{label}</span>
    </span>
  )
}
