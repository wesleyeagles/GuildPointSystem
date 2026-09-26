import { useState } from 'react'
import { useItems, useDeleteItem } from '@/Domain/Item/hooks/useItems'
import { useItemSeeds } from '@/Domain/Seed/hooks/useItemSeeds'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { CreateItemForm } from '@/Features/Items/components/CreateItemForm/CreateItemForm'
import { ItemCard } from '@/Features/Items/components/ItemCard/ItemCard'
import { ItemCatalog } from '@/Features/Items/components/AccessoryCatalog/AccessoryCatalog'
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
      <div className="items-tabs" role="tablist">
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

      {tab === 'catalog' && (
        <Panel title="Catálogo do jogo" code="DB">
          <ItemCatalog />
        </Panel>
      )}

      {tab === 'create' && isAdmin && (
        <CreateItemForm onSuccess={() => setTab('list')} />
      )}

      {tab === 'list' && (
        <Panel title="Inventário da guild" code={`${items.length} ITM`}>
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
        </Panel>
      )}

      {confirmDelete !== null && (
        <div className="items-confirm-overlay" onClick={() => setConfirmDelete(null)}>
          <div onClick={(e) => e.stopPropagation()}>
            <Panel title="Excluir item" variant="danger" className="items-confirm">
              <p>Confirmar exclusão do item?</p>
              <div className="items-confirm__actions">
                <Button variant="secondary" size="sm" onClick={() => setConfirmDelete(null)}>
                  Cancelar
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => handleDelete(confirmDelete)}
                  disabled={deleteItem.isPending}
                >
                  {deleteItem.isPending ? 'Deletando...' : 'Deletar'}
                </Button>
              </div>
            </Panel>
          </div>
        </div>
      )}
    </div>
  )
}
