import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { AuctionListPage } from '@/Features/Auction/AuctionList'
import { ItemsPage } from '@/Features/Items/Items'
import '@/Features/Items/Items.styles.scss'

type Tab = 'auctions' | 'items' | 'create-item'

/** Leilões + Itens under one menu entry; regular members only get the auctions tab. */
export function AuctionsPage() {
  const { hasRole } = useAuthContext()
  const canSeeItems = hasRole('MODERADOR')
  const canCreateItem = hasRole('ADMINISTRADOR')
  const [searchParams, setSearchParams] = useSearchParams()
  // Remounts the items view so re-clicking "Itens" leaves an open edit form.
  const [itemsKey, setItemsKey] = useState(0)

  const requested = searchParams.get('tab')
  const tab: Tab =
    (requested === 'items' && canSeeItems) || (requested === 'create-item' && canCreateItem)
      ? requested
      : 'auctions'

  const selectTab = (next: Tab) => {
    if (next === 'items') setItemsKey((k) => k + 1)
    setSearchParams(next === 'auctions' ? {} : { tab: next }, { replace: true })
  }

  const tabButton = (value: Tab, label: string) => (
    <button
      type="button"
      role="tab"
      aria-selected={tab === value}
      className={`items-tabs__btn${tab === value ? ' items-tabs__btn--active' : ''}`}
      onClick={() => selectTab(value)}
    >
      {label}
    </button>
  )

  return (
    <div className="items-page">
      <div className="items-tabs" role="tablist">
        {tabButton('auctions', 'Leilões')}
        {canSeeItems && tabButton('items', 'Itens')}
        {canCreateItem && tabButton('create-item', '+ Criar item')}
      </div>

      {tab === 'auctions' ? (
        <AuctionListPage />
      ) : (
        <ItemsPage
          key={itemsKey}
          view={tab === 'create-item' ? 'create' : 'list'}
          onShowList={() => selectTab('items')}
        />
      )}
    </div>
  )
}
