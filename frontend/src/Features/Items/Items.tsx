import { useState } from 'react'
import { useItems, useDeleteItem } from '@/Domain/Item/hooks/useItems'
import { useItemSeeds } from '@/Domain/Seed/hooks/useItemSeeds'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { CreateItemForm } from '@/Features/Items/components/CreateItemForm/CreateItemForm'
import { ItemCard } from '@/Features/Items/components/ItemCard/ItemCard'
import { AccessoryCatalog } from '@/Features/Items/components/AccessoryCatalog/AccessoryCatalog'
import './Items.styles.scss'

export { ItemCard } from '@/Features/Items/components/ItemCard/ItemCard'

type Tab = 'list' | 'catalog' | 'create'

export function ItemsPage() {
  const { data: items = [], isLoading } = useItems()
  const { data: weaponCasts = [] } = useItemSeeds('WEAPON_CAST')
  const { hasRole } = useAuthContext()
  const { showToast } = useAppToast()
  const deleteItem = useDeleteItem()
  const isAdmin = hasRole('ADMINISTRADOR')

  const [tab, setTab] = useState<Tab>('list')
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)

  const handleDelete = async (id: number) => {
    try {
      await deleteItem.mutateAsync(id)
      showToast('Item deletado.', 'success')
      setConfirmDelete(null)
    } catch {
      showToast('Erro ao deletar item.', 'error')
    }
  }

  return (
    <div className="items-page">
      <h2>Itens</h2>

      <div className="items-tabs">
        <button
          type="button"
          className={`items-tabs__btn${tab === 'list' ? ' items-tabs__btn--active' : ''}`}
          onClick={() => setTab('list')}
        >
          Listagem
        </button>
        <button
          type="button"
          className={`items-tabs__btn${tab === 'catalog' ? ' items-tabs__btn--active' : ''}`}
          onClick={() => setTab('catalog')}
        >
          Catálogo
        </button>
        {isAdmin && (
          <button
            type="button"
            className={`items-tabs__btn${tab === 'create' ? ' items-tabs__btn--active' : ''}`}
            onClick={() => setTab('create')}
          >
            + Criar item
          </button>
        )}
      </div>

      {tab === 'catalog' && <AccessoryCatalog />}

      {tab === 'create' && isAdmin && (
        <CreateItemForm onSuccess={() => setTab('list')} />
      )}

      {tab === 'list' && (
        <>
          {isLoading ? (
            <p className="items-page__loading">Carregando itens...</p>
          ) : items.length === 0 ? (
            <p className="items-page__empty">Nenhum item cadastrado.</p>
          ) : (
            <div className="items-grid">
              {items.map((item) => (
                <ItemCard
                  key={item.id}
                  item={item}
                  canAdmin={isAdmin}
                  weaponCasts={weaponCasts}
                  onDelete={(id) => setConfirmDelete(id)}
                />
              ))}
            </div>
          )}
        </>
      )}

      {confirmDelete !== null && (
        <div className="items-confirm-overlay" onClick={() => setConfirmDelete(null)}>
          <div className="items-confirm" onClick={(e) => e.stopPropagation()}>
            <p>Confirmar exclusão do item?</p>
            <div className="items-confirm__actions">
              <button
                type="button"
                className="items-confirm__btn items-confirm__btn--cancel"
                onClick={() => setConfirmDelete(null)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="items-confirm__btn items-confirm__btn--confirm"
                onClick={() => handleDelete(confirmDelete)}
                disabled={deleteItem.isPending}
              >
                {deleteItem.isPending ? 'Deletando...' : 'Deletar'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
